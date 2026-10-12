package es.mitv.iptv.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** KeyValueStore backed by a map, for tests. */
final class MapStore implements KeyValueStore {
    final Map<String, Object> values = new HashMap<>();

    @Override public String getString(String key, String fallback) {
        Object v = values.get(key);
        return v instanceof String ? (String) v : fallback;
    }

    @Override public void putString(String key, String value) {
        if (value == null) values.remove(key); else values.put(key, value);
    }

    @SuppressWarnings("unchecked")
    @Override public Set<String> getStringSet(String key) {
        Object v = values.get(key);
        return v instanceof Set ? new HashSet<>((Set<String>) v) : new HashSet<String>();
    }

    @Override public void putStringSet(String key, Set<String> value) {
        values.put(key, new HashSet<>(value));
    }

    @Override public boolean getBoolean(String key, boolean fallback) {
        Object v = values.get(key);
        return v instanceof Boolean ? (Boolean) v : fallback;
    }

    @Override public void putBoolean(String key, boolean value) {
        values.put(key, value);
    }

    @Override public long getLong(String key, long fallback) {
        Object v = values.get(key);
        return v instanceof Long ? (Long) v : fallback;
    }

    @Override public void putLong(String key, long value) {
        values.put(key, value);
    }
}
