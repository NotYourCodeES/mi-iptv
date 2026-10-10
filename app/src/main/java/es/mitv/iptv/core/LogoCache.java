package es.mitv.iptv.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * Downloads channel logos for the phone app and keeps the bytes in memory.
 * Many providers serve logos over plain HTTP, which iOS blocks by default, so the phone
 * asks the TV (on the local network) and the TV fetches them from the internet.
 */
public final class LogoCache {

    public static final class Logo {
        public final byte[] data;
        public final String type;

        Logo(byte[] data, String type) {
            this.data = data;
            this.type = type;
        }
    }

    static final int MAX_LOGO_BYTES = 1024 * 1024;
    private static final long FAILURE_TTL_MS = 10 * 60 * 1000;

    private final int maxBytes;
    private final String userAgent;
    private final Semaphore downloads = new Semaphore(6);
    private final LinkedHashMap<String, Logo> cache = new LinkedHashMap<>(64, 0.75f, true);
    private final Map<String, Long> failures = new HashMap<>();
    private int size;

    public LogoCache(int maxBytes, String userAgent) {
        this.maxBytes = maxBytes;
        this.userAgent = userAgent;
    }

    /** Returns the logo, downloading it if needed, or null if it can't be fetched. */
    public Logo get(String url) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) return null;
        synchronized (this) {
            Logo hit = cache.get(url);
            if (hit != null) return hit;
            Long failedAt = failures.get(url);
            if (failedAt != null && System.currentTimeMillis() - failedAt < FAILURE_TTL_MS) return null;
        }
        Logo logo = null;
        try {
            downloads.acquire();
            try {
                logo = download(url);
            } finally {
                downloads.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException ignored) {
        }
        synchronized (this) {
            if (logo == null) {
                failures.put(url, System.currentTimeMillis());
                return null;
            }
            if (!cache.containsKey(url)) {
                cache.put(url, logo);
                size += logo.data.length;
                trim();
            }
            return logo;
        }
    }

    private void trim() {
        Iterator<Map.Entry<String, Logo>> it = cache.entrySet().iterator();
        while (size > maxBytes && it.hasNext()) {
            size -= it.next().getValue().data.length;
            it.remove();
        }
    }

    private Logo download(String address) throws IOException {
        String current = address;
        for (int redirects = 0; redirects < 5; redirects++) {
            HttpURLConnection c = (HttpURLConnection) new URL(current).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(8000);
            c.setReadTimeout(10000);
            c.setRequestProperty("User-Agent", userAgent);
            try {
                int code = c.getResponseCode();
                if (code >= 300 && code < 400) {
                    String loc = c.getHeaderField("Location");
                    if (loc == null) return null;
                    current = new URL(new URL(current), loc).toString();
                    continue;
                }
                if (code != 200) return null;
                String type = c.getContentType();
                if (type == null || !type.toLowerCase().startsWith("image/")) type = "image/png";
                try (InputStream in = c.getInputStream()) {
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    byte[] b = new byte[8192];
                    int n;
                    while ((n = in.read(b)) > 0) {
                        buf.write(b, 0, n);
                        if (buf.size() > MAX_LOGO_BYTES) return null;
                    }
                    return buf.size() == 0 ? null : new Logo(buf.toByteArray(), type);
                }
            } finally {
                c.disconnect();
            }
        }
        return null;
    }
}
