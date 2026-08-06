package com.neuralarc.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PositionValidationAttemptTrackerTest {
    private final PositionValidationAttemptTracker tracker = new PositionValidationAttemptTracker();

    @Test
    void startsAtAttemptOneAndNotWarningPaused() {
        assertEquals(1, tracker.activeAttempt());
        assertFalse(tracker.isWarningPaused());
    }

    @Test
    void aFailedBatchFetchIncrementsTheAttemptAndEntersWarningPaused() {
        tracker.recordCycleBatchResult(false);

        assertEquals(2, tracker.activeAttempt());
        assertTrue(tracker.isWarningPaused());
    }

    @Test
    void consecutiveFailuresKeepIncrementing() {
        tracker.recordCycleBatchResult(false);
        tracker.recordCycleBatchResult(false);
        tracker.recordCycleBatchResult(false);

        assertEquals(4, tracker.activeAttempt());
        assertTrue(tracker.isWarningPaused());
    }

    @Test
    void aSuccessfulBatchFetchResetsTheCounter() {
        tracker.recordCycleBatchResult(false);
        tracker.recordCycleBatchResult(false);

        tracker.recordCycleBatchResult(true);

        assertEquals(1, tracker.activeAttempt());
        assertFalse(tracker.isWarningPaused());
    }

    @Test
    void manualRefreshResetsTheCounterEvenWithoutASuccessfulFetch() {
        tracker.recordCycleBatchResult(false);
        tracker.recordCycleBatchResult(false);

        tracker.resetOnManualRefresh();

        assertEquals(1, tracker.activeAttempt());
        assertFalse(tracker.isWarningPaused());
    }
}
