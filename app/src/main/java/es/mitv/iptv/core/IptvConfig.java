package es.mitv.iptv.core;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/** Connection details: either an M3U URL, or server + username + password (Xtream Codes). */
public final class IptvConfig {
    public static final String MODE_M3U = "m3u";
    public static final String MODE_XTREAM = "xtream";

    public String mode = MODE_M3U;
    public String m3uUrl = "";
    public String server = "";
    public String user = "";
    public String pass = "";

    public boolean isComplete() {
        if (MODE_XTREAM.equals(mode)) {
            return !server.isEmpty() && !user.isEmpty() && !pass.isEmpty();
        }
        return !m3uUrl.isEmpty();
    }

    /** URL of the channel playlist to download. */
    public String playlistUrl() {
        if (MODE_XTREAM.equals(mode)) {
            String base = normalizeServer(server);
            return base + "/get.php?username=" + enc(user) + "&password=" + enc(pass)
                    + "&type=m3u_plus&output=ts";
        }
        return m3uUrl.trim();
    }

    public static String normalizeServer(String s) {
        String base = s.trim();
        if (!base.startsWith("http://") && !base.startsWith("https://")) base = "http://" + base;
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base;
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return s;
        }
    }
}
