package es.mitv.iptv;

import android.content.Context;

import java.util.Collections;
import java.util.List;

import es.mitv.iptv.core.Pairing;
import es.mitv.iptv.core.Playlists;

/** State shared by the main screen and Settings while the app is running. */
final class Session {

    private Session() {}

    /** Locked categories stay open until the user leaves the app. */
    static boolean unlocked;

    /** Categories of the playlist on screen, for the Settings lists. */
    static List<String> groups = Collections.emptyList();

    /** Setup page address shown in Settings, or null without a network. */
    static String setupUrl;

    /** The phones paired with this TV (null until the server starts). */
    static Pairing pairing;

    /** Settings asked for a fresh download of the channels. */
    static boolean reloadRequested;

    private static Playlists playlists;

    /** The saved playlists, shared by every screen (and the setup page's server thread). */
    static synchronized Playlists playlists(Context context) {
        if (playlists == null) {
            playlists = new Playlists(new AndroidStore(context), context.getString(R.string.lists_default_name));
        }
        return playlists;
    }
}
