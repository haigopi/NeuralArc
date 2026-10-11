package com.neuralarc.ui;

import java.util.HashMap;
import java.util.Map;

/**
 * Lets a recurring condition be logged once instead of on every refresh: a message repeating the
 * previous one for the same key is dropped, and clearing the key makes the next occurrence log again.
 */
final class RepeatedLogFilter {
    private final Map<String, String> lastByKey = new HashMap<>();

    /** @return true when {@code message} differs from the last one logged for {@code key}. */
    boolean shouldLog(String key, String message) {
        return !message.equals(lastByKey.put(key, message));
    }

    /** The condition no longer holds; its next appearance is news again. */
    void clear(String key) {
        lastByKey.remove(key);
    }
}
