package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PollingCountdownPolicyTest {

    private static final long INTERVAL_MILLIS = 60_000L;

    @Test
    void initializesCountdownFromLastPolledAtWhenNotYetActive() {
        long now = 1_000_000L;
        long lastPolledAt = now - 10_000L;

        PollingCountdownPolicy.Result result = PollingCountdownPolicy.resolve(
                false, 0L, 0L, 0L, lastPolledAt, INTERVAL_MILLIS, now);

        assertEquals(lastPolledAt + INTERVAL_MILLIS, result.nextPollDueAtMillis());
        assertEquals(lastPolledAt, result.lastAppliedPolledAtEpochMilli());
        assertEquals(INTERVAL_MILLIS, result.lastAppliedPollIntervalMillis());
    }

    @Test
    void staleSnapshotWithUnchangedLastPolledAtDoesNotClobberFreshDueTime() {
        // Regression test: onStrategyPollCompleted() just set a fresh future due time
        // (computed from "now", not from lastPolledAt), but the very next re-sync tick
        // reads a repository snapshot whose lastPolledAt hasn't caught up yet (still the
        // pre-poll value that made this strategy "due" in the first place). That stale
        // read must NOT overwrite the fresh value — this was the "stuck at 0s forever" bug.
        long now = 1_000_000L;
        long lastPolledAt = now - 65_000L; // pre-poll snapshot, already overdue
        long freshNextDueAt = now + INTERVAL_MILLIS; // just set by markPollingCycleCompleted

        PollingCountdownPolicy.Result result = PollingCountdownPolicy.resolve(
                true, freshNextDueAt, lastPolledAt, INTERVAL_MILLIS, lastPolledAt, INTERVAL_MILLIS, now);

        assertEquals(freshNextDueAt, result.nextPollDueAtMillis());
        assertEquals(lastPolledAt, result.lastAppliedPolledAtEpochMilli());
    }

    @Test
    void newlyObservedLastPolledAtReanchorsTheCountdown() {
        long now = 1_000_000L;
        long previouslyAppliedPolledAt = now - 65_000L;
        long freshlyCompletedPolledAt = now; // repository snapshot has now caught up
        long staleNextDueAt = now - 5_000L; // left over from before completion landed

        PollingCountdownPolicy.Result result = PollingCountdownPolicy.resolve(
                true, staleNextDueAt, previouslyAppliedPolledAt, INTERVAL_MILLIS,
                freshlyCompletedPolledAt, INTERVAL_MILLIS, now);

        assertEquals(freshlyCompletedPolledAt + INTERVAL_MILLIS, result.nextPollDueAtMillis());
        assertEquals(freshlyCompletedPolledAt, result.lastAppliedPolledAtEpochMilli());
    }

    @Test
    void intervalChangeWithSameLastPolledAtReanchorsTheCountdown() {
        long now = 1_000_000L;
        long lastPolledAt = now - 10_000L;
        long oldIntervalDueAt = lastPolledAt + INTERVAL_MILLIS; // due time under the old interval
        long shorterInterval = 5_000L;

        PollingCountdownPolicy.Result result = PollingCountdownPolicy.resolve(
                true, oldIntervalDueAt, lastPolledAt, INTERVAL_MILLIS, lastPolledAt, shorterInterval, now);

        assertEquals(lastPolledAt + shorterInterval, result.nextPollDueAtMillis());
        assertEquals(shorterInterval, result.lastAppliedPollIntervalMillis());
    }

    @Test
    void nullLastPolledAtFallsBackToNowWhenInitializing() {
        long now = 1_000_000L;

        PollingCountdownPolicy.Result result = PollingCountdownPolicy.resolve(
                false, 0L, 0L, 0L, null, INTERVAL_MILLIS, now);

        assertEquals(now + INTERVAL_MILLIS, result.nextPollDueAtMillis());
        assertEquals(now, result.lastAppliedPolledAtEpochMilli());
    }

    @Test
    void nullLastPolledAtNeverReanchorsAnActiveCountdown() {
        long now = 1_000_000L;
        long activeNextDueAt = now + 30_000L;

        PollingCountdownPolicy.Result result = PollingCountdownPolicy.resolve(
                true, activeNextDueAt, 0L, INTERVAL_MILLIS, null, INTERVAL_MILLIS, now);

        assertEquals(activeNextDueAt, result.nextPollDueAtMillis());
    }
}
