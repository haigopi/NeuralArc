package com.neuralarc.api;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Makes an endpoint readable for the log. Query values arrive percent-encoded, so a symbol list logs
 * as {@code AAPL%2CAMZN%2CMETA}; this prints it as {@code AAPL,AMZN,META}. For logging only — the
 * request itself is sent with the original encoded URL.
 */
final class ApiLogUrls {
    private ApiLogUrls() {
    }

    /** Decodes each query value; the path and the {@code &}-separated structure are left as they are. */
    static String readable(String endpoint) {
        if (endpoint == null) {
            return "";
        }
        int query = endpoint.indexOf('?');
        if (query < 0) {
            return endpoint;
        }
        StringBuilder out = new StringBuilder(endpoint.substring(0, query + 1));
        String[] pairs = endpoint.substring(query + 1).split("&", -1);
        for (int i = 0; i < pairs.length; i++) {
            if (i > 0) {
                out.append('&');
            }
            int equals = pairs[i].indexOf('=');
            if (equals < 0) {
                out.append(pairs[i]);
            } else {
                out.append(pairs[i], 0, equals + 1).append(decode(pairs[i].substring(equals + 1)));
            }
        }
        return out.toString();
    }

    private static String decode(String value) {
        try {
            // A decoded value could hold a line break; keep one log record on one line.
            return URLDecoder.decode(value, StandardCharsets.UTF_8).replaceAll("[\\r\\n\\t]+", " ");
        } catch (IllegalArgumentException malformedEscape) {
            return value;
        }
    }
}
