package es.mitv.iptv.core;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pairs phones with the TV: the TV shows a short PIN, the phone sends it back once and
 * receives a long random token that it uses on every later request.
 */
public final class Pairing {

    /** Where the paired devices are kept (SharedPreferences on the TV, a map in tests). */
    public interface Store {
        /** Token → device name, oldest first. */
        Map<String, String> load();
        void save(Map<String, String> devices);
    }

    /** Current time in ms (replaceable in tests; java.util.function is not available on API 21). */
    public interface Clock {
        long now();
    }

    public static final int PIN_DIGITS = 4;
    public static final long PIN_VALID_MS = 2 * 60 * 1000;
    static final int MAX_ATTEMPTS = 5;
    static final int MAX_DEVICES = 10;
    /** Pause after {@link #MAX_ATTEMPTS} wrong codes; it doubles each time, up to {@link #MAX_LOCKOUT_MS}. */
    static final long LOCKOUT_MS = 30 * 1000;
    static final long MAX_LOCKOUT_MS = 15 * 60 * 1000;
    /** Wrong codes older than this are forgotten. */
    static final long FORGIVE_MS = 60 * 60 * 1000;

    private final Store store;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final LinkedHashMap<String, String> devices = new LinkedHashMap<>();

    private String pin;
    private String pinDevice;
    private long pinExpires;
    // Wrong codes are counted across PINs, so asking for a new PIN doesn't give fresh tries
    private int attempts;
    private int lockouts;
    private long lockedUntil;
    private long lastFailure;

    public Pairing(Store store) {
        this(store, System::currentTimeMillis);
    }

    public Pairing(Store store, Clock clock) {
        this.store = store;
        this.clock = clock;
        Map<String, String> saved = store.load();
        if (saved != null) devices.putAll(saved);
    }

    /**
     * Starts (or continues) a pairing request and returns the PIN to show on the TV.
     * An active PIN is reused so the number on screen doesn't change under the user.
     * Returns null while pairing is locked after too many wrong codes (see {@link #lockedForMs()}).
     */
    public synchronized String start(String deviceName) {
        long now = clock.now();
        if (now < lockedUntil) return null;
        forgiveOldFailures(now);
        if (pin == null || now >= pinExpires) {
            pin = String.format(Locale.ROOT, "%0" + PIN_DIGITS + "d", random.nextInt(pow10(PIN_DIGITS)));
        }
        pinDevice = cleanName(deviceName);
        pinExpires = now + PIN_VALID_MS;
        return pin;
    }

    /** The device name of the pending request (for the TV overlay), or null. */
    public synchronized String pendingDevice() {
        return isPending() ? pinDevice : null;
    }

    public synchronized boolean isPending() {
        return pin != null && clock.now() < pinExpires;
    }

    /** How long pairing stays locked after too many wrong codes (0 if it isn't). */
    public synchronized long lockedForMs() {
        return Math.max(0, lockedUntil - clock.now());
    }

    public synchronized void cancel() {
        pin = null;
        pinDevice = null;
    }

    /** Result of a pairing attempt. */
    public static final class Result {
        public final String token;     // non-null on success
        public final boolean expired;  // the PIN is gone: the phone has to ask for a new one

        Result(String token, boolean expired) {
            this.token = token;
            this.expired = expired;
        }

        public boolean ok() { return token != null; }
    }

    /**
     * Checks the PIN typed on the phone. After {@link #MAX_ATTEMPTS} wrong tries the PIN is discarded
     * and no new one can be requested for a while.
     */
    public synchronized Result confirm(String typedPin, String deviceName) {
        if (!isPending()) {
            pin = null;
            return new Result(null, true);
        }
        long now = clock.now();
        forgiveOldFailures(now);
        String typed = typedPin == null ? "" : typedPin.trim();
        boolean match = MessageDigest.isEqual(typed.getBytes(StandardCharsets.UTF_8), pin.getBytes(StandardCharsets.UTF_8));
        if (!match) {
            attempts++;
            lastFailure = now;
            if (attempts >= MAX_ATTEMPTS) {
                attempts = 0;
                lockouts++;
                lockedUntil = now + Math.min(LOCKOUT_MS << Math.min(lockouts - 1, 10), MAX_LOCKOUT_MS);
                pin = null;
                pinDevice = null;
                return new Result(null, true);
            }
            return new Result(null, false);
        }
        attempts = 0;
        lockouts = 0;
        String name = cleanName(deviceName != null && !deviceName.trim().isEmpty() ? deviceName : pinDevice);
        String token = newToken();
        devices.put(token, name);
        while (devices.size() > MAX_DEVICES) devices.remove(devices.keySet().iterator().next());
        store.save(new LinkedHashMap<>(devices));
        pin = null;
        pinDevice = null;
        return new Result(token, false);
    }

    public synchronized boolean isAuthorized(String token) {
        if (token == null || token.isEmpty()) return false;
        for (String t : devices.keySet()) {
            if (MessageDigest.isEqual(t.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) return true;
        }
        return false;
    }

    public synchronized String deviceName(String token) {
        return token == null ? null : devices.get(token);
    }

    /** Names of the paired devices, oldest first. */
    public synchronized List<String> deviceNames() {
        return Collections.unmodifiableList(new ArrayList<>(devices.values()));
    }

    public synchronized void forget(String token) {
        if (token != null && devices.remove(token) != null) store.save(new LinkedHashMap<>(devices));
    }

    public synchronized void forgetAll() {
        devices.clear();
        store.save(new LinkedHashMap<>(devices));
    }

    private void forgiveOldFailures(long now) {
        if (lastFailure != 0 && now - lastFailure >= FORGIVE_MS) {
            attempts = 0;
            lockouts = 0;
            lastFailure = 0;
        }
    }

    private String newToken() {
        byte[] b = new byte[24];
        random.nextBytes(b);
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format(Locale.ROOT, "%02x", x & 0xff));
        return s.toString();
    }

    static String cleanName(String name) {
        if (name == null) return "Phone";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < name.length() && b.length() < 40; i++) {
            char c = name.charAt(i);
            if (c >= 0x20 && c != 0x7f) b.append(c);
        }
        String s = b.toString().trim();
        return s.isEmpty() ? "Phone" : s;
    }

    private static int pow10(int n) {
        int r = 1;
        for (int i = 0; i < n; i++) r *= 10;
        return r;
    }
}
