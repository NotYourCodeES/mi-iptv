package es.mitv.iptv.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class IptvConfigTest {

    @Test
    public void buildsXtreamPlaylistUrlWithEncodedCredentials() {
        IptvConfig c = new IptvConfig();
        c.mode = IptvConfig.MODE_XTREAM;
        c.server = "srv.example.com:8080/";
        c.user = "a b";
        c.pass = "p&1";
        assertEquals("http://srv.example.com:8080/get.php?username=a+b&password=p%261&type=m3u_plus&output=ts",
                c.playlistUrl());
    }

    @Test
    public void m3uModeUsesTheUrlAsIs() {
        IptvConfig c = new IptvConfig();
        c.m3uUrl = "  http://example.com/list.m3u  ";
        assertEquals("http://example.com/list.m3u", c.playlistUrl());
    }

    @Test
    public void completenessDependsOnMode() {
        IptvConfig c = new IptvConfig();
        assertFalse(c.isComplete());
        c.m3uUrl = "http://example.com/list.m3u";
        assertTrue(c.isComplete());
        c.mode = IptvConfig.MODE_XTREAM;
        assertFalse(c.isComplete());
        c.server = "s"; c.user = "u"; c.pass = "p";
        assertTrue(c.isComplete());
    }
}
