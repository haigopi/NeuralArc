package com.neuralarc.service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Session-only, cycle-level counter of consecutive failed shared broker-snapshot fetches. This is
 * informational by default (drives the UI "Attempt N" label / Warning-Paused state) and only
 * actually stops polling when an operator opts into {@code maxValidationAttemptsBeforePause}.
 * Deliberately not persisted and deliberately separate from the existing per-strategy
 * {@code StrategyStatus.PAUSED}/{@code PauseReason.SYSTEM_ERROR} business-pause path — a failed
 * batch fetch is shared infrastructure health, not a per-strategy business condition, and it never
 * mutates a persisted {@code Strategy}.
 */
final class PositionValidationAttemptTracker {
    private final AtomicInteger consecutiveBatchFailures = new AtomicInteger(0);

    void recordCycleBatchResult(boolean batchFetchSucceeded) {
        if (batchFetchSucceeded) {
            consecutiveBatchFailures.set(0);
        } else {
            consecutiveBatchFailures.incrementAndGet();
        }
    }

    int activeAttempt() {
        return consecutiveBatchFailures.get() + 1;
    }

    boolean isWarningPaused() {
        return consecutiveBatchFailures.get() >= 1;
    }

    void resetOnManualRefresh() {
        consecutiveBatchFailures.set(0);
    }
}
