package es.mitv.iptv.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses M3U / M3U8 playlists (including the Xtream "m3u_plus" format). */
public final class M3uParser {
    /** Group key used for channels without a group (translated by the UI). */
    public static final String NO_GROUP = "Uncategorized";

    private static final Pattern ATTR = Pattern.compile("([a-zA-Z0-9_-]+)=\"([^\"]*)\"");

    private M3uParser() {}

    public static List<Channel> parse(Reader reader) throws IOException {
        BufferedReader in = new BufferedReader(reader);
        List<Channel> out = new ArrayList<>();
        String name = null, group = null, logo = null;
        String line;
        boolean first = true;
        while ((line = in.readLine()) != null) {
            if (first) {
                // Strip the UTF-8 BOM if present
                if (!line.isEmpty() && line.charAt(0) == '﻿') line = line.substring(1);
                first = false;
            }
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#EXTINF")) {
                name = null; group = null; logo = null;
                Matcher m = ATTR.matcher(line);
                String tvgName = null;
                while (m.find()) {
                    String key = m.group(1).toLowerCase();
                    String val = m.group(2).trim();
                    if (key.equals("group-title")) group = val;
                    else if (key.equals("tvg-logo")) logo = val;
                    else if (key.equals("tvg-name")) tvgName = val;
                }
                int comma = firstCommaOutsideQuotes(line);
                if (comma >= 0 && comma + 1 < line.length()) name = line.substring(comma + 1).trim();
                if (name == null || name.isEmpty()) name = tvgName;
            } else if (line.startsWith("#EXTGRP:")) {
                if (group == null || group.isEmpty()) group = line.substring(8).trim();
            } else if (line.startsWith("#")) {
                // Other tags (#EXTM3U, #EXTVLCOPT...) are ignored
            } else {
                String n = (name == null || name.isEmpty()) ? "Channel " + (out.size() + 1) : name;
                String g = (group == null || group.isEmpty()) ? NO_GROUP : group;
                out.add(new Channel(n, g, logo, line, out.size() + 1));
                name = null; group = null; logo = null;
            }
        }
        return out;
    }

    private static int firstCommaOutsideQuotes(String s) {
        boolean inQuotes = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') inQuotes = !inQuotes;
            else if (c == ',' && !inQuotes) return i;
        }
        return -1;
    }

    /** Groups in the order they first appear in the playlist. */
    public static List<String> groups(List<Channel> channels) {
        List<String> groups = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Channel c : channels) {
            if (seen.add(c.group)) groups.add(c.group);
        }
        return groups;
    }
}
