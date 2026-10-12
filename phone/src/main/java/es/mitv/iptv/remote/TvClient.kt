package es.mitv.iptv.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Why a call to the TV failed. The UI shows [message] through [TvError.text]. */
sealed class TvError : Exception() {
    object Unauthorized : TvError()
    object NotMiIptv : TvError()
    object Disconnected : TvError()
    data class BadResponse(val code: Int) : TvError()
    data class Message(val text: String) : TvError()
}

/** Talks to the Mi IPTV app on the TV over the local network (see RemoteApi.java on the TV side). */
class TvClient(val host: String, val port: Int, val token: String?) {

    val baseUrl: String
        get() = "http://" + (if (host.contains(':') && !host.startsWith("[")) "[$host]" else host) + ":" + port

    // ---------------------------------------------------------------- Pairing

    suspend fun info(): TvInfo {
        val info = try {
            Json.info(send(request("/api/remote/info")))
        } catch (e: TvError) {
            throw if (e == TvError.Disconnected || e == TvError.Unauthorized) e else TvError.NotMiIptv
        }
        if (info.app != "mi-iptv") throw TvError.NotMiIptv
        return info
    }

    suspend fun startPairing(device: String) {
        send(request("/api/remote/pair/start", "POST", form = mapOf("device" to device)))
    }

    suspend fun pair(pin: String, device: String): PairResponse =
        Json.pair(send(request("/api/remote/pair", "POST", form = mapOf("pin" to pin, "device" to device))))

    suspend fun unpair() {
        send(request("/api/remote/unpair", "POST"))
    }

    // ---------------------------------------------------------------- Reading

    suspend fun state(): TvState = Json.state(send(request("/api/remote/state")))

    suspend fun channels(): ChannelList =
        Json.channelList(send(request("/api/remote/channels", timeoutMs = 30_000))) // big playlists take a moment

    suspend fun logo(number: Int): ByteArray = withContext(Dispatchers.IO) {
        val c = open(request("/api/remote/logo", query = mapOf("n" to number.toString()), timeoutMs = 15_000))
        try {
            if (c.responseCode != 200) throw TvError.BadResponse(c.responseCode)
            c.inputStream.use { it.readLimited(LOGO_LIMIT) }
        } catch (e: IOException) {
            throw TvError.Disconnected
        } finally {
            c.disconnect()
        }
    }

    /** Live updates from the TV. Ends with an error when the connection drops. */
    fun events(): Flow<TvEvent> = callbackFlow {
        val c = open(request("/api/remote/events", timeoutMs = 45_000)) // the TV sends a keep-alive every 15 s
        val reader = launch(Dispatchers.IO) {
            try {
                val code = try {
                    c.responseCode
                } catch (e: IOException) {
                    throw TvError.Disconnected
                }
                if (code == 401) throw TvError.Unauthorized
                if (code != 200) throw TvError.BadResponse(code)
                c.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    for (line in lines) {
                        if (!isActive) break
                        val event = parseEventLine(line) ?: continue
                        trySend(event)
                    }
                }
                close(TvError.Disconnected)
            } catch (e: TvError) {
                close(e)
            } catch (e: IOException) {
                close(TvError.Disconnected)
            }
        }
        awaitClose {
            reader.cancel()
            c.disconnect() // unblocks the read
        }
    }.flowOn(Dispatchers.IO)

    // ---------------------------------------------------------------- Commands

    suspend fun key(key: RemoteKey): OkResponse =
        Json.ok(send(request("/api/remote/key", "POST", form = mapOf("key" to key.wire))))

    /** Digits typed on the phone's keypad are sent as a channel number in one go. */
    suspend fun play(number: Int): OkResponse =
        Json.ok(send(request("/api/remote/play", "POST", form = mapOf("n" to number.toString()))))

    suspend fun favorite(number: Int, on: Boolean?): Boolean {
        val form = mutableMapOf("n" to number.toString())
        if (on != null) form["on"] = if (on) "1" else "0"
        return Json.ok(send(request("/api/remote/favorite", "POST", form = form))).favorite ?: false
    }

    suspend fun search(query: String) {
        send(request("/api/remote/search", "POST", form = mapOf("q" to query)))
    }

    /** Sends a link to the TV: played straight away, or loaded as the channel list. Returns the TV's message. */
    suspend fun share(url: String, title: String?, asPlaylist: Boolean): String? {
        val form = mutableMapOf("url" to url, "as" to if (asPlaylist) "playlist" else "play")
        if (!title.isNullOrEmpty()) form["title"] = title
        val r = Json.ok(send(request("/api/remote/share", "POST", form = form)))
        if (!r.ok) throw TvError.Message(r.message ?: "")
        return r.message
    }

    suspend fun reload(): String? = Json.ok(send(request("/api/remote/reload", "POST"))).message

    // ---------------------------------------------------------------- Plumbing

    private class Request(val url: String, val method: String, val body: String?, val timeoutMs: Int)

    private fun request(
        path: String,
        method: String = "GET",
        query: Map<String, String> = emptyMap(),
        form: Map<String, String> = emptyMap(),
        timeoutMs: Int = 6_000,
    ): Request {
        val q = if (query.isEmpty()) "" else "?" + formEncode(query)
        return Request(baseUrl + path + q, method, if (method == "POST") formEncode(form) else null, timeoutMs)
    }

    private fun open(r: Request): HttpURLConnection {
        val c = URL(r.url).openConnection() as HttpURLConnection
        c.requestMethod = r.method
        c.connectTimeout = 6_000
        c.readTimeout = r.timeoutMs
        c.useCaches = false
        if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
        if (r.body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
        }
        return c
    }

    private suspend fun send(r: Request): JSONObject = withContext(Dispatchers.IO) {
        val c = open(r)
        try {
            val code: Int
            val data: ByteArray
            try {
                if (r.body != null) c.outputStream.use { it.write(r.body.toByteArray(Charsets.UTF_8)) }
                code = c.responseCode
                val stream = if (code >= 400) c.errorStream else c.inputStream
                data = stream?.use { it.readLimited(JSON_LIMIT) } ?: ByteArray(0)
            } catch (e: IOException) {
                throw TvError.Disconnected
            }
            if (code == 401) throw TvError.Unauthorized
            val json = try {
                JSONObject(String(data, Charsets.UTF_8))
            } catch (e: JSONException) {
                null
            }
            if (code >= 400) {
                val message = json?.let { Json.ok(it).message }
                throw if (message != null) TvError.Message(message) else TvError.BadResponse(code)
            }
            json ?: throw TvError.BadResponse(code)
        } finally {
            c.disconnect()
        }
    }

    companion object {
        private const val JSON_LIMIT = 32 * 1024 * 1024
        private const val LOGO_LIMIT = 1024 * 1024

        fun formEncode(form: Map<String, String>): String =
            form.toSortedMap().entries.joinToString("&") { (k, v) -> enc(k) + "=" + enc(v) }

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

        /** One line of the Server-Sent Events stream, or null if it isn't an event. */
        fun parseEventLine(line: String): TvEvent? {
            if (!line.startsWith("data:")) return null
            return try {
                Json.event(JSONObject(line.substring(5).trim()))
            } catch (e: JSONException) {
                null
            }
        }

        private fun InputStream.readLimited(limit: Int): ByteArray {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > limit) throw IOException("response too large")
            }
            return out.toByteArray()
        }
    }
}
