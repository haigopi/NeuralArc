package com.neuralarc.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consecutive poll failures per strategy, so one bad cycle does not cost the operator a position.
 *
 * <p>A poll touches the network several times — quotes, positions, open orders — and any of those can
 * fail for a second: a 429, a dropped connection, a symbol whose data arrives late. Pausing on the
 * first exception turned those seconds into an indefinite stop: the row read "Canceled (System
 * Error)" and stayed there, not monitoring its stop loss, until someone noticed and resumed it by
 * hand.
 *
 * <p>Failures are counted instead, and only a strategy that keeps failing is paused — by then it is
 * a real fault worth an operator's attention rather than a blip. A single success clears the count.
 */
final class PollFailureTracker {
    /** Consecutive failures a strategy is allowed before it is paused for a human to look at. */
    static final int FAILURES_BEFORE_PAUSE = 3;

    private final Map<String, Integer> failures = new ConcurrentHashMap<>();

    /** Records a failed poll; returns how many have now failed in a row. */
    int recordFailure(String strategyId) {
        if (strategyId == null) {
            return FAILURES_BEFORE_PAUSE;
        }
        return failures.merge(strategyId, 1, Integer::sum);
    }

    /** A successful poll means whatever went wrong has passed. */
    void recordSuccess(String strategyId) {
        if (strategyId != null) {
            failures.remove(strategyId);
        }
    }

    int consecutiveFailures(String strategyId) {
        return strategyId == null ? 0 : failures.getOrDefault(strategyId, 0);
    }

    /** True once a strategy has failed often enough that it should stop and be looked at. */
    static boolean shouldPause(int consecutiveFailures) {
        return consecutiveFailures >= FAILURES_BEFORE_PAUSE;
    }
}
