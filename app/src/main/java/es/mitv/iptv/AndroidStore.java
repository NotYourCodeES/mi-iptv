package es.mitv.iptv;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import es.mitv.iptv.core.KeyValueStore;

/** The app's SharedPreferences as a {@link KeyValueStore}. */
final class AndroidStore implements KeyValueStore {

    static final String PREFS = "iptv";

    private final SharedPreferences prefs;

    AndroidStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @Override public String getString(String key, String fallback) {
        return prefs.getString(key, fallback);
    }

    @Override public void putString(String key, String value) {
        if (value == null) prefs.edit().remove(key).apply();
        else prefs.edit().putString(key, value).apply();
    }

    @Override public Set<String> getStringSet(String key) {
        return new HashSet<>(prefs.getStringSet(key, Collections.<String>emptySet()));
    }

    @Override public void putStringSet(String key, Set<String> value) {
        prefs.edit().putStringSet(key, new HashSet<>(value)).apply();
    }

    @Override public boolean getBoolean(String key, boolean fallback) {
        return prefs.getBoolean(key, fallback);
    }

    @Override public void putBoolean(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
    }

    @Override public long getLong(String key, long fallback) {
        return prefs.getLong(key, fallback);
    }

    @Override public void putLong(String key, long value) {
        prefs.edit().putLong(key, value).apply();
    }
}
