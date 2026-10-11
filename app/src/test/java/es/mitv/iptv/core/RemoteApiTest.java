package es.mitv.iptv.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RemoteApiTest {

    private ConfigServer server;
    private RemoteApi remote;
    private String base;
    private String shownPin;
    private String pairedName;
    private final List<String> keys = new ArrayList<>();
    private int played = -1;
    private String searched;
    private String shared;

    @Before
    public void start() throws Exception {
        Map<String, String> store = new LinkedHashMap<>();
        Pairing pairing = new Pairing(new Pairing.Store() {
            @Override public Map<String, String> load() { return store; }
            @Override public void save(Map<String, String> d) { store.clear(); store.putAll(d); }
        });
        remote = new RemoteApi(new FakeTv(), pairing, null);
        server = new ConfigServer(0, "<html>setup</html>", new ConfigServer.Listener() {
            @Override public IptvConfig currentConfig() { return new IptvConfig(); }
            @Override public String onConfigSaved(IptvConfig c) { return "Saved"; }
            @Override public String onReload() { return "Reloading"; }
            @Override public String status() { return "ok"; }
        });
        server.setRemote(remote);
        server.start();
        base = "http://127.0.0.1:" + server.getPort();
    }

    @After
    public void stop() {
        remote.close();
        server.stop();
    }

    @Test
    public void infoIsOpenAndIdentifiesTheTv() throws Exception {
        String info = get("/api/remote/info", null);
        assertTrue(info.contains("\"app\":\"mi-iptv\""));
        assertTrue(info.contains("\"name\":\"Salón\""));
        assertTrue(info.contains("\"paired\":false"));
    }

    @Test
    public void everythingElseNeedsAToken() throws Exception {
        HttpURLConnection c = open("/api/remote/state", null);
        assertEquals(401, c.getResponseCode());
        c = open("/api/remote/state", "made-up");
        assertEquals(401, c.getResponseCode());
        assertEquals(401, postCode("/api/remote/key", "key=ok", null));
    }

    @Test
    public void pairingShowsThePinOnTheTvAndReturnsAToken() throws Exception {
        String token = pair("iPhone");
        assertEquals("iPhone", pairedName);
        assertTrue(get("/api/remote/info", token).contains("\"paired\":true"));
        assertTrue(get("/api/remote/state", token).contains("\"screen\":\"full\""));
        assertTrue(get("/api/remote/devices", null).contains("\"iPhone\""));
    }

    @Test
    public void wrongPinFails() throws Exception {
        post("/api/remote/pair/start", "device=x", null);
        String wrong = shownPin.equals("0000") ? "1111" : "0000";
        String r = post("/api/remote/pair", "pin=" + wrong + "&device=x", null);
        assertTrue(r.contains("\"ok\":false"));
        assertFalse(r.contains("token"));
    }

    @Test
    public void pairingIsLockedAfterTooManyWrongPins() throws Exception {
        post("/api/remote/pair/start", "device=x", null);
        String wrong = shownPin.equals("0000") ? "1111" : "0000";
        for (int i = 0; i < Pairing.MAX_ATTEMPTS; i++) post("/api/remote/pair", "pin=" + wrong + "&device=x", null);

        shownPin = null;
        HttpURLConnection c = postConn("/api/remote/pair/start", "device=x", null);
        assertEquals(429, c.getResponseCode());
        String r = read(c);
        assertTrue(r, r.contains("\"error\":\"locked\""));
        assertTrue(r, r.contains("\"message\":\"Too many wrong codes. Try again in 30 s.\""));
        assertEquals("no PIN is shown on the TV", null, shownPin);
    }

    @Test
    public void keysPlayFavoritesSearchAndShareReachTheTv() throws Exception {
        String token = pair("p");
        assertTrue(post("/api/remote/key", "key=up", token).contains("\"ok\":true"));
        assertTrue(post("/api/remote/key", "key=volup", token).contains("\"ok\":true"));
        assertEquals(400, postCode("/api/remote/key", "key=selfdestruct", token));
        assertEquals(List.of("up", "volup"), keys);

        post("/api/remote/play", "n=7", token);
        assertEquals(7, played);

        assertTrue(post("/api/remote/favorite", "n=7", token).contains("\"favorite\":true"));
        assertEquals(404, postCode("/api/remote/favorite", "n=999", token));

        post("/api/remote/search", "q=f%C3%BAtbol", token);
        assertEquals("fútbol", searched);

        post("/api/remote/share", "url=http%3A%2F%2Fx%2Flive.m3u8&as=play", token);
        assertEquals("play:http://x/live.m3u8", shared);
        assertEquals(400, postCode("/api/remote/share", "url=file%3A%2F%2F%2Fetc%2Fpasswd", token));
    }

    @Test
    public void eventStreamSendsStateThenUpdates() throws Exception {
        String token = pair("p");
        HttpURLConnection c = open("/api/remote/events", token);
        assertEquals(200, c.getResponseCode());
        assertTrue(c.getContentType().startsWith("text/event-stream"));
        BufferedReader in = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
        String first = in.readLine();
        assertTrue(first, first.startsWith("data: {\"type\":\"state\""));
        assertEquals("", in.readLine());

        remote.publishToast("Hola \"tele\"");
        String next = in.readLine();
        assertEquals("data: {\"type\":\"toast\",\"text\":\"Hola \\\"tele\\\"\"}", next);
        c.disconnect();
    }

    @Test
    public void unpairRevokesTheToken() throws Exception {
        String token = pair("p");
        post("/api/remote/unpair", "", token);
        assertEquals(401, open("/api/remote/state", token).getResponseCode());
    }

    @Test
    public void setupPageStillWorksWithTheRemoteEnabled() throws Exception {
        assertTrue(get("/", null).contains("setup"));
        assertTrue(get("/api/config", null).contains("\"status\":\"ok\""));
    }

    // ------------------------------------------------------------------ helpers

    private String pair(String device) throws Exception {
        post("/api/remote/pair/start", "device=" + device, null);
        assertNotNull(shownPin);
        String r = post("/api/remote/pair", "pin=" + shownPin + "&device=" + device, null);
        Matcher m = Pattern.compile("\"token\":\"([0-9a-f]+)\"").matcher(r);
        assertTrue(r, m.find());
        return m.group(1);
    }

    private HttpURLConnection open(String path, String token) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(base + path).openConnection();
        if (token != null) c.setRequestProperty("Authorization", "Bearer " + token);
        return c;
    }

    private String get(String path, String token) throws Exception {
        return read(open(path, token));
    }

    private HttpURLConnection postConn(String path, String body, String token) throws Exception {
        HttpURLConnection c = open(path, token);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        return c;
    }

    private String post(String path, String body, String token) throws Exception {
        return read(postConn(path, body, token));
    }

    private int postCode(String path, String body, String token) throws Exception {
        return postConn(path, body, token).getResponseCode();
    }

    private static String read(HttpURLConnection c) throws Exception {
        InputStream in = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[4096];
        int n;
        while ((n = in.read(b)) > 0) buf.write(b, 0, n);
        return new String(buf.toByteArray(), StandardCharsets.UTF_8);
    }

    private final class FakeTv implements RemoteApi.Tv {
        boolean fav;
        @Override public String tvId() { return "tv1"; }
        @Override public String tvName() { return "Salón"; }
        @Override public String appVersion() { return "test"; }
        @Override public void showPairingCode(String pin, String deviceName) { shownPin = pin; }
        @Override public void hidePairingCode() {}
        @Override public void onDevicePaired(String deviceName) { pairedName = deviceName; }
        @Override public String key(String key) { keys.add(key); return null; }
        @Override public String play(int number) { played = number; return null; }
        @Override public Boolean favorite(int number, Boolean on) {
            if (number != 7) return null;
            fav = on != null ? on : !fav;
            return fav;
        }
        @Override public void search(String query) { searched = query; }
        @Override public String share(String url, String title, boolean asPlaylist) {
            shared = (asPlaylist ? "playlist:" : "play:") + url;
            return "ok";
        }
        @Override public String reload() { return "Reloading"; }
        @Override public String stateJson() { return "{\"screen\":\"full\"}"; }
        @Override public String channelsJson() { return "{\"version\":1,\"groups\":[],\"channels\":[],\"favorites\":[]}"; }
        @Override public String logoUrl(int number) { return null; }
    }
}
