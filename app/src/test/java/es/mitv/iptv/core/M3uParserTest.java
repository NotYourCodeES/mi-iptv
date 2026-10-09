package es.mitv.iptv.core;

import static org.junit.Assert.assertEquals;

import java.io.StringReader;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class M3uParserTest {

    private static final String PLAYLIST = "﻿#EXTM3U x-tvg-url=\"http://epg\"\n"
            + "#EXTINF:-1 tvg-id=\"one\" tvg-name=\"One HD\" tvg-logo=\"http://logo/1.png\" group-title=\"News\",One HD\n"
            + "http://host/live/u/p/1.ts\n\n"
            + "#EXTINF:-1 tvg-name=\"Two, HD\" group-title=\"News\",Two, HD\n"
            + "#EXTVLCOPT:http-user-agent=foo\n"
            + "http://host/live/u/p/2.m3u8\n"
            + "#EXTINF:-1,No group\n#EXTGRP:Sports\nhttp://host/3\n"
            + "#EXTINF:-1 tvg-name=\"Only tvg-name\",\nhttp://host/4\n";

    private List<Channel> parse() throws Exception {
        return M3uParser.parse(new StringReader(PLAYLIST));
    }

    @Test
    public void parsesAllChannelsWithNumbers() throws Exception {
        List<Channel> channels = parse();
        assertEquals(4, channels.size());
        for (int i = 0; i < channels.size(); i++) assertEquals(i + 1, channels.get(i).number);
    }

    @Test
    public void readsNameGroupLogoAndUrl() throws Exception {
        Channel c = parse().get(0);
        assertEquals("One HD", c.name);
        assertEquals("News", c.group);
        assertEquals("http://logo/1.png", c.logo);
        assertEquals("http://host/live/u/p/1.ts", c.url);
    }

    @Test
    public void keepsCommasInsideChannelNames() throws Exception {
        assertEquals("Two, HD", parse().get(1).name);
    }

    @Test
    public void supportsExtgrpAndFallbacks() throws Exception {
        List<Channel> channels = parse();
        assertEquals("Sports", channels.get(2).group);
        assertEquals("Only tvg-name", channels.get(3).name);
        assertEquals(M3uParser.NO_GROUP, channels.get(3).group);
    }

    @Test
    public void listsGroupsInOrderOfAppearance() throws Exception {
        assertEquals(Arrays.asList("News", "Sports", M3uParser.NO_GROUP), M3uParser.groups(parse()));
    }
}
