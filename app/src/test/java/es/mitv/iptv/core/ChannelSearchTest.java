package es.mitv.iptv.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class ChannelSearchTest {

    private final List<Channel> channels = Arrays.asList(
            new Channel("La 1 HD", "General", null, "u1", 1),
            new Channel("Telecinco", "General", null, "u2", 2),
            new Channel("Antena 3", "General", null, "u3", 3),
            new Channel("Cuatro", "General", null, "u4", 4),
            new Channel("Canal Fútbol 3", "Deportes", null, "u5", 5));

    @Test
    public void ignoresCaseAndAccents() {
        assertEquals(1, ChannelSearch.find(channels, "TELECINCO").size());
        assertEquals("Canal Fútbol 3", ChannelSearch.find(channels, "futbol").get(0).name);
        assertEquals("Canal Fútbol 3", ChannelSearch.find(channels, "  FÚTBOL ").get(0).name);
    }

    @Test
    public void exactNumberComesFirst() {
        List<Channel> r = ChannelSearch.find(channels, "3");
        assertEquals(3, r.get(0).number);           // channel 3
        assertEquals(2, r.size());                  // and "Canal Fútbol 3", which has 3 in its name
    }

    @Test
    public void emptyQueryFindsNothing() {
        assertTrue(ChannelSearch.find(channels, "").isEmpty());
        assertTrue(ChannelSearch.find(channels, null).isEmpty());
    }
}
