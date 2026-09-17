package com.neuralarc.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitBackoffTest {
    @Test
    void theWaitDoublesBetweenAttempts() {
        assertEquals(500L, RateLimitBackoff.delayMillis(1));
        assertEquals(1_000L, RateLimitBackoff.delayMillis(2));
        assertEquals(2_000L, RateLimitBackoff.delayMillis(3));
    }

    @Test
    void theWaitIsCappedSoARunNeverStalls() {
        assertEquals(4_000L, RateLimitBackoff.delayMillis(4));
        assertEquals(4_000L, RateLimitBackoff.delayMillis(50));
        assertEquals(4_000L, RateLimitBackoff.delayMillis(Integer.MAX_VALUE), "no overflow at the extreme");
    }

    @Test
    void retryingStopsRatherThanLoopingForever() {
        assertTrue(RateLimitBackoff.hasAttemptsLeft(1));
        assertTrue(RateLimitBackoff.hasAttemptsLeft(RateLimitBackoff.MAX_ATTEMPTS - 1));
        assertFalse(RateLimitBackoff.hasAttemptsLeft(RateLimitBackoff.MAX_ATTEMPTS),
                "an exhausted quota is reported, not retried forever");
        assertFalse(RateLimitBackoff.hasAttemptsLeft(RateLimitBackoff.MAX_ATTEMPTS + 5));
    }

    @Test
    void theWholeRetrySequenceStaysUnderTenSeconds() {
        // A symbol's analysis must not hang the run while waiting out a limit.
        long total = 0;
        for (int attempt = 1; RateLimitBackoff.hasAttemptsLeft(attempt); attempt++) {
            total += RateLimitBackoff.delayMillis(attempt);
        }

        assertTrue(total <= 10_000L, "total backoff was " + total + "ms");
    }

    @Test
    void aNonPositiveAttemptStillYieldsAUsableDelay() {
        assertEquals(500L, RateLimitBackoff.delayMillis(0));
        assertEquals(500L, RateLimitBackoff.delayMillis(-3));
    }
}
