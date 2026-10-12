package es.mitv.iptv.remote

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.Collections
import kotlin.concurrent.thread

/** The phone client against a small fake of the TV's /api/remote/ (see RemoteApi.java). */
class TvClientTest {

    private lateinit var server: ServerSocket
    private val requests: MutableList<String> = Collections.synchronizedList(mutableListOf())
    @Volatile private var locked = false

    @Before
    fun start() {
        server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = try {
                    server.accept()
                } catch (e: Exception) {
                    break
                }
                thread(isDaemon = true) { socket.use(::handle) }
            }
        }
    }

    @After
    fun stop() = server.close()

    private fun client(token: String? = null) = TvClient("127.0.0.1", server.localPort, token)

    private fun handle(socket: Socket) {
        val input = socket.getInputStream()
        val head = readHead(input) ?: return
        val lines = head.split("\r\n")
        val (method, target) = lines[0].split(" ")
        val headers = lines.drop(1).filter { ':' in it }.associate {
            it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim()
        }
        val length = headers["content-length"]?.toInt() ?: 0
        val body = String(input.readNBytes(length), Charsets.UTF_8)
        val form = body.split("&").filter { it.contains('=') }.associate {
            val (k, v) = it.split("=", limit = 2)
            URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
        }
        val path = target.substringBefore('?').removePrefix("/api/remote/")
        requests += "$method $path $form"
        val out = socket.getOutputStream()
        val authorized = headers["authorization"] == "Bearer good"
        when {
            path == "info" -> reply(out, 200, """{"app":"mi-iptv","api":1,"id":"tv1","name":"Salón","version":"1.4.0","paired":$authorized}""")
            path == "pair/start" && locked -> reply(out, 429, """{"ok":false,"error":"locked","retryIn":30,"message":"Too many wrong codes. Try again in 30 s."}""")
            path == "pair/start" -> reply(out, 200, """{"ok":true,"digits":4,"expiresIn":120}""")
            path == "pair" && form["pin"] == "1234" -> reply(out, 200, """{"ok":true,"token":"good","id":"tv1","name":"Salón"}""")
            path == "pair" -> reply(out, 200, """{"ok":false,"expired":false}""")
            !authorized -> reply(out, 401, """{"ok":false,"error":"unauthorized"}""")
            path == "channels" -> reply(out, 200, """{"version":3,"groups":["News","Sports"],"channels":[{"n":1,"name":"Aurora News","g":0,"logo":"http://x/a.png"},{"n":2,"name":"Nebula Sports","g":1}],"favorites":["Nebula Sports"]}""")
            path == "key" && form["key"] == "selfdestruct" -> reply(out, 400, """{"ok":false,"message":"Unknown key"}""")
            path == "key" -> reply(out, 200, """{"ok":true}""")
            path == "favorite" -> reply(out, 200, """{"ok":true,"favorite":${form["on"] == "1"}}""")
            path == "share" -> reply(out, 200, """{"ok":true,"message":"Playing on the TV"}""")
            path == "events" -> {
                out.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\n".toByteArray())
                out.write("data: {\"type\":\"state\",\"state\":{\"screen\":\"full\",\"channel\":{\"n\":2,\"name\":\"Nebula Sports\",\"group\":\"Sports\",\"logo\":null,\"fav\":true},\"paused\":false,\"version\":3,\"search\":null}}\n\n".toByteArray())
                out.write(": ping\n\n".toByteArray())
                out.write("data: {\"type\":\"toast\",\"text\":\"Hola \\\"tele\\\"\"}\n\n".toByteArray())
                out.flush()
            }
            else -> reply(out, 404, "Not found")
        }
    }

    private fun readHead(input: InputStream): String? {
        val buf = StringBuilder()
        while (!buf.endsWith("\r\n\r\n")) {
            val b = input.read()
            if (b < 0) return null
            buf.append(b.toChar())
        }
        return buf.substring(0, buf.length - 4)
    }

    private fun reply(out: OutputStream, code: Int, body: String) {
        val bytes = body.toByteArray()
        out.write("HTTP/1.1 $code X\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(bytes)
        out.flush()
    }

    @Test
    fun pairsWithTheCodeShownOnTheTv() = runBlocking {
        val info = client().info()
        assertEquals("Salón", info.name)
        assertFalse(info.paired)

        client().startPairing("Pixel de Daniel")
        val wrong = client().pair("0000", "Pixel de Daniel")
        assertFalse(wrong.ok)
        assertEquals(false, wrong.expired)

        val ok = client().pair("1234", "Pixel de Daniel")
        assertEquals("good", ok.token)
        assertTrue(client("good").info().paired)
        assertTrue(requests.contains("POST pair {device=Pixel de Daniel, pin=1234}"))
    }

    @Test
    fun showsTheTvsMessageWhenPairingIsLocked() = runBlocking {
        locked = true
        try {
            client().startPairing("x")
            fail("expected an error")
        } catch (e: TvError.Message) {
            assertEquals("Too many wrong codes. Try again in 30 s.", e.text)
        }
    }

    @Test
    fun readsTheChannelList() = runBlocking {
        val list = client("good").channels()
        assertEquals(3, list.version)
        assertEquals(listOf("News", "Sports"), list.groups)
        assertEquals(TvChannel(1, "Aurora News", 0, "http://x/a.png"), list.channels[0])
        assertNull(list.channels[1].logo)
        assertEquals(listOf("Nebula Sports"), list.favorites)
    }

    @Test
    fun commandsAndErrors() = runBlocking {
        assertTrue(client("good").key(RemoteKey.ChannelUp).ok)
        assertTrue(requests.last().endsWith("{key=chup}"))
        assertTrue(client("good").favorite(2, on = true))
        assertEquals("Playing on the TV", client("good").share("http://x/live.m3u8", "Live", asPlaylist = false))
        assertTrue(requests.last().endsWith("{as=play, title=Live, url=http://x/live.m3u8}"))

        try {
            client("bad").channels()
            fail("expected unauthorized")
        } catch (e: TvError) {
            assertEquals(TvError.Unauthorized, e)
        }
        try {
            TvClient("127.0.0.1", 1, "good").key(RemoteKey.Ok)
            fail("expected disconnected")
        } catch (e: TvError) {
            assertEquals(TvError.Disconnected, e)
        }
    }

    @Test
    fun readsTheLiveEventStream() = runBlocking {
        val events = withTimeout(5_000) { client("good").events().take(2).toList() }
        val state = events[0].state!!
        assertEquals("Nebula Sports", state.channel!!.name)
        assertTrue(state.channel!!.fav)
        assertNull(state.search)
        assertEquals("toast", events[1].type)
        assertEquals("Hola \"tele\"", events[1].text)
    }

    @Test
    fun eventStreamNeedsPairing() = runBlocking {
        try {
            withTimeout(5_000) { client("bad").events().first() }
            fail("expected unauthorized")
        } catch (e: TvError) {
            assertEquals(TvError.Unauthorized, e)
        }
    }

    @Test
    fun encodesFormsLikeTheIphoneApp() {
        assertEquals("a=1&q=f%C3%BAtbol%20tv", TvClient.formEncode(mapOf("q" to "fútbol tv", "a" to "1")))
        assertNull(TvClient.parseEventLine(": ping"))
        assertEquals("channels", TvClient.parseEventLine("data: {\"type\":\"channels\",\"version\":4}")!!.type)
    }
}
