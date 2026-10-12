package es.mitv.iptv.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlaylistsTest {

    private static IptvConfig m3u(String url) {
        IptvConfig c = new IptvConfig();
        c.m3uUrl = url;
        return c;
    }

    private static IptvConfig xtream(String server, String user, String pass) {
        IptvConfig c = new IptvConfig();
        c.mode = IptvConfig.MODE_XTREAM;
        c.server = server;
        c.user = user;
        c.pass = pass;
        return c;
    }

    @Test
    public void theOldSingleConfigurationBecomesTheFirstList() {
        MapStore store = new MapStore();
        store.putString("mode", "xtream");
        store.putString("server", "tv.example.com");
        store.putString("user", "u");
        store.putString("pass", "secret");

        Playlists lists = new Playlists(store, "My list");
        assertEquals(1, lists.all().size());
        assertEquals("My list", lists.active().name);
        assertEquals("secret", lists.activeConfig().pass);
        assertNull("the old keys are gone", store.getString("pass", null));

        Playlists again = new Playlists(store, "ignored");
        assertEquals("My list", again.active().name);
        assertEquals("tv.example.com", again.activeConfig().server);
    }

    @Test
    public void startsEmptyWithoutAConfiguration() {
        Playlists lists = new Playlists(new MapStore(), "My list");
        assertTrue(lists.all().isEmpty());
        assertNull(lists.active());
        assertFalse(lists.activeConfig().isComplete());
    }

    @Test
    public void addsSwitchesEditsAndRemovesLists() {
        MapStore store = new MapStore();
        Playlists lists = new Playlists(store, "x");
        Playlists.Entry home = lists.add("Casa", m3u("http://lista.example.com/home.m3u"));
        Playlists.Entry sports = lists.add("", xtream("http://www.sport.example.org:8080/", "me", "pw"));
        assertEquals("the first list is active", home.id, lists.active().id);
        assertEquals("unnamed lists take the provider's host", "sport.example.org", sports.name);

        lists.setActive(sports.id);
        assertEquals("pw", new Playlists(store, "x").activeConfig().pass);

        lists.update(home.id, "Home", m3u("http://other/list.m3u"));
        assertEquals("Home", lists.find(home.id).name);

        lists.remove(sports.id);
        assertEquals("the remaining list becomes active", home.id, lists.active().id);
        assertNull(store.getString("list." + sports.id + ".pass", null));
        assertEquals(1, new Playlists(store, "x").all().size());
    }

    @Test
    public void theSetupPageEditsTheActiveList() {
        Playlists lists = new Playlists(new MapStore(), "x");
        lists.updateActive(m3u("http://a/1.m3u"), "From the web");
        assertEquals("From the web", lists.active().name);
        lists.updateActive(m3u("http://a/2.m3u"), "ignored");
        assertEquals(1, lists.all().size());
        assertEquals("http://a/2.m3u", lists.activeConfig().m3uUrl);
    }

    @Test
    public void activeConfigIsACopy() {
        Playlists lists = new Playlists(new MapStore(), "x");
        lists.add("a", m3u("http://a/1.m3u"));
        lists.activeConfig().m3uUrl = "changed";
        assertEquals("http://a/1.m3u", lists.activeConfig().m3uUrl);
    }
}
