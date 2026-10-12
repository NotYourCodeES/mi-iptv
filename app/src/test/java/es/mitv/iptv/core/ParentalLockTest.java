package es.mitv.iptv.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ParentalLockTest {

    @Test
    public void checksThePinWithoutSavingIt() {
        MapStore store = new MapStore();
        ParentalLock lock = new ParentalLock(store);
        assertFalse(lock.hasPin());
        assertFalse(lock.check("1234"));

        assertTrue(lock.setPin("4821"));
        assertTrue(lock.hasPin());
        assertTrue(lock.check("4821"));
        assertTrue(lock.check(" 4821 "));
        assertFalse(lock.check("1234"));
        assertFalse("only a hash is kept", store.values.containsValue("4821"));

        assertTrue(new ParentalLock(store).check("4821"));
        lock.clearPin();
        assertFalse(lock.hasPin());
        assertFalse(lock.check("4821"));
    }

    @Test
    public void pinsAreFourDigits() {
        ParentalLock lock = new ParentalLock(new MapStore());
        assertFalse(lock.setPin("123"));
        assertFalse(lock.setPin("12345"));
        assertFalse(lock.setPin("12a4"));
        assertFalse(lock.hasPin());
    }
}
