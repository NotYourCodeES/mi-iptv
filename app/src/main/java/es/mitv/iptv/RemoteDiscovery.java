package es.mitv.iptv;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;

import java.util.Map;

import es.mitv.iptv.core.RemoteApi;

/**
 * Announces the TV on the local network (mDNS / Bonjour) so the phone app finds it by itself.
 * The TXT record carries the TV id (to recognise a paired TV after its IP changes) and its IP.
 */
final class RemoteDiscovery {

    private final NsdManager nsd;
    private NsdManager.RegistrationListener listener;

    RemoteDiscovery(Context context) {
        nsd = (NsdManager) context.getApplicationContext().getSystemService(Context.NSD_SERVICE);
    }

    void register(String name, int port, Map<String, String> txt) {
        if (nsd == null || listener != null) return;
        NsdServiceInfo info = new NsdServiceInfo();
        info.setServiceName(name);
        info.setServiceType(RemoteApi.SERVICE_TYPE);
        info.setPort(port);
        for (Map.Entry<String, String> e : txt.entrySet()) info.setAttribute(e.getKey(), e.getValue());
        listener = new NsdManager.RegistrationListener() {
            @Override public void onServiceRegistered(NsdServiceInfo s) {}
            @Override public void onRegistrationFailed(NsdServiceInfo s, int error) { listener = null; }
            @Override public void onServiceUnregistered(NsdServiceInfo s) {}
            @Override public void onUnregistrationFailed(NsdServiceInfo s, int error) {}
        };
        try {
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener);
        } catch (RuntimeException e) {
            listener = null; // discovery is a convenience: the phone can still connect by IP
        }
    }

    void unregister() {
        if (nsd == null || listener == null) return;
        try {
            nsd.unregisterService(listener);
        } catch (RuntimeException ignored) {
        }
        listener = null;
    }
}
