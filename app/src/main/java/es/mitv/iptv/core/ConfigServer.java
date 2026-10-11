package es.mitv.iptv.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Tiny web server running inside the TV app.
 * Open http://TV-IP:8080 from a computer or phone on the same network to enter the provider details.
 * The phone remote app talks to the same server, under /api/remote/ (see {@link RemoteApi}).
 */
public final class ConfigServer {

    public interface Listener {
        IptvConfig currentConfig();
        /** Saves the configuration and starts loading the playlist. Returns a message for the web page. */
        String onConfigSaved(IptvConfig config);
        /** Reloads the channel list. */
        String onReload();
        /** Current status as one sentence (e.g. "245 channels"). */
        String status();
        /** Message shown when required fields are missing. */
        default String missingFieldsMessage() {
            return "Some fields are missing.";
        }
    }

    public static final int DEFAULT_PORT = 8080;
    private static final int MAX_BODY = 64 * 1024;

    private final int port;
    private final String html;
    private final Listener listener;
    private volatile ServerSocket socket;
    private volatile RemoteApi remote;
    private Thread acceptThread;

    public ConfigServer(int port, String html, Listener listener) {
        this.port = port;
        this.html = html;
        this.listener = listener;
    }

    /** Enables the phone remote API. Without it the server behaves exactly as before. */
    public void setRemote(RemoteApi remote) {
        this.remote = remote;
    }

    public int getPort() {
        ServerSocket s = socket;
        return s != null ? s.getLocalPort() : port;
    }

    public synchronized void start() throws IOException {
        if (socket != null) return;
        ServerSocket s = new ServerSocket();
        s.setReuseAddress(true);
        s.bind(new java.net.InetSocketAddress(port));
        socket = s;
        acceptThread = new Thread(this::acceptLoop, "config-server");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public synchronized void stop() {
        ServerSocket s = socket;
        socket = null;
        if (s != null) {
            try { s.close(); } catch (IOException ignored) {}
        }
    }

    private void acceptLoop() {
        ServerSocket s;
        while ((s = socket) != null) {
            try {
                final Socket client = s.accept();
                Thread t = new Thread(() -> handle(client), "config-client");
                t.setDaemon(true);
                t.start();
            } catch (SocketException e) {
                return; // server closed
            } catch (IOException ignored) {
            }
        }
    }

    private void handle(Socket client) {
        try (Socket c = client) {
            c.setSoTimeout(10000);
            InputStream in = c.getInputStream();
            OutputStream out = c.getOutputStream();
            String head = readHead(in);
            if (head == null) return;
            String[] lines = head.split("\r\n");
            String[] req = lines[0].split(" ");
            if (req.length < 2) { send(out, 400, "text/plain", "Bad request"); return; }
            String method = req[0].toUpperCase(Locale.ROOT);
            String path = req[1];
            String queryString = "";
            int q = path.indexOf('?');
            if (q >= 0) {
                queryString = path.substring(q + 1);
                path = path.substring(0, q);
            }

            Map<String, String> headers = new HashMap<>();
            for (int i = 1; i < lines.length; i++) {
                int colon = lines[i].indexOf(':');
                if (colon > 0) {
                    headers.put(lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT), lines[i].substring(colon + 1).trim());
                }
            }
            int contentLength = 0;
            String cl = headers.get("content-length");
            if (cl != null) {
                try { contentLength = Integer.parseInt(cl); }
                catch (NumberFormatException ignored) {}
            }
            if (contentLength < 0 || contentLength > MAX_BODY) { send(out, 413, "text/plain", "Too large"); return; }
            byte[] body = readBytes(in, contentLength);

            String host = headers.get("host");
            if (!isLocalHost(host) || !isSameOrigin(headers.get("origin"), host)) {
                send(out, 403, "text/plain; charset=utf-8", "Forbidden");
                return;
            }

            RemoteApi r = remote;
            if (r != null && path.startsWith("/api/remote/")) {
                HttpRequest request = new HttpRequest(method, path, parseForm(queryString), headers,
                        parseForm(new String(body, StandardCharsets.UTF_8)));
                r.handle(request, out);
                return;
            }

            if (method.equals("GET") && (path.equals("/") || path.equals("/index.html"))) {
                send(out, 200, "text/html; charset=utf-8", html);
            } else if (method.equals("GET") && path.equals("/api/config")) {
                send(out, 200, "application/json; charset=utf-8", configJson());
            } else if (method.equals("POST") && path.equals("/api/config")) {
                Map<String, String> form = parseForm(new String(body, StandardCharsets.UTF_8));
                IptvConfig prev = listener.currentConfig();
                IptvConfig cfg = new IptvConfig();
                cfg.mode = IptvConfig.MODE_XTREAM.equals(form.get("mode")) ? IptvConfig.MODE_XTREAM : IptvConfig.MODE_M3U;
                cfg.m3uUrl = val(form, "m3uUrl");
                cfg.server = val(form, "server");
                cfg.user = val(form, "user");
                cfg.pass = val(form, "pass");
                // An empty password keeps the one already saved, but only for the same server and user:
                // otherwise anyone on the network could have the TV send it to a server of their choice
                if (cfg.pass.isEmpty() && prev != null && sameAccount(prev, cfg)) cfg.pass = prev.pass;
                if (!cfg.isComplete()) {
                    send(out, 400, "application/json; charset=utf-8", result(false, listener.missingFieldsMessage()));
                    return;
                }
                String msg = listener.onConfigSaved(cfg);
                send(out, 200, "application/json; charset=utf-8", result(true, msg));
            } else if (method.equals("POST") && path.equals("/api/reload")) {
                send(out, 200, "application/json; charset=utf-8", result(true, listener.onReload()));
            } else {
                send(out, 404, "text/plain; charset=utf-8", "Not found");
            }
        } catch (Exception ignored) {
        }
    }

