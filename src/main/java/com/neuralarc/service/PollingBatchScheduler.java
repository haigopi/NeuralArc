package com.neuralarc.service;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Pure scheduling math for grouping due strategies into synchronized batches. Never reads or
 * mutates {@code Strategy.pollingIntervalSeconds()} for anything other than computing these
 * grouping keys — a strategy's own configured interval is never overridden. Deliberately takes no
 * domain objects (no {@code Strategy} references) so it stays trivially unit-testable.
 */
final class PollingBatchScheduler {
    static final long CATCH_UP_WINDOW_MILLIS = 5_000L;
    static final long[] STANDARD_BUCKET_SECONDS = {5L, 15L, 30L, 60L};

    private PollingBatchScheduler() {
    }

    /** Nearest of {5,15,30,60}s to a strategy's own configured interval — a grouping/telemetry key only. */
    static long nearestBucketSeconds(long configuredIntervalSeconds) {
        long safeInterval = Math.max(1L, configuredIntervalSeconds);
        long nearest = STANDARD_BUCKET_SECONDS[0];
        long smallestDiff = Math.abs(safeInterval - nearest);
        for (long bucket : STANDARD_BUCKET_SECONDS) {
            long diff = Math.abs(safeInterval - bucket);
            if (diff < smallestDiff) {
                smallestDiff = diff;
                nearest = bucket;
            }
        }
        return nearest;
    }

    /** True when two due-at instants (millis) fall within the 5-second catch-up window of each other. */
    static boolean withinCatchUpWindow(long dueAtMillisA, long dueAtMillisB) {
        return Math.abs(dueAtMillisA - dueAtMillisB) <= CATCH_UP_WINDOW_MILLIS;
    }

    /**
     * Strategy IDs that are not yet due but should be pulled into this cycle's batch: their own
     * natural due time is within the 5s catch-up window of now, AND they share a standard bucket
     * with a strategy that's already firing this cycle. Never pulls a strategy in later than its
     * own due time — only ever slightly early, bounded to the catch-up window.
     */
    static Set<String> pullForwardCandidateStrategyIds(
            Set<Long> dueBucketsSecondsThisCycle,
            Map<String, Long> notYetDueNaturalDueAtMillis,
            Map<String, Long> notYetDueBucketSeconds,
            long nowMillis
    ) {
        Set<String> pulledForward = new LinkedHashSet<>();
        for (Map.Entry<String, Long> entry : notYetDueNaturalDueAtMillis.entrySet()) {
            String strategyId = entry.getKey();
            long naturalDueAtMillis = entry.getValue();
            if (naturalDueAtMillis <= nowMillis || naturalDueAtMillis - nowMillis > CATCH_UP_WINDOW_MILLIS) {
                continue;
            }
            Long bucket = notYetDueBucketSeconds.get(strategyId);
            if (bucket != null && dueBucketsSecondsThisCycle.contains(bucket)) {
                pulledForward.add(strategyId);
            }
        }
        return pulledForward;
    }
}
