package es.mitv.iptv.core;

/** A playlist channel: name, group (category), logo and stream URL. */
public final class Channel {
    public final String name;
    public final String group;
    public final String logo;
    public final String url;
    /** Channel number within the full playlist (1-based). */
    public final int number;

    public Channel(String name, String group, String logo, String url) {
        this(name, group, logo, url, 0);
    }

    public Channel(String name, String group, String logo, String url, int number) {
        this.name = name;
        this.group = group;
        this.logo = logo;
        this.url = url;
        this.number = number;
    }

    @Override
    public String toString() {
        return name;
    }
}
