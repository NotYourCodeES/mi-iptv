package es.mitv.iptv.remote

import org.json.JSONArray
import org.json.JSONObject

/** A channel as listed by the TV. [n] is its number in the playlist (1-based). */
data class TvChannel(val n: Int, val name: String, val g: Int?, val logo: String?)

/** The whole channel list (`GET /api/remote/channels`). */
data class ChannelList(
    val version: Int,
    val groups: List<String>,
    val channels: List<TvChannel>,
    val favorites: List<String>,
)

/** The channel the TV is showing. [n] is 0 for a link sent from the phone. */
data class NowPlaying(val n: Int, val name: String, val group: String, val logo: String?, val fav: Boolean)

/** What is on the TV screen right now. */
data class TvState(
    /** "welcome", "list", "full" or "background" (the app is not open on the TV) */
    val screen: String = "full",
    val channel: NowPlaying? = null,
    val paused: Boolean = false,
    val loading: Boolean = false,
    val configured: Boolean = true,
    val status: String = "",
    val channelCount: Int = 0,
    val version: Int = 0,
    val search: String? = null,
    val setupUrl: String? = null,
)

/** One message of the TV's event stream. */
data class TvEvent(
    val type: String,
    val state: TvState? = null,
    val version: Int? = null,
    val favorites: List<String>? = null,
    val text: String? = null,
)

data class TvInfo(val app: String, val api: Int, val id: String, val name: String, val version: String, val paired: Boolean)

data class PairResponse(val ok: Boolean, val token: String?, val id: String?, val name: String?, val expired: Boolean?)

data class OkResponse(val ok: Boolean, val message: String?, val favorite: Boolean?)

/** The TV this phone is paired with. */
data class PairedTv(val id: String, val name: String, val host: String, val port: Int, val token: String) {
    val address: String get() = if (port == 8080) host else "$host:$port"
}

/** Buttons the TV understands (the same as the physical remote). */
enum class RemoteKey(val wire: String) {
    Up("up"), Down("down"), Left("left"), Right("right"), Ok("ok"), Back("back"),
    Menu("menu"), Info("info"), Guide("guide"), Fav("fav"),
    ChannelUp("chup"), ChannelDown("chdown"), Last("last"), PlayPause("playpause"),
    VolumeUp("volup"), VolumeDown("voldown"), Mute("mute"),
}

/** Reads the TV's JSON. Missing fields get the same defaults as the iPhone app. */
object Json {

    fun channelList(o: JSONObject): ChannelList {
        val channels = o.optJSONArray("channels")
        val list = ArrayList<TvChannel>(channels?.length() ?: 0)
        if (channels != null) {
            for (i in 0 until channels.length()) {
                val c = channels.getJSONObject(i)
                list += TvChannel(
                    n = c.getInt("n"),
                    name = c.optString("name"),
                    g = if (c.has("g") && !c.isNull("g")) c.getInt("g") else null,
                    logo = c.str("logo"),
                )
            }
        }
        return ChannelList(o.optInt("version"), strings(o.optJSONArray("groups")), list, strings(o.optJSONArray("favorites")))
    }

    fun state(o: JSONObject): TvState = TvState(
        screen = o.str("screen") ?: "full",
        channel = o.optJSONObject("channel")?.let {
            NowPlaying(it.optInt("n"), it.optString("name"), it.optString("group"), it.str("logo"), it.optBoolean("fav"))
        },
        paused = o.optBoolean("paused", false),
        loading = o.optBoolean("loading", false),
        configured = o.optBoolean("configured", true),
        status = o.str("status") ?: "",
        channelCount = o.optInt("channelCount", 0),
        version = o.optInt("version", 0),
        search = o.str("search"),
        setupUrl = o.str("setupUrl"),
    )

    fun event(o: JSONObject): TvEvent = TvEvent(
        type = o.optString("type"),
        state = o.optJSONObject("state")?.let(::state),
        version = if (o.has("version")) o.optInt("version") else null,
        favorites = o.optJSONArray("favorites")?.let(::strings),
        text = o.str("text"),
    )

    fun info(o: JSONObject) = TvInfo(
        app = o.optString("app"),
        api = o.optInt("api"),
        id = o.optString("id"),
        name = o.optString("name"),
        version = o.optString("version"),
        paired = o.optBoolean("paired"),
    )

    fun pair(o: JSONObject) = PairResponse(
        ok = o.optBoolean("ok"),
        token = o.str("token"),
        id = o.str("id"),
        name = o.str("name"),
        expired = if (o.has("expired")) o.optBoolean("expired") else null,
    )

    fun ok(o: JSONObject) = OkResponse(
        ok = o.optBoolean("ok"),
        message = o.str("message"),
        favorite = if (o.has("favorite")) o.optBoolean("favorite") else null,
    )

    fun paired(tv: PairedTv): String = JSONObject()
        .put("id", tv.id).put("name", tv.name).put("host", tv.host).put("port", tv.port).put("token", tv.token)
        .toString()

    fun paired(text: String): PairedTv? = runCatching {
        val o = JSONObject(text)
        PairedTv(o.getString("id"), o.getString("name"), o.getString("host"), o.getInt("port"), o.getString("token"))
    }.getOrNull()

    private fun strings(a: JSONArray?): List<String> {
        if (a == null) return emptyList()
        return List(a.length()) { a.optString(it) }
    }

    /** A string field, or null when it's missing or JSON null (optString would give "null"). */
    private fun JSONObject.str(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
}
