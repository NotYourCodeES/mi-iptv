package es.mitv.iptv.remote

import android.content.Context

/** Remembers the paired TV (the Share screen reads it too). */
object PairedTvStore {
    private const val PREFS = "remote"
    private const val KEY = "pairedTV"

    fun load(context: Context): PairedTv? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)?.let(Json::paired)

    fun save(context: Context, tv: PairedTv?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (tv != null) putString(KEY, Json.paired(tv)) else remove(KEY)
        }.apply()
    }
}
