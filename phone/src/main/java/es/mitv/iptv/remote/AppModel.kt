package es.mitv.iptv.remote

import android.app.Application
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A TV we are about to pair with (shown in the code sheet). */
data class PairingTarget(val id: String, val name: String, val host: String, val port: Int)

sealed interface PinResult {
    object Ok : PinResult
    object Wrong : PinResult
    object Expired : PinResult
    data class Failed(val message: String) : PinResult
}

data class Toast(val id: Long, val text: String)

enum class Connection { Idle, Connecting, Online, Offline }

/** What to tell the user when a call to the TV fails. */
fun describeError(context: Context, e: Throwable): String = when (e) {
    TvError.Unauthorized -> context.getString(R.string.error_unauthorized)
    TvError.NotMiIptv -> context.getString(R.string.error_not_miiptv)
    is TvError.BadResponse -> context.getString(R.string.error_bad_response)
    is TvError.Message -> e.text.ifEmpty { context.getString(R.string.error_link) }
    else -> context.getString(R.string.error_disconnected)
}

/** Everything the app knows about the TV, and every action it can take on it. All on the main thread. */
class AppModel(app: Application) : AndroidViewModel(app) {

    // Finding and pairing
    var discovered by mutableStateOf<List<DiscoveredTv>>(emptyList()); private set
    var discoveryProblem by mutableStateOf(false); private set
    var pairingTarget by mutableStateOf<PairingTarget?>(null)
    var connectError by mutableStateOf<String?>(null)
    var connectingTo by mutableStateOf<String?>(null); private set

    // The paired TV
    var paired by mutableStateOf(PairedTvStore.load(app)); private set
    var connection by mutableStateOf(Connection.Idle); private set
    var state by mutableStateOf<TvState?>(null); private set

    // Channel list
    var channels by mutableStateOf<List<TvChannel>>(emptyList()); private set
    var groups by mutableStateOf<List<String>>(emptyList()); private set
    var favorites by mutableStateOf<Set<String>>(emptySet()); private set
    var loadingChannels by mutableStateOf(false); private set
    private var channelsVersion = -1
    private var folded: List<String> = emptyList()   // names without case/accents, same order as channels
    private var byNumber: Map<Int, Int> = emptyMap()  // channel number → index

    var toast by mutableStateOf<Toast?>(null); private set

    // Settings
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var useTouchpad by mutableStateOf(prefs.getBoolean("useTouchpad", true)); private set
    /** The phone's volume buttons change the TV's volume while the app is open. */
    var volumeKeys by mutableStateOf(prefs.getBoolean("volumeKeys", true)); private set

    fun updateUseTouchpad(on: Boolean) {
        useTouchpad = on
        prefs.edit().putBoolean("useTouchpad", on).apply()
    }

    fun updateVolumeKeys(on: Boolean) {
        volumeKeys = on
        prefs.edit().putBoolean("volumeKeys", on).apply()
    }

    var client: TvClient? = null; private set
    private val discovery = Discovery(app, viewModelScope)
    private var eventsJob: Job? = null
    private var lastCommand: Job? = null

    init {
        discovery.onChange = ::discoveryChanged
        discovery.onProblem = { discoveryProblem = it }
    }