    private String configJson() {
        IptvConfig c = listener.currentConfig();
        if (c == null) c = new IptvConfig();
        return "{\"mode\":" + json(c.mode)
                + ",\"m3uUrl\":" + json(c.m3uUrl)
                + ",\"server\":" + json(c.server)
                + ",\"user\":" + json(c.user)
                + ",\"hasPass\":" + (!c.pass.isEmpty())
                + ",\"status\":" + json(listener.status()) + "}";
    }

    static boolean sameAccount(IptvConfig a, IptvConfig b) {
        return IptvConfig.MODE_XTREAM.equals(a.mode) && IptvConfig.MODE_XTREAM.equals(b.mode)
                && IptvConfig.normalizeServer(a.server).equalsIgnoreCase(IptvConfig.normalizeServer(b.server))
                && a.user.trim().equals(b.user.trim());
    }

    /**
     * Only answers to names that can't belong to a website: IP addresses, localhost, single-label
     * and local-network names. This stops DNS rebinding (a website pointing its own name at the TV).
     * Clients that send no Host header at all are not browsers and are let through.
     */
    static boolean isLocalHost(String host) {
        if (host == null) return true;
        String h = host.trim().toLowerCase(Locale.ROOT);
        if (h.startsWith("[")) return h.indexOf(']') > 0; // IPv6 literal
        int colon = h.lastIndexOf(':');
        if (colon >= 0) h = h.substring(0, colon);
        while (h.endsWith(".")) h = h.substring(0, h.length() - 1);
        if (h.isEmpty()) return false;
        if (h.matches("\\d{1,3}(\\.\\d{1,3}){3}") || h.equals("localhost") || h.indexOf('.') < 0) return true;
        for (String suffix : LOCAL_SUFFIXES) {
            if (h.endsWith(suffix)) return true;
        }
        return false;
    }

    private static final String[] LOCAL_SUFFIXES = {
            ".local", ".lan", ".home", ".home.arpa", ".internal", ".localhost"};

    /** Browsers send Origin on cross-site requests: it has to be this same server. */
    static boolean isSameOrigin(String origin, String host) {
        if (origin == null) return true;
        return host != null && origin.trim().equalsIgnoreCase("http://" + host.trim());
    }

    private static String result(boolean ok, String msg) {
        return "{\"ok\":" + ok + ",\"message\":" + json(msg) + "}";
    }

    private static String val(Map<String, String> m, String k) {
        String v = m.get(k);
        return v == null ? "" : v.trim();
    }

    static Map<String, String> parseForm(String body) {
        Map<String, String> map = new HashMap<>();
        if (body == null || body.isEmpty()) return map;
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            try {
                String k = URLDecoder.decode(eq >= 0 ? pair.substring(0, eq) : pair, "UTF-8");
                String v = eq >= 0 ? URLDecoder.decode(pair.substring(eq + 1), "UTF-8") : "";
                map.put(k, v);
            } catch (Exception ignored) {}
        }
        return map;
    }

    public static String json(String s) {
        if (s == null) return "null";
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                case '<': b.append("\\u003c"); break;
                default:
                    if (ch < 0x20) b.append(String.format(Locale.ROOT, "\\u%04x", (int) ch));
                    else b.append(ch);
            }
        }
        return b.append('"').toString();
    }

    private static String readHead(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int state = 0, b;
        while ((b = in.read()) != -1) {
            buf.write(b);
            if (buf.size() > 16 * 1024) return null;
            // Detect the end of the headers (\r\n\r\n)
            if ((state == 0 || state == 2) && b == '\r') state++;
            else if ((state == 1 || state == 3) && b == '\n') state++;
            else state = (b == '\r') ? 1 : 0;
            if (state == 4) {
                String s = buf.toString("ISO-8859-1");
                return s.substring(0, s.length() - 4);
            }
        }
        return null;
    }

    private static byte[] readBytes(InputStream in, int n) throws IOException {
        byte[] data = new byte[n];
        int off = 0;
        while (off < n) {
            int r = in.read(data, off, n - off);
            if (r < 0) break;
            off += r;
        }
        return data;
    }

    private static void send(OutputStream out, int code, String type, String body) throws IOException {
        send(out, code, type, body.getBytes(StandardCharsets.UTF_8));
    }

    static void send(OutputStream out, int code, String type, byte[] bytes) throws IOException {
        send(out, code, type, bytes, "Cache-Control: no-store\r\n");
    }

    static void send(OutputStream out, int code, String type, byte[] bytes, String extraHeaders) throws IOException {
        String reason = code == 200 ? "OK" : code == 401 ? "Unauthorized" : code == 403 ? "Forbidden"
                : code == 404 ? "Not Found" : code == 413 ? "Payload Too Large"
                : code == 429 ? "Too Many Requests" : "Bad Request";
        String head = "HTTP/1.1 " + code + " " + reason + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + extraHeaders
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.write(bytes);
        out.flush();
    }
}
