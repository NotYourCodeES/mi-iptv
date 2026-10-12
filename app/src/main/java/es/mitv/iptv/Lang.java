package es.mitv.iptv;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

import es.mitv.iptv.core.TvSettings;

/** Applies the language chosen in Settings (or the TV's own) to an activity. */
final class Lang {

    /** The TV's language, captured before any change. */
    private static final Locale SYSTEM = Locale.getDefault();

    private Lang() {}

    /** The chosen language code, or "" to follow the TV. */
    static String chosen(Context context) {
        return new TvSettings(new AndroidStore(context)).language();
    }

    /** Call from attachBaseContext. */
    static Context wrap(Context base) {
        String code = chosen(base);
        if (code.isEmpty()) {
            Locale.setDefault(SYSTEM);
            return base;
        }
        Locale locale = new Locale(code);
        Locale.setDefault(locale);
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(locale);
        return base.createConfigurationContext(config);
    }
}
