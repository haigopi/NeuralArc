package com.neuralarc.model;

import org.json.JSONObject;

/** The app's own keys inside a stored broker order response, and keeping them when the response is refreshed. */
final class OrderMetadata {
    /** Every key this app adds to an order's raw JSON starts with this; the broker never sends one. */
    static final String KEY_PREFIX = "neuralarc";

    private OrderMetadata() {
    }

    /**
     * @return {@code fresh} with every {@link #KEY_PREFIX} key of {@code previous} that it lacks copied in;
     *         {@code "{}"} when there is no fresh response. A response that cannot be parsed is returned unchanged.
     */
    static String carryOver(String previous, String fresh) {
        if (fresh == null) {
            return "{}";
        }
        if (previous == null || !previous.contains(KEY_PREFIX)) {
            return fresh;
        }
        try {
            JSONObject old = new JSONObject(previous);
            JSONObject merged = fresh.isBlank() ? new JSONObject() : new JSONObject(fresh);
            boolean changed = false;
            for (String key : old.keySet()) {
                if (key.startsWith(KEY_PREFIX) && !merged.has(key)) {
                    merged.put(key, old.get(key));
                    changed = true;
                }
            }
            return changed ? merged.toString() : fresh;
        } catch (Exception ignored) {
            return fresh;
        }
    }
}
