package es.mitv.iptv.core;

import java.util.Collections;
import java.util.Map;

/** A parsed request: method, path, query and form parameters and headers (lower-case names). */
public final class HttpRequest {
    public final String method;
    public final String path;
    public final Map<String, String> query;
    public final Map<String, String> headers;
    public final Map<String, String> form;

    public HttpRequest(String method, String path, Map<String, String> query,
                       Map<String, String> headers, Map<String, String> form) {
        this.method = method;
        this.path = path;
        this.query = query != null ? query : Collections.<String, String>emptyMap();
        this.headers = headers != null ? headers : Collections.<String, String>emptyMap();
        this.form = form != null ? form : Collections.<String, String>emptyMap();
    }

    /** A form field, falling back to the query string; trimmed, never null. */
    public String param(String name) {
        String v = form.get(name);
        if (v == null) v = query.get(name);
        return v == null ? "" : v.trim();
    }

    /** The token sent as "Authorization: Bearer ...". */
    public String bearerToken() {
        String h = headers.get("authorization");
        if (h == null) return null;
        h = h.trim();
        if (h.regionMatches(true, 0, "Bearer ", 0, 7)) return h.substring(7).trim();
        return null;
    }
}
