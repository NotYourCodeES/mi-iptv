package es.mitv.iptv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Downloads and caches channel logos in memory, with no external libraries. */
final class ImageLoader {

    private static final int MAX_BYTES = 2 * 1024 * 1024;

    private final LruCache<String, Bitmap> cache;
    private final Set<String> failed = Collections.synchronizedSet(new HashSet<String>());
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final int targetPx;
    private final String userAgent;

    ImageLoader(int targetPx, String userAgent) {
        this.targetPx = targetPx;
        this.userAgent = userAgent;
        int bytes = (int) Math.min(Runtime.getRuntime().maxMemory() / 12, 48L * 1024 * 1024);
        cache = new LruCache<String, Bitmap>(bytes) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }
        };
    }

    /** Shows the logo in {@code view}; until it loads, {@code fallback} (the initial letter) is shown. */
    void load(final String url, final ImageView view, final View fallback) {
        view.setTag(url);
        if (url == null || url.isEmpty() || failed.contains(url)) {
            showFallback(view, fallback);
            return;
        }
        Bitmap cached = cache.get(url);
        if (cached != null) {
            showBitmap(view, fallback, cached);
            return;
        }
        showFallback(view, fallback);
        pool.execute(() -> {
            if (!url.equals(view.getTag())) return; // the row is already showing another channel
            final Bitmap bmp = fetch(url);
            if (bmp == null) {
                failed.add(url);
                return;
            }
            cache.put(url, bmp);
            ui.post(() -> {
                if (url.equals(view.getTag())) showBitmap(view, fallback, bmp);
            });
        });
    }

    void shutdown() {
        pool.shutdownNow();
    }

    private static void showFallback(ImageView view, View fallback) {
        view.setImageDrawable(null);
        view.setVisibility(View.INVISIBLE);
        if (fallback != null) fallback.setVisibility(View.VISIBLE);
    }

    private static void showBitmap(ImageView view, View fallback, Bitmap bmp) {
        view.setImageBitmap(bmp);
        view.setVisibility(View.VISIBLE);
        if (fallback != null) fallback.setVisibility(View.INVISIBLE);
    }

    private Bitmap fetch(String address) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(address).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(10000);
            c.setRequestProperty("User-Agent", userAgent);
            c.setInstanceFollowRedirects(true);
            if (c.getResponseCode() != 200) return null;
            byte[] data;
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] b = new byte[8192];
                int n;
                while ((n = in.read(b)) > 0) {
                    buf.write(b, 0, n);
                    if (buf.size() > MAX_BYTES) return null;
                }
                data = buf.toByteArray();
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, opts);
            if (opts.outWidth <= 0 || opts.outHeight <= 0) return null;
            int sample = 1;
            while (opts.outWidth / (sample * 2) >= targetPx && opts.outHeight / (sample * 2) >= targetPx) {
                sample *= 2;
            }
            BitmapFactory.Options real = new BitmapFactory.Options();
            real.inSampleSize = sample;
            return BitmapFactory.decodeByteArray(data, 0, data.length, real);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
