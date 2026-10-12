package es.mitv.iptv.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * The 4-digit PIN of the parental control. Only a salted hash is saved.
 * It keeps children out of some categories; it isn't meant to stop someone who can reach the setup page.
 */
public final class ParentalLock {

    public static final int PIN_DIGITS = 4;
    private static final String KEY_HASH = "pinHash";
    private static final String KEY_SALT = "pinSalt";

    private final KeyValueStore store;

    public ParentalLock(KeyValueStore store) {
        this.store = store;
    }

    public boolean hasPin() {
        return store.getString(KEY_HASH, null) != null;
    }

    public static boolean isValidPin(String pin) {
        return pin != null && pin.matches("\\d{" + PIN_DIGITS + "}");
    }

    /** Sets a new PIN. Returns false if it isn't 4 digits. */
    public boolean setPin(String pin) {
        if (!isValidPin(pin)) return false;
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String saltHex = hex(salt);
        store.putString(KEY_SALT, saltHex);
        store.putString(KEY_HASH, hash(saltHex, pin));
        return true;
    }

    public void clearPin() {
        store.putString(KEY_HASH, null);
        store.putString(KEY_SALT, null);
    }

    public boolean check(String pin) {
        String saved = store.getString(KEY_HASH, null);
        String salt = store.getString(KEY_SALT, "");
        if (saved == null || pin == null) return false;
        return MessageDigest.isEqual(saved.getBytes(StandardCharsets.UTF_8),
                hash(salt, pin.trim()).getBytes(StandardCharsets.UTF_8));
    }

    private static String hash(String salt, String pin) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            return hex(d.digest((salt + ":" + pin).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder s = new StringBuilder();
        for (byte b : bytes) s.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return s.toString();
    }
}
