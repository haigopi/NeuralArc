package com.neuralarc.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PollFailureTrackerTest {
    private final PollFailureTracker tracker = new PollFailureTracker();

    @Test
    void oneBadCycleDoesNotStopAStrategy() {
        assertFalse(PollFailureTracker.shouldPause(tracker.recordFailure("s1")),
                "a rate limit or a dropped connection must not leave a position unwatched");
        assertFalse(PollFailureTracker.shouldPause(tracker.recordFailure("s1")));
    }

    @Test
    void repeatedFailuresAreARealFaultAndPause() {
        tracker.recordFailure("s1");
        tracker.recordFailure("s1");

        assertTrue(PollFailureTracker.shouldPause(tracker.recordFailure("s1")));
        assertEquals(PollFailureTracker.FAILURES_BEFORE_PAUSE, tracker.consecutiveFailures("s1"));
    }

    @Test
    void oneGoodPollClearsWhateverWentWrong() {
        tracker.recordFailure("s1");
        tracker.recordFailure("s1");

        tracker.recordSuccess("s1");

        assertEquals(0, tracker.consecutiveFailures("s1"));
        assertFalse(PollFailureTracker.shouldPause(tracker.recordFailure("s1")), "the count starts again");
    }

    @Test
    void strategiesAreCountedApart() {
        tracker.recordFailure("s1");
        tracker.recordFailure("s1");
        tracker.recordFailure("s2");

        assertEquals(2, tracker.consecutiveFailures("s1"));
        assertEquals(1, tracker.consecutiveFailures("s2"));
    }
}
