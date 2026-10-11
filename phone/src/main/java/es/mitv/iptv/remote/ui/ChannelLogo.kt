package es.mitv.iptv.remote.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.mitv.iptv.remote.Logic
import es.mitv.iptv.remote.TvClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Loads channel logos once and keeps small thumbnails in memory. If a logo can't be fetched, the TV fetches it. */
object LogoStore {
    private const val THUMB_PX = 128
    private const val MAX_BYTES = 1024 * 1024

    private val cache = LruCache<String, ImageBitmap>(600)
    private val inflight = HashMap<String, CompletableDeferred<ImageBitmap?>>()
    private val failedAt = HashMap<String, Long>()   // retried after FAILURE_TTL_MS (the TV may have been offline)
    private const val FAILURE_TTL_MS = 2 * 60 * 1000L
    private val downloads = Semaphore(6)

    fun cached(url: String): ImageBitmap? = cache.get(url)

    /** Call from the main thread. */
    suspend fun image(url: String, number: Int, client: TvClient?): ImageBitmap? {
        cache.get(url)?.let { return it }
        failedAt[url]?.let { if (System.currentTimeMillis() - it < FAILURE_TTL_MS) return null }
        inflight[url]?.let { return it.await() }
        val result = CompletableDeferred<ImageBitmap?>()
        inflight[url] = result
        val image = try {
            downloads.withPermit { load(url, number, client) }
        } finally {
            inflight.remove(url)
        }
        if (image != null) cache.put(url, image) else failedAt[url] = System.currentTimeMillis()
        result.complete(image)
        return image
    }

    private suspend fun load(url: String, number: Int, client: TvClient?): ImageBitmap? {
        var data = withContext(Dispatchers.IO) { fetch(url) }
        if (data == null && number > 0 && client != null) {
            data = try {
                client.logo(number)
            } catch (e: Exception) {
                null
            }
        }
        return data?.let { withContext(Dispatchers.Default) { decode(it) } }
    }

    private fun fetch(address: String): ByteArray? {
        if (!Logic.isWebLink(address)) return null
        var c: HttpURLConnection? = null
        return try {
            c = URL(address).openConnection() as HttpURLConnection
            c.connectTimeout = 8_000
            c.readTimeout = 10_000
            c.instanceFollowRedirects = true
            if (c.responseCode != 200) return null
            c.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(8192)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_BYTES) return null
                }
                out.toByteArray()
            }
        } catch (e: Exception) {
            null
        } finally {
            c?.disconnect()
        }
    }

    private fun decode(data: ByteArray): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= THUMB_PX && bounds.outHeight / (sample * 2) >= THUMB_PX) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(data, 0, data.size, opts)?.asImageBitmap()
    }
}

/** Square logo with the channel's initial as a placeholder (like the TV list). */
@Composable
fun ChannelLogo(number: Int, logo: String?, name: String, client: TvClient?, size: Dp = 44.dp) {
    val shape = RoundedCornerShape(size * 0.26f)
    var image by remember(number, logo) { mutableStateOf(logo?.let(LogoStore::cached)) }
    LaunchedEffect(number, logo) {
        if (logo.isNullOrEmpty() || image != null) return@LaunchedEffect
        image = LogoStore.image(logo, number, client)
    }
    Box(Modifier.size(size).card(shape, Palette.surface2), contentAlignment = Alignment.Center) {
        Crossfade(image, label = "logo") { bitmap ->
            if (bitmap != null) {
                Image(
                    bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(size * 0.14f),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        Logic.initial(name),
                        color = Palette.text2,
                        fontSize = (size.value * 0.4f).sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
