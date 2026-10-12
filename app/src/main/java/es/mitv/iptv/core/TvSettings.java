package es.mitv.iptv.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The TV's own settings: language, hidden and locked categories (per list) and the daily reload. */
public final class TvSettings {

    /** Follow the TV's language. */
    public static final String LANGUAGE_AUTO = "";
    public static final long REFRESH_EVERY_MS = 24L * 60 * 60 * 1000;

    private final KeyValueStore store;

    public TvSettings(KeyValueStore store) {
        this.store = store;
    }

    public String language() {
        return store.getString("language", LANGUAGE_AUTO);
    }

    public void setLanguage(String code) {
        store.putString("language", code == null || code.isEmpty() ? null : code);
    }

    public boolean autoRefresh() {
        return store.getBoolean("autoRefresh", true);
    }

    public void setAutoRefresh(boolean on) {
        store.putBoolean("autoRefresh", on);
    }

    /** When the active list was last downloaded successfully (0 if never). */
    public long lastLoaded() {
        return store.getLong("lastLoaded", 0);
    }

    public void setLastLoaded(long time) {
        store.putLong("lastLoaded", time);
    }

    /** True when the daily reload is on and the last download is more than a day old. */
    public boolean refreshDue(long now) {
        long last = lastLoaded();
        return autoRefresh() && last > 0 && now - last >= REFRESH_EVERY_MS;
    }

    public Set<String> hiddenGroups(String listId) {
        return new HashSet<>(store.getStringSet("hidden." + listId));
    }

    public void setHiddenGroups(String listId, Set<String> groups) {
        store.putStringSet("hidden." + listId, new HashSet<>(groups));
    }

    public Set<String> lockedGroups(String listId) {
        return new HashSet<>(store.getStringSet("locked." + listId));
    }

    public void setLockedGroups(String listId, Set<String> groups) {
        store.putStringSet("locked." + listId, new HashSet<>(groups));
    }

    /** Forgets a deleted list's categories. */
    public void forgetList(String listId) {
        store.putStringSet("hidden." + listId, new HashSet<String>());
        store.putStringSet("locked." + listId, new HashSet<String>());
    }

    /**
     * The channels the TV shows: without hidden categories, and without locked ones unless they are unlocked.
     * Everything else (lists, zapping, numbers, favorites, search, the phone apps) works on this list.
     */
    public static List<Channel> visible(List<Channel> all, Set<String> hidden, Set<String> locked, boolean unlocked) {
        if (hidden.isEmpty() && (locked.isEmpty() || unlocked)) return all;
        List<Channel> out = new ArrayList<>(all.size());
        for (Channel c : all) {
            if (hidden.contains(c.group)) continue;
            if (!unlocked && locked.contains(c.group)) continue;
            out.add(c);
        }
        return out;
    }
}
