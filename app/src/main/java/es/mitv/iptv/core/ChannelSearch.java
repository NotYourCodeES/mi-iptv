package es.mitv.iptv.core;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Accent- and case-insensitive channel search ("telecinco" finds "TeleCinco HD", "6" finds channel 6). */
public final class ChannelSearch {

    private ChannelSearch() {}

    public static List<Channel> find(List<Channel> channels, String query) {
        List<Channel> out = new ArrayList<>();
        String q = normalize(query);
        if (q.isEmpty()) return out;
        int number = -1;
        if (q.matches("\\d{1,6}")) number = Integer.parseInt(q);
        for (Channel c : channels) {
            if (c.number == number || normalize(c.name).contains(q)) out.add(c);
        }
        // An exact channel number goes first
        if (number > 0) {
            for (int i = 0; i < out.size(); i++) {
                if (out.get(i).number == number) {
                    out.add(0, out.remove(i));
                    break;
                }
            }
        }
        return out;
    }

    public static String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
