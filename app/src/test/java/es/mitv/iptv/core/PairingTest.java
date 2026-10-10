package es.mitv.iptv.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Before;
import org.junit.Test;

public class PairingTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private Map<String, String> saved;
    private Pairing pairing;

    @Before
    public void setUp() {
        saved = new LinkedHashMap<>();
        pairing = new Pairing(store(), now::get);
    }

    private Pairing.Store store() {
        return new Pairing.Store() {
            @Override public Map<String, String> load() { return new LinkedHashMap<>(saved); }
            @Override public void save(Map<String, String> devices) { saved = new LinkedHashMap<>(devices); }
        };
    }

    @Test
    public void correctPinGivesAWorkingToken() {
        String pin = pairing.start("iPhone de Daniel");
        assertEquals(Pairing.PIN_DIGITS, pin.length());
        assertEquals("iPhone de Daniel", pairing.pendingDevice());

        Pairing.Result r = pairing.confirm(pin, "iPhone de Daniel");
        assertTrue(r.ok());
        assertTrue(pairing.isAuthorized(r.token));
        assertEquals("iPhone de Daniel", pairing.deviceName(r.token));
        assertFalse("the PIN can only be used once", pairing.isPending());
        assertFalse(pairing.confirm(pin, "x").ok());
    }

    @Test
    public void unknownTokensAreRejected() {
        assertFalse(pairing.isAuthorized(null));
        assertFalse(pairing.isAuthorized(""));
        assertFalse(pairing.isAuthorized("nope"));
    }

    @Test
    public void activePinIsReusedSoTheScreenDoesNotChange() {
        String first = pairing.start("a");
        now.addAndGet(30_000);
        assertEquals(first, pairing.start("a"));
    }

    @Test
    public void pinExpires() {
        String pin = pairing.start("a");
        now.addAndGet(Pairing.PIN_VALID_MS + 1);
        Pairing.Result r = pairing.confirm(pin, "a");
        assertFalse(r.ok());
        assertTrue(r.expired);
    }

    @Test
    public void tooManyWrongPinsDiscardTheCode() {
        String pin = pairing.start("a");
        String wrong = pin.equals("0000") ? "1111" : "0000";
        for (int i = 1; i < Pairing.MAX_ATTEMPTS; i++) {
            Pairing.Result r = pairing.confirm(wrong, "a");
            assertFalse(r.ok());
            assertFalse(r.expired);
        }
        assertTrue(pairing.confirm(wrong, "a").expired);
        assertFalse("even the right PIN no longer works", pairing.confirm(pin, "a").ok());
    }

    @Test
    public void devicesSurviveARestartAndCanBeForgotten() {
        String token = pairing.confirm(pairing.start("Phone 1"), "Phone 1").token;
        Pairing again = new Pairing(store(), now::get);
        assertTrue(again.isAuthorized(token));
        assertEquals(1, again.deviceNames().size());

        again.forget(token);
        assertFalse(again.isAuthorized(token));
        assertTrue(saved.isEmpty());
    }

    @Test
    public void keepsAtMostTenDevices() {
        String first = pairing.confirm(pairing.start("p0"), "p0").token;
        for (int i = 1; i <= Pairing.MAX_DEVICES; i++) {
            String t = pairing.confirm(pairing.start("p" + i), "p" + i).token;
            assertNotEquals(first, t);
        }
        assertEquals(Pairing.MAX_DEVICES, pairing.deviceNames().size());
        assertFalse("the oldest device is dropped", pairing.isAuthorized(first));
    }

    @Test
    public void cleansDeviceNames() {
        assertEquals("Phone", Pairing.cleanName(null));
        assertEquals("Phone", Pairing.cleanName("  \n "));
        assertEquals("ab", Pairing.cleanName("a\u0000b"));
        assertEquals(40, Pairing.cleanName("x".repeat(100)).length());
        assertNull(pairing.pendingDevice());
    }
}
