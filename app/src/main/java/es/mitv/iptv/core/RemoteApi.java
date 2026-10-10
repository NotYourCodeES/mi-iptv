package es.mitv.iptv.core;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * JSON API used by the phone app, under /api/remote/.
 *
 * <p>Everything except {@code info}, the two pairing calls and the device list used by the
 * setup page needs "Authorization: Bearer TOKEN", where the token comes from pairing.
 * Request bodies are form-encoded, responses are JSON, and {@code events} is a
 * Server-Sent Events stream that tells the phone what the TV is doing.</p>
 *
 * <p>The TV works exactly the same without any phone: this is only an extra way in.</p>
 */
public final class RemoteApi {

    public static final int API_VERSION = 1;
    public static final String SERVICE_TYPE = "_miiptv._tcp";

    /** Keys the phone can press. The TV turns them into the same events as the physical remote. */
    public static final Set<String> KEYS = new HashSet<>(Arrays.asList(
            "up", "down", "left", "right", "ok", "back", "menu", "info", "guide", "fav",
            "chup", "chdown", "last", "playpause", "volup", "voldown", "mute",
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9"));

    /** What the TV app does for the phone. Calls arrive on server threads. */
    public interface Tv {
        String tvId();
        String tvName();
        String appVersion();

        /** Show the PIN on screen (it disappears on its own after {@link Pairing#PIN_VALID_MS}). */
        void showPairingCode(String pin, String deviceName);
        void hidePairingCode();
        void onDevicePaired(String deviceName);

        /** Presses a key from {@link #KEYS}. Returns a message for the phone, or null if done. */
        String key(String key);
        /** Plays the channel with this playlist number. Returns an error message, or null if done. */
        String play(int number);
        /** Sets (true/false) or toggles (null) a favorite. Returns the new state, or null if there is no such channel. */
        Boolean favorite(int number, Boolean on);
        /** Shows the channels matching the query on the TV (empty query clears the search). */
        void search(String query);
        /** Plays a stream URL or loads it as the playlist. Returns a message for the phone. */
        String share(String url, String title, boolean asPlaylist);
        String reload();

        /** JSON object describing what is on screen. */
        String stateJson();
        /** JSON object with the whole channel list. */
        String channelsJson();
        /** Original logo URL of a channel, or null. */
        String logoUrl(int number);
    }

    private static final long KEEPALIVE_MS = 15000;

    private final Tv tv;
    private final Pairing pairing;
    private final EventHub hub = new EventHub();
    private final LogoCache logos;
    private volatile boolean closed;

    public RemoteApi(Tv tv, Pairing pairing, LogoCache logos) {
        this.tv = tv;
        this.pairing = pairing;
        this.logos = logos;
    }

    public Pairing pairing() { return pairing; }

    public int connectedPhones() { return hub.subscribers(); }

    // ------------------------------------------------------------------ Events pushed to phones

    public void publishState(String stateJson) {
        if (hub.subscribers() > 0) hub.publish("{\"type\":\"state\",\"state\":" + stateJson + "}");
    }

    public void publishChannelsChanged(int version) {
        hub.publish("{\"type\":\"channels\",\"version\":" + version + "}");
    }

    public void publishFavorites(List<String> favoriteNames) {
        if (hub.subscribers() > 0) hub.publish("{\"type\":\"favorites\",\"favorites\":" + jsonArray(favoriteNames) + "}");
    }

    /** A short notice also shown on the TV (e.g. "This channel can't be played"). */
    public void publishToast(String text) {
        if (hub.subscribers() > 0) hub.publish("{\"type\":\"toast\",\"text\":" + ConfigServer.json(text) + "}");
    }

    public void close() {
        closed = true;
        hub.closeAll();
    }

    // ------------------------------------------------------------------ Routing

    /** Handles /api/remote/* requests. Returns false if the path is not part of this API. */
    boolean handle(HttpRequest req, OutputStream out) throws IOException {
        if (!req.path.startsWith("/api/remote/")) return false;
        String p = req.path.substring("/api/remote/".length());
        boolean get = req.method.equals("GET");
        boolean post = req.method.equals("POST");

        // ---- Open endpoints
        if (get && p.equals("info")) {
            json(out, 200, "{\"app\":\"mi-iptv\",\"api\":" + API_VERSION
                    + ",\"id\":" + ConfigServer.json(tv.tvId())
                    + ",\"name\":" + ConfigServer.json(tv.tvName())
                    + ",\"version\":" + ConfigServer.json(tv.appVersion())
                    + ",\"paired\":" + pairing.isAuthorized(req.bearerToken()) + "}");
            return true;
        }
        if (post && p.equals("pair/start")) {
            String device = Pairing.cleanName(req.param("device"));
            String pin = pairing.start(device);
            tv.showPairingCode(pin, device);
            json(out, 200, "{\"ok\":true,\"digits\":" + Pairing.PIN_DIGITS
                    + ",\"expiresIn\":" + (Pairing.PIN_VALID_MS / 1000) + "}");
            return true;
        }
        if (post && p.equals("pair")) {
            Pairing.Result r = pairing.confirm(req.param("pin"), req.param("device"));
            if (r.ok()) {
                String device = pairing.deviceName(r.token);
                tv.hidePairingCode();
                tv.onDevicePaired(device);
                json(out, 200, "{\"ok\":true,\"token\":" + ConfigServer.json(r.token)
                        + ",\"id\":" + ConfigServer.json(tv.tvId())
                        + ",\"name\":" + ConfigServer.json(tv.tvName()) + "}");
            } else {
                if (r.expired) tv.hidePairingCode();
                json(out, 200, "{\"ok\":false,\"expired\":" + r.expired + "}");
            }
            return true;
        }
        // Used by the setup page, which is open to the local network like before
        if (get && p.equals("devices")) {
            json(out, 200, "{\"devices\":" + jsonArray(pairing.deviceNames())
                    + ",\"connected\":" + hub.subscribers() + "}");
            return true;
        }
        if (post && p.equals("devices/forget")) {
            pairing.forgetAll();
            json(out, 200, "{\"ok\":true}");
            return true;
        }

        // ---- Everything else needs a paired phone
        String token = req.bearerToken();
        if (!pairing.isAuthorized(token)) {
            json(out, 401, "{\"ok\":false,\"error\":\"unauthorized\"}");
            return true;
        }

        if (get && p.equals("state")) {
            json(out, 200, tv.stateJson());
        } else if (get && p.equals("channels")) {
            json(out, 200, tv.channelsJson());
        } else if (get && p.equals("events")) {
            stream(out);
        } else if (get && p.equals("logo")) {
            logo(out, req);
        } else if (post && p.equals("key")) {
            String key = req.param("key");
            if (!KEYS.contains(key)) { json(out, 400, result(false, "Unknown key")); return true; }
            String msg = tv.key(key);
            json(out, 200, result(msg == null, msg));
        } else if (post && p.equals("play")) {
            int n = number(req.param("n"));
            String msg = n > 0 ? tv.play(n) : "Bad channel number";
            json(out, 200, result(msg == null, msg));
        } else if (post && p.equals("favorite")) {
            int n = number(req.param("n"));
            String on = req.param("on");
            Boolean state = n > 0 ? tv.favorite(n, on.isEmpty() ? null : (Boolean) (on.equals("1") || on.equals("true"))) : null;
            if (state == null) json(out, 404, result(false, "No such channel"));
            else json(out, 200, "{\"ok\":true,\"favorite\":" + state + "}");
        } else if (post && p.equals("search")) {
            tv.search(req.param("q"));
            json(out, 200, result(true, null));
        } else if (post && p.equals("share")) {
            String url = req.param("url");
            if (!(url.startsWith("http://") || url.startsWith("https://"))) {
                json(out, 400, result(false, "Only http(s) links can be sent to the TV"));
                return true;
            }
            String msg = tv.share(url, req.param("title"), req.param("as").equals("playlist"));
            json(out, 200, result(true, msg));
        } else if (post && p.equals("reload")) {
            json(out, 200, result(true, tv.reload()));
        } else if (post && p.equals("unpair")) {
            pairing.forget(token);
            json(out, 200, result(true, null));
        } else {
            ConfigServer.send(out, 404, "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8));
        }
        return true;
    }

    // ------------------------------------------------------------------ Handlers

    private void stream(OutputStream out) throws IOException {
        BlockingDeque<String> queue = hub.subscribe();
        try {
            String head = "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: text/event-stream; charset=utf-8\r\n"
                    + "Cache-Control: no-store\r\n"
                    + "Connection: close\r\n\r\n";
            out.write(head.getBytes(StandardCharsets.ISO_8859_1));
            writeEvent(out, "{\"type\":\"state\",\"state\":" + tv.stateJson() + "}");
            while (!closed) {
                String ev = queue.pollFirst(KEEPALIVE_MS, TimeUnit.MILLISECONDS);
                if (EventHub.CLOSE.equals(ev)) break;
                if (ev == null) {
                    out.write(": ping\n\n".getBytes(StandardCharsets.UTF_8));
                    out.flush();
                } else {
                    writeEvent(out, ev);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            hub.unsubscribe(queue);
        }
    }

    private static void writeEvent(OutputStream out, String json) throws IOException {
        // Events are one-line JSON, so a single "data:" line is enough
        out.write(("data: " + json + "\n\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private void logo(OutputStream out, HttpRequest req) throws IOException {
        int n = number(req.param("n"));
        String url = n > 0 ? tv.logoUrl(n) : null;
        LogoCache.Logo logo = (url != null && logos != null) ? logos.get(url) : null;
        if (logo == null) {
            ConfigServer.send(out, 404, "text/plain; charset=utf-8", "No logo".getBytes(StandardCharsets.UTF_8));
        } else {
            ConfigServer.send(out, 200, logo.type, logo.data, "Cache-Control: max-age=86400\r\n");
        }
    }

    // ------------------------------------------------------------------ Helpers

    private static void json(OutputStream out, int code, String body) throws IOException {
        ConfigServer.send(out, code, "application/json; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    private static String result(boolean ok, String msg) {
        return "{\"ok\":" + ok + (msg != null ? ",\"message\":" + ConfigServer.json(msg) : "") + "}";
    }

    static int number(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static String jsonArray(List<String> items) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) b.append(',');
            b.append(ConfigServer.json(items.get(i)));
        }
        return b.append(']').toString();
    }
}
