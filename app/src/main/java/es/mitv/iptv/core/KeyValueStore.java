package es.mitv.iptv.core;

import java.util.Set;

/** Where settings are kept: SharedPreferences on the TV, a map in tests. */
public interface KeyValueStore {
    String getString(String key, String fallback);
    /** A null value removes the key. */
    void putString(String key, String value);
    Set<String> getStringSet(String key);
    void putStringSet(String key, Set<String> value);
    boolean getBoolean(String key, boolean fallback);
    void putBoolean(String key, boolean value);
    long getLong(String key, long fallback);
    void putLong(String key, long value);
}
