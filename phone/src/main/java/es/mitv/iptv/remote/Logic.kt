package es.mitv.iptv.remote

import java.text.Normalizer
import java.util.Locale

/** Which channels the Channels tab shows. */
sealed interface ChannelFilter {
    object Favorites : ChannelFilter
    object All : ChannelFilter
    data class Group(val index: Int) : ChannelFilter
}

/** Logic shared by the screens, kept free of Android so it runs in JVM tests. */
object Logic {

    /** Lower case, without accents or surrounding spaces ("Fútbol " → "futbol"). */
    fun fold(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(MARKS, "").lowercase(Locale.ROOT).trim()

    /**
     * Channels matching [query] (by name, or exact number) within [filter]. An exact number goes first.
     * [folded] holds the folded names in the same order as [channels].
     */
    fun filter(
        channels: List<TvChannel>,
        folded: List<String>,
        favorites: Set<String>,
        query: String,
        filter: ChannelFilter,
    ): List<TvChannel> {
        val q = fold(query)
        val number = q.toIntOrNull()
        val out = ArrayList<TvChannel>()
        for ((i, c) in channels.withIndex()) {
            when (filter) {
                ChannelFilter.Favorites -> if (c.name !in favorites) continue
                is ChannelFilter.Group -> if (c.g != filter.index) continue
                ChannelFilter.All -> {}
            }
            if (q.isEmpty() || c.n == number || folded[i].contains(q)) out += c
        }
        if (number != null) {
            val i = out.indexOfFirst { it.n == number }
            if (i > 0) out.add(0, out.removeAt(i))
        }
        return out
    }

    /** Accepts "192.168.1.50", "192.168.1.50:8080" or the full "http://192.168.1.50:8080" shown on the TV. */
    fun parseAddress(text: String): Pair<String, Int>? {
        var t = text.trim()
        t.indexOf("://").takeIf { it >= 0 }?.let { t = t.substring(it + 3) }
        t.indexOf('/').takeIf { it >= 0 }?.let { t = t.substring(0, it) }
        var host = t
        var port = 8080
        val colon = t.lastIndexOf(':')
        if (colon >= 0 && !t.startsWith("[")) {
            t.substring(colon + 1).toIntOrNull()?.let {
                host = t.substring(0, colon)
                port = it
            }
        }
        return if (host.isEmpty()) null else host to port
    }

    /** A web link the TV can open. */
    fun isWebLink(text: String): Boolean {
        val l = text.trim().lowercase(Locale.ROOT)
        return (l.startsWith("http://") || l.startsWith("https://")) && l.length > 8 && ' ' !in l
    }

    /** The first http(s) link inside shared text (browsers often share "Title https://..."). */
    fun firstLink(text: String): String? =
        LINK.find(text)?.value?.trimEnd('.', ',', ')', ']', '"', '\'')

    /** The channel's initial for the logo placeholder. */
    fun initial(name: String): String =
        name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "·"

    private val MARKS = Regex("\\p{M}+")
    private val LINK = Regex("https?://\\S+", RegexOption.IGNORE_CASE)
}