    private fun text(id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

    fun errorText(e: Throwable): String = describeError(getApplication(), e)

    // ---------------------------------------------------------------- Lifecycle

    fun appBecameActive() {
        discovery.start()
        if (paired != null && (connection != Connection.Online || eventsJob == null)) connect()
    }

    fun appWentToBackground() {
        eventsJob?.cancel()
        eventsJob = null
        connection = Connection.Idle
        discovery.stop()
    }

    fun retryDiscovery() = discovery.restart()

    override fun onCleared() {
        discovery.stop()
    }

    // ---------------------------------------------------------------- Pairing

    val deviceName: String
        get() {
            val app = getApplication<Application>()
            val name = Settings.Global.getString(app.contentResolver, Settings.Global.DEVICE_NAME)
            return if (name.isNullOrBlank()) Build.MODEL ?: "Android" else name
        }

    fun choose(tv: DiscoveredTv) {
        val host = tv.host
        if (host == null) {
            connectError = text(R.string.error_no_address)
            return
        }
        viewModelScope.launch { beginPairing(host, tv.port ?: 8080, tv.name, tv.id) }
    }

    fun chooseManually(text: String) {
        val (host, port) = Logic.parseAddress(text) ?: return
        viewModelScope.launch { beginPairing(host, port, host, host) }
    }

    private suspend fun beginPairing(host: String, port: Int, fallbackName: String, key: String) {
        connectError = null
        connectingTo = key
        try {
            val c = TvClient(host, port, null)
            val info = c.info()
            c.startPairing(deviceName)
            pairingTarget = PairingTarget(info.id, info.name.ifEmpty { fallbackName }, host, port)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            connectError = errorText(e)
        } finally {
            connectingTo = null
        }
    }

    /** Asks the TV to show the code again (or a new one if it expired). Returns an error to show, if any. */
    suspend fun requestNewCode(target: PairingTarget): String? = try {
        TvClient(target.host, target.port, null).startPairing(deviceName)
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        errorText(e)
    }

    suspend fun submitPin(pin: String, target: PairingTarget): PinResult = try {
        val r = TvClient(target.host, target.port, null).pair(pin, deviceName)
        val token = r.token
        if (!r.ok || token == null) {
            if (r.expired == true) PinResult.Expired else PinResult.Wrong
        } else {
            val tv = PairedTv(r.id ?: target.id, r.name ?: target.name, target.host, target.port, token)
            PairedTvStore.save(getApplication(), tv)
            paired = tv
            pairingTarget = null
            connect()
            PinResult.Ok
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        PinResult.Failed(errorText(e))
    }

    /** Unpairs on the TV (if it's reachable) and goes back to the connect screen. */
    fun disconnect() {
        val c = client
        viewModelScope.launch {
            try {
                c?.unpair()
            } catch (e: CancellationException) {
                throw e
            } catch (ignored: Exception) {
            }
            forgetTv(null)
        }
    }

    private fun forgetTv(message: String?) {
        eventsJob?.cancel()
        eventsJob = null
        PairedTvStore.save(getApplication(), null)
        paired = null
        client = null
        state = null
        channels = emptyList()
        groups = emptyList()
        favorites = emptySet()
        folded = emptyList()
        byNumber = emptyMap()
        channelsVersion = -1
        connection = Connection.Idle
        connectError = message
        discovery.start()
    }

    // ---------------------------------------------------------------- Connection

    fun connect() {
        val tv = paired ?: return
        eventsJob?.cancel()
        val c = TvClient(tv.host, tv.port, tv.token)
        client = c
        connection = Connection.Connecting
        eventsJob = viewModelScope.launch { listen(c) }
    }

    /** Keeps the event stream open, reconnecting with a growing delay when the TV goes away. */
    private suspend fun listen(c: TvClient) {
        var delaySeconds = 1L
        while (true) {
            try {
                c.events().collect { event ->
                    if (connection != Connection.Online) {
                        connection = Connection.Online
                        delaySeconds = 1
                    }
                    apply(event)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: TvError.Unauthorized) {
                forgetTv(text(R.string.error_unauthorized))
                return
            } catch (ignored: Exception) {
                // Dropped or unreachable: try again below
            }
            connection = Connection.Offline
            delay(delaySeconds * 1000)
            delaySeconds = minOf(delaySeconds * 2, 8)
        }
    }

    private fun apply(event: TvEvent) {
        when (event.type) {
            "state" -> event.state?.let { s ->
                state = s
                s.channel?.let { ch ->
                    if (ch.n > 0) favorites = if (ch.fav) favorites + ch.name else favorites - ch.name
                }
                if (s.version != channelsVersion) viewModelScope.launch { loadChannels() }
            }
            "channels" -> viewModelScope.launch { loadChannels() }
            "favorites" -> event.favorites?.let { favorites = it.toSet() }
            "toast" -> event.text?.let(::show)
        }
    }

    /** The TV moved to another IP (DHCP): follow it using its id. */
    private fun discoveryChanged(tvs: List<DiscoveredTv>) {
        discovered = tvs
        val tv = paired ?: return
        val found = tvs.firstOrNull { it.id == tv.id } ?: return
        val host = found.host ?: return
        val port = found.port ?: return
        if (host != tv.host || port != tv.port) {
            val moved = tv.copy(host = host, port = port)
            paired = moved
            PairedTvStore.save(getApplication(), moved)
            connect()
        }
    }

    // ---------------------------------------------------------------- Channels

    suspend fun loadChannels() {
        val c = client ?: return
        if (loadingChannels) return
        loadingChannels = true
        try {
            val list = c.channels()
            folded = list.channels.map { Logic.fold(it.name) }
            byNumber = list.channels.withIndex().associate { (i, ch) -> ch.n to i }
            channels = list.channels
            groups = list.groups
            favorites = list.favorites.toSet()
            channelsVersion = list.version
        } catch (e: CancellationException) {
            throw e
        } catch (e: TvError.Unauthorized) {
            forgetTv(text(R.string.error_unauthorized))
        } catch (ignored: Exception) {
            // Keep the previous list; it will be fetched again on the next state change
        } finally {
            loadingChannels = false
        }
    }

    fun channel(number: Int): TvChannel? = byNumber[number]?.let { channels[it] }

    fun groupName(channel: TvChannel): String = channel.g?.let { groups.getOrNull(it) } ?: ""

    fun isFavorite(name: String) = name in favorites

    fun isPlaying(channel: TvChannel) = state?.channel?.n == channel.n

    fun filteredChannels(query: String, filter: ChannelFilter): List<TvChannel> =
        Logic.filter(channels, folded, favorites, query, filter)

    // ---------------------------------------------------------------- Commands (in order, so fast taps arrive in order)

    fun press(key: RemoteKey) = run { it.key(key) }

    fun play(channel: TvChannel) = run { it.play(channel.n) }

    fun play(number: Int) = run { it.play(number) }

    fun toggleFavorite(channel: TvChannel) = toggleFavorite(channel.n, channel.name)

    fun toggleFavorite(number: Int, name: String) {
        val on = name !in favorites
        favorites = if (on) favorites + name else favorites - name // instant feedback
        run {
            it.favorite(number, on)
            null
        }
    }

    fun searchOnTv(query: String) = run {
        it.search(query)
        null
    }

    fun reloadOnTv() = run { c ->
        c.reload()?.let(::show)
        null
    }

    suspend fun share(url: String, asPlaylist: Boolean): String {
        val c = client ?: return text(R.string.not_connected)
        return try {
            c.share(url, null, asPlaylist) ?: text(R.string.sent_to_tv)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorText(e)
        }
    }

    private fun run(operation: suspend (TvClient) -> OkResponse?) {
        val c = client ?: return
        val previous = lastCommand
        lastCommand = viewModelScope.launch {
            previous?.join()
            try {
                val r = operation(c)
                if (r != null && !r.ok && r.message != null) show(r.message)
            } catch (e: CancellationException) {
                throw e
            } catch (e: TvError.Unauthorized) {
                forgetTv(text(R.string.error_unauthorized))
            } catch (e: Exception) {
                show(errorText(e))
            }
        }
    }

    fun show(text: String) {
        toast = Toast(System.nanoTime(), text)
    }

    fun dismissToast(id: Long) {
        if (toast?.id == id) toast = null
    }
}
