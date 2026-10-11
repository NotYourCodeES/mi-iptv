package es.mitv.iptv.remote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import kotlin.coroutines.resume

/** A Mi IPTV TV found on the Wi-Fi. */
data class DiscoveredTv(
    val id: String,         // the TV's own id (stable), or the service name for older versions
    val name: String,
    val host: String?,
    val port: Int?,
)

/**
 * Browses for `_miiptv._tcp` services (the TV announces itself with the same NsdManager).
 * The TV's id and IP come from the TXT record, which Android only reads when a service is resolved,
 * so every service found is resolved, one at a time (older Android versions allow only one).
 */
class Discovery(context: Context, private val scope: CoroutineScope) {

    var onChange: ((List<DiscoveredTv>) -> Unit)? = null
    var onProblem: ((Boolean) -> Unit)? = null

    private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager?
    private var listener: NsdManager.DiscoveryListener? = null
    private val found = LinkedHashMap<String, DiscoveredTv>()      // by service name
    private var toResolve = Channel<NsdServiceInfo>(Channel.UNLIMITED)
    private var resolver: Job? = null

    fun start() {
        if (nsd == null || listener != null) return
        val queue = Channel<NsdServiceInfo>(Channel.UNLIMITED)
        toResolve = queue
        resolver = scope.launch {
            for (service in queue) resolve(service)?.let { tv ->
                found[service.serviceName] = tv
                publish()
            }
        }
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                scope.launch { onProblem?.invoke(false) }
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                queue.trySend(service)
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                scope.launch {
                    if (found.remove(service.serviceName) != null) publish()
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                scope.launch {
                    listener = null
                    onProblem?.invoke(true)
                }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        listener = l
        try {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l)
        } catch (e: RuntimeException) {
            listener = null
            onProblem?.invoke(true)
        }
    }

    fun stop() {
        val l = listener ?: return
        listener = null
        resolver?.cancel()
        toResolve.close()
        try {
            nsd?.stopServiceDiscovery(l)
        } catch (ignored: RuntimeException) {
        }
    }

    fun restart() {
        stop()
        found.clear()
        publish()
        start()
    }

    private fun publish() {
        onChange?.invoke(found.values.sortedBy { it.name.lowercase() })
    }

    @Suppress("DEPRECATION") // resolveService is the only way that works from API 26 to 33
    private suspend fun resolve(service: NsdServiceInfo): DiscoveredTv? {
        val manager = nsd ?: return null
        val info = withTimeoutOrNull(8_000) {
            suspendCancellableCoroutine<NsdServiceInfo?> { cont ->
                try {
                    manager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(s: NsdServiceInfo, errorCode: Int) {
                            if (cont.isActive) cont.resume(null)
                        }

                        override fun onServiceResolved(s: NsdServiceInfo) {
                            if (cont.isActive) cont.resume(s)
                        }
                    })
                } catch (e: RuntimeException) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        } ?: return null
        val txt = info.attributes.mapValues { (_, v) -> v?.toString(Charsets.UTF_8) }
        val host = txt["ip"]?.takeIf { it.isNotEmpty() } ?: addressOf(info)
        val port = txt["port"]?.toIntOrNull() ?: info.port.takeIf { it > 0 }
        return DiscoveredTv(
            id = txt["id"]?.takeIf { it.isNotEmpty() } ?: service.serviceName,
            name = service.serviceName,
            host = host,
            port = port,
        )
    }

    @Suppress("DEPRECATION")
    private fun addressOf(info: NsdServiceInfo): String? {
        if (Build.VERSION.SDK_INT >= 34) {
            info.hostAddresses.firstOrNull { it is Inet4Address }?.let { return it.hostAddress }
        }
        return info.host?.hostAddress?.substringBefore('%')
    }

    companion object {
        const val SERVICE_TYPE = "_miiptv._tcp"
    }
}
