package com.neuralarc.service;

import org.json.JSONObject;

import java.util.Optional;

/**
 * Reads OpenAI's error responses so a failure says what actually went wrong.
 *
 * <p>OpenAI answers HTTP 429 for two very different conditions: a per-minute rate limit, which clears
 * in seconds and is worth retrying, and {@code insufficient_quota}, meaning the account has no credit
 * left, which no retry will ever fix. Reporting only the status code made the two indistinguishable,
 * so an exhausted account looked like a transient hiccup on every scan.
 */
final class OpenAiErrors {
    static final String INSUFFICIENT_QUOTA = "insufficient_quota";
    /** Total attempts for one request, the first included. */
    static final int MAX_ATTEMPTS = 3;
    private static final long FIRST_DELAY_MILLIS = 2_000L;
    private static final long MAX_DELAY_MILLIS = 20_000L;
    private static final int MESSAGE_LIMIT = 200;

    private OpenAiErrors() {
    }

    /** The {@code error.code} (or {@code error.type}) from an error body, when there is one. */
    static Optional<String> code(String body) {
        JSONObject error = error(body);
        if (error == null) {
            return Optional.empty();
        }
        String code = error.optString("code", "");
        if (code.isBlank() || "null".equals(code)) {
            code = error.optString("type", "");
        }
        return code.isBlank() ? Optional.empty() : Optional.of(code);
    }

    /** A rate limit or a server-side failure clears on its own; quota, auth and bad requests do not. */
    static boolean retryable(int status, String body) {
        if (status == 429) {
            return !code(body).map(INSUFFICIENT_QUOTA::equals).orElse(false);
        }
        return status >= 500;
    }

    /** "OpenAI request failed with HTTP 429 (insufficient_quota: You exceeded your current quota...)". */
    static String describe(int status, String body) {
        StringBuilder text = new StringBuilder("OpenAI request failed with HTTP ").append(status);
        Optional<String> code = code(body);
        JSONObject error = error(body);
        String message = error == null ? "" : error.optString("message", "").strip();
        if (code.isPresent() || !message.isBlank()) {
            text.append(" (").append(code.orElse("error"));
            if (!message.isBlank()) {
                text.append(": ").append(message.length() > MESSAGE_LIMIT
                        ? message.substring(0, MESSAGE_LIMIT) + "..."
                        : message);
            }
            text.append(')');
        }
        if (code.map(INSUFFICIENT_QUOTA::equals).orElse(false)) {
            text.append(". The OpenAI account has no remaining credit; add billing or switch AI provider in Settings.");
        }
        return text.toString();
    }

    /**
     * Wait before the retry following {@code completedAttempts}: the server's Retry-After when it sent
     * one, otherwise 2s then 4s, never more than 20s.
     */
    static long delayMillis(int completedAttempts, Optional<String> retryAfterSeconds) {
        Optional<Long> serverDelay = retryAfterSeconds.flatMap(OpenAiErrors::parseSeconds);
        long delay = serverDelay.orElseGet(() -> FIRST_DELAY_MILLIS << Math.max(0, Math.min(completedAttempts - 1, 4)));
        return Math.max(0L, Math.min(delay, MAX_DELAY_MILLIS));
    }

    private static Optional<Long> parseSeconds(String value) {
        try {
            return Optional.of(Math.round(Double.parseDouble(value.trim()) * 1000));
        } catch (NumberFormatException | NullPointerException ex) {
            return Optional.empty();
        }
    }

    private static JSONObject error(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return new JSONObject(body).optJSONObject("error");
        } catch (Exception ex) {
            return null;
        }
    }
}
