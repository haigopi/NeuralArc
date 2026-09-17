package com.neuralarc.api;

/**
 * How long to wait before retrying a request the broker rate-limited.
 *
 * <p>Alpaca's limit is per minute, so a 429 is nearly always transient: the same request succeeds a
 * second or two later. Failing immediately turned one crowded moment into a permanently failed
 * analysis for that symbol, which is why a run of twenty symbols could come back half empty.
 *
 * <p>Delays double and are capped, so a genuinely exhausted quota is reported rather than retried
 * forever. Only rate limiting is worth retrying: an authentication or validation failure returns the
 * same answer however many times it is asked.
 */
final class RateLimitBackoff {
    /** Total attempts for one request, the first included. */
    static final int MAX_ATTEMPTS = 4;
    private static final long FIRST_DELAY_MILLIS = 500L;
    private static final long MAX_DELAY_MILLIS = 4_000L;

    private RateLimitBackoff() {
    }

    /** True while another attempt is worth making after {@code completedAttempts} have failed. */
    static boolean hasAttemptsLeft(int completedAttempts) {
        return completedAttempts < MAX_ATTEMPTS;
    }

    /** Wait before the retry that follows {@code completedAttempts}: 500ms, 1s, 2s, then capped at 4s. */
    static long delayMillis(int completedAttempts) {
        if (completedAttempts < 1) {
            return FIRST_DELAY_MILLIS;
        }
        long doublings = Math.min(completedAttempts - 1, 8);
        long delay = FIRST_DELAY_MILLIS << doublings;
        return Math.min(delay, MAX_DELAY_MILLIS);
    }
}
