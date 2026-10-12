package es.mitv.iptv.core;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The saved playlists (M3U links or Xtream accounts) and which one the TV is using.
 * The setup page and the phone apps always edit the active one.
 */
public final class Playlists {

    public static final class Entry {
        public final String id;
        public final String name;
        public final IptvConfig config;

        Entry(String id, String name, IptvConfig config) {
            this.id = id;
            this.name = name;
            this.config = config;
        }
    }

    private static final String KEY_IDS = "lists";
    private static final String KEY_ACTIVE = "activeList";

    private final KeyValueStore store;
    private final List<Entry> entries = new ArrayList<>();
    private String activeId;

    /** Loads the saved lists. A configuration from before lists existed becomes the first one, named {@code legacyName}. */
    public Playlists(KeyValueStore store, String legacyName) {
        this.store = store;
        String ids = store.getString(KEY_IDS, null);
        if (ids == null) {
            migrateLegacy(legacyName);
        } else {
            for (String id : ids.split(",")) {
                if (!id.isEmpty()) entries.add(read(id));
            }
            activeId = store.getString(KEY_ACTIVE, null);
            if (find(activeId) == null) activeId = entries.isEmpty() ? null : entries.get(0).id;
        }
    }

    public synchronized List<Entry> all() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    /** The list in use, or null when there is none yet. */
    public synchronized Entry active() {
        return find(activeId);
    }

    /** A copy of the active list's connection details (empty when there is no list). */
    public synchronized IptvConfig activeConfig() {
        Entry e = active();
        return e == null ? new IptvConfig() : copy(e.config);
    }

    public synchronized Entry find(String id) {
        if (id == null) return null;
        for (Entry e : entries) if (e.id.equals(id)) return e;
        return null;
    }

    /** Saves a new list. The first list also becomes the active one. */
    public synchronized Entry add(String name, IptvConfig config) {
        Entry e = new Entry(newId(), cleanName(name, config), copy(config));
        entries.add(e);
        write(e);
        if (activeId == null) activeId = e.id;
        saveIndex();
        return e;
    }

    public synchronized Entry update(String id, String name, IptvConfig config) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id.equals(id)) {
                Entry e = new Entry(id, cleanName(name, config), copy(config));
                entries.set(i, e);
                write(e);
                return e;
            }
        }
        return null;
    }

    /** Changes the active list's details (setup page, phone), creating a list if there is none. */
    public synchronized Entry updateActive(IptvConfig config, String nameIfNew) {
        Entry e = active();
        return e == null ? add(nameIfNew, config) : update(e.id, e.name, config);
    }

    public synchronized void setActive(String id) {
        if (find(id) == null) return;
        activeId = id;
        saveIndex();
    }

    /** Deletes a list. If it was the active one, the first remaining list becomes active. */
    public synchronized void remove(String id) {
        Entry e = find(id);
        if (e == null) return;
        entries.remove(e);
        for (String field : new String[]{"name", "mode", "m3uUrl", "server", "user", "pass"}) {
            store.putString(key(id, field), null);
        }
        if (id.equals(activeId)) activeId = entries.isEmpty() ? null : entries.get(0).id;
        saveIndex();
    }

    /** A short name for a list nobody named: the provider's host ("lista.example.com"). */
    public static String defaultName(IptvConfig c) {
        String address = IptvConfig.MODE_XTREAM.equals(c.mode) ? IptvConfig.normalizeServer(c.server) : c.m3uUrl.trim();
        try {
            String host = new URI(address).getHost();
            if (host != null && !host.isEmpty()) return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception ignored) {
        }
        return "IPTV";
    }

    // ---------------------------------------------------------------- Storage

    private void migrateLegacy(String legacyName) {
        IptvConfig c = new IptvConfig();
        c.mode = store.getString("mode", IptvConfig.MODE_M3U);
        c.m3uUrl = store.getString("m3uUrl", "");
        c.server = store.getString("server", "");
        c.user = store.getString("user", "");
        c.pass = store.getString("pass", "");
        if (c.isComplete()) {
            Entry e = new Entry("1", legacyName, c);
            entries.add(e);
            write(e);
            activeId = e.id;
        }
        for (String k : new String[]{"mode", "m3uUrl", "server", "user", "pass"}) store.putString(k, null);
        saveIndex();
    }

    private Entry read(String id) {
        IptvConfig c = new IptvConfig();
        c.mode = store.getString(key(id, "mode"), IptvConfig.MODE_M3U);
        c.m3uUrl = store.getString(key(id, "m3uUrl"), "");
        c.server = store.getString(key(id, "server"), "");
        c.user = store.getString(key(id, "user"), "");
        c.pass = store.getString(key(id, "pass"), "");
        return new Entry(id, store.getString(key(id, "name"), defaultName(c)), c);
    }

    private void write(Entry e) {
        store.putString(key(e.id, "name"), e.name);
        store.putString(key(e.id, "mode"), e.config.mode);
        store.putString(key(e.id, "m3uUrl"), e.config.m3uUrl);
        store.putString(key(e.id, "server"), e.config.server);
        store.putString(key(e.id, "user"), e.config.user);
        store.putString(key(e.id, "pass"), e.config.pass);
    }

    private void saveIndex() {
        StringBuilder b = new StringBuilder();
        for (Entry e : entries) {
            if (b.length() > 0) b.append(',');
            b.append(e.id);
        }
        store.putString(KEY_IDS, b.toString());
        store.putString(KEY_ACTIVE, activeId);
    }

    private String newId() {
        int max = 0;
        for (Entry e : entries) {
            try {
                max = Math.max(max, Integer.parseInt(e.id));
            } catch (NumberFormatException ignored) {
            }
        }
        return String.valueOf(max + 1);
    }

    private static String key(String id, String field) {
        return "list." + id + "." + field;
    }

    private static String cleanName(String name, IptvConfig c) {
        String n = name == null ? "" : name.trim();
        if (n.length() > 40) n = n.substring(0, 40).trim();
        return n.isEmpty() ? defaultName(c) : n;
    }

    static IptvConfig copy(IptvConfig c) {
        IptvConfig out = new IptvConfig();
        out.mode = c.mode;
        out.m3uUrl = c.m3uUrl;
        out.server = c.server;
        out.user = c.user;
        out.pass = c.pass;
        return out;
    }
}
