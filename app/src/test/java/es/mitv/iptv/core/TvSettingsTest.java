package es.mitv.iptv.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

public class TvSettingsTest {

    private final List<Channel> all = Arrays.asList(
            new Channel("News 1", "News", null, "u1", 1),
            new Channel("Kids 1", "Kids", null, "u2", 2),
            new Channel("Adult 1", "Adults", null, "u3", 3),
            new Channel("News 2", "News", null, "u4", 4));

    private static Set<String> set(String... s) {
        return new HashSet<>(Arrays.asList(s));
    }

    private static String names(List<Channel> list) {
        StringBuilder b = new StringBuilder();
        for (Channel c : list) b.append(c.number);
        return b.toString();
    }

    @Test
    public void hidesAndLocksCategories() {
        Set<String> none = Collections.emptySet();
        assertSame("nothing to filter", all, TvSettings.visible(all, none, none, false));
        assertEquals("124", names(TvSettings.visible(all, none, set("Adults"), false)));
        assertEquals("1234", names(TvSettings.visible(all, none, set("Adults"), true)));
        assertEquals("hidden categories stay hidden when unlocked", "3", names(TvSettings.visible(all, set("News", "Kids"), set("Adults"), true)));
    }

    @Test
    public void categoriesAreSavedPerList() {
        TvSettings s = new TvSettings(new MapStore());
        s.setHiddenGroups("1", set("News"));
        s.setLockedGroups("1", set("Adults"));
        assertEquals(set("News"), s.hiddenGroups("1"));
        assertTrue(s.hiddenGroups("2").isEmpty());
        s.forgetList("1");
        assertTrue(s.lockedGroups("1").isEmpty());
    }

    @Test
    public void reloadsOnceADay() {
        TvSettings s = new TvSettings(new MapStore());
        assertFalse("never loaded yet", s.refreshDue(1_000_000_000L));
        s.setLastLoaded(1_000_000_000L);
        assertFalse(s.refreshDue(1_000_000_000L + TvSettings.REFRESH_EVERY_MS - 1));
        assertTrue(s.refreshDue(1_000_000_000L + TvSettings.REFRESH_EVERY_MS));
        s.setAutoRefresh(false);
        assertFalse(s.refreshDue(1_000_000_000L + 2 * TvSettings.REFRESH_EVERY_MS));
    }

    @Test
    public void languageDefaultsToTheTvs() {
        TvSettings s = new TvSettings(new MapStore());
        assertEquals(TvSettings.LANGUAGE_AUTO, s.language());
        s.setLanguage("es");
        assertEquals("es", s.language());
        s.setLanguage(TvSettings.LANGUAGE_AUTO);
        assertEquals("", s.language());
    }
}
