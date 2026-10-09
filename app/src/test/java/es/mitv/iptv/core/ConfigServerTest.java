package es.mitv.iptv.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ConfigServerTest {

    private IptvConfig saved;
    private ConfigServer server;
    private String base;

    @Before
    public void start() throws Exception {
        saved = new IptvConfig();
        saved.mode = IptvConfig.MODE_XTREAM;
        saved.server = "s";
        saved.user = "u";
        saved.pass = "secret";
        server = new ConfigServer(0, "<html>setup page</html>", new ConfigServer.Listener() {
            @Override public IptvConfig currentConfig() { return saved; }
            @Override public String onConfigSaved(IptvConfig c) { saved = c; return "Saved"; }
            @Override public String onReload() { return "Reloading"; }
            @Override public String status() { return "10 channels \"ok\""; }
        });
        server.start();
        base = "http://127.0.0.1:" + server.getPort();
    }

    @After
    public void stop() {
        server.stop();
    }

    @Test
    public void servesTheSetupPage() throws Exception {
        assertTrue(get("/").contains("setup page"));
    }

    @Test
    public void neverExposesThePassword() throws Exception {
        String json = get("/api/config");
        assertFalse(json.contains("secret"));
        assertTrue(json.contains("\"hasPass\":true"));
        assertTrue(json.contains("10 channels \\\"ok\\\""));
    }

    @Test
    public void emptyPasswordKeepsTheSavedOne() throws Exception {
        String r = post("/api/config", "mode=xtream&server=new.tv&user=me&pass=");
        assertTrue(r.contains("\"ok\":true"));
        assertEquals("secret", saved.pass);
        assertEquals("new.tv", saved.server);
    }

    @Test
    public void rejectsIncompleteData() throws Exception {
        assertTrue(post("/api/config", "mode=m3u&m3uUrl=").contains("\"ok\":false"));
    }

    @Test
    public void decodesFormValues() throws Exception {
        post("/api/config", "mode=m3u&m3uUrl=http%3A%2F%2Fx%2Flist.m3u%3Fa%3D1%26b%3D%C3%B1");
        assertEquals("http://x/list.m3u?a=1&b=ñ", saved.m3uUrl);
    }

    @Test
    public void reloadEndpointCallsListener() throws Exception {
        assertTrue(post("/api/reload", "").contains("Reloading"));
    }

    private String get(String path) throws Exception {
        return read((HttpURLConnection) new URL(base + path).openConnection());
    }

    private String post(String path, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(base + path).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        return read(c);
    }

    private static String read(HttpURLConnection c) throws Exception {
        InputStream in = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[4096];
        int n;
        while ((n = in.read(b)) > 0) buf.write(b, 0, n);
        return new String(buf.toByteArray(), StandardCharsets.UTF_8);
    }
}
