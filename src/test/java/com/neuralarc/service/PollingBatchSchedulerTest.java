package com.neuralarc.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PollingBatchSchedulerTest {
    @Test
    void nearestBucketSecondsSnapsToClosestStandardBucket() {
        assertEquals(5L, PollingBatchScheduler.nearestBucketSeconds(1L));
        assertEquals(5L, PollingBatchScheduler.nearestBucketSeconds(5L));
        assertEquals(15L, PollingBatchScheduler.nearestBucketSeconds(15L));
        assertEquals(30L, PollingBatchScheduler.nearestBucketSeconds(30L));
        assertEquals(60L, PollingBatchScheduler.nearestBucketSeconds(60L));
        assertEquals(60L, PollingBatchScheduler.nearestBucketSeconds(500L));
    }

    @Test
    void nearestBucketSecondsTieBreaksToTheLowerBucket() {
        // 10 is equidistant between 5 and 15; the lower bucket wins deterministically.
        assertEquals(5L, PollingBatchScheduler.nearestBucketSeconds(10L));
        // 22.5 -> 22/23 equidistant-ish between 15 and 30; 22 rounds toward 15.
        assertEquals(15L, PollingBatchScheduler.nearestBucketSeconds(22L));
    }

    @Test
    void withinCatchUpWindowHonorsFiveSecondBoundaryInclusive() {
        assertTrue(PollingBatchScheduler.withinCatchUpWindow(10_000L, 15_000L));
        assertTrue(PollingBatchScheduler.withinCatchUpWindow(15_000L, 10_000L));
        assertFalse(PollingBatchScheduler.withinCatchUpWindow(10_000L, 15_001L));
    }

    @Test
    void pullsForwardStrategyDueSoonInTheSameBucketAsAnAlreadyDueStrategy() {
        long now = 100_000L;
        Set<Long> dueBuckets = Set.of(60L);
        Map<String, Long> naturalDueAt = new LinkedHashMap<>();
        naturalDueAt.put("s1", now + 3_000L); // within 5s window, same bucket as due
        Map<String, Long> buckets = new LinkedHashMap<>();
        buckets.put("s1", 60L);

        Set<String> pulled = PollingBatchScheduler.pullForwardCandidateStrategyIds(dueBuckets, naturalDueAt, buckets, now);

        assertEquals(Set.of("s1"), pulled);
    }

    @Test
    void doesNotPullForwardWhenBucketDoesNotMatchAnyDueStrategy() {
        long now = 100_000L;
        Set<Long> dueBuckets = Set.of(5L);
        Map<String, Long> naturalDueAt = new LinkedHashMap<>();
        naturalDueAt.put("s1", now + 3_000L);
        Map<String, Long> buckets = new LinkedHashMap<>();
        buckets.put("s1", 60L); // different bucket than what's due this cycle

        Set<String> pulled = PollingBatchScheduler.pullForwardCandidateStrategyIds(dueBuckets, naturalDueAt, buckets, now);

        assertTrue(pulled.isEmpty());
    }

    @Test
    void doesNotPullForwardWhenBeyondTheCatchUpWindow() {
        long now = 100_000L;
        Set<Long> dueBuckets = Set.of(60L);
        Map<String, Long> naturalDueAt = new LinkedHashMap<>();
        naturalDueAt.put("s1", now + 5_001L); // just past the 5s window
        Map<String, Long> buckets = new LinkedHashMap<>();
        buckets.put("s1", 60L);

        Set<String> pulled = PollingBatchScheduler.pullForwardCandidateStrategyIds(dueBuckets, naturalDueAt, buckets, now);

        assertTrue(pulled.isEmpty());
    }

    @Test
    void neverPullsForwardAStrategyAlreadyPastItsOwnDueTime() {
        long now = 100_000L;
        Set<Long> dueBuckets = Set.of(60L);
        Map<String, Long> naturalDueAt = new LinkedHashMap<>();
        naturalDueAt.put("s1", now - 1_000L); // already due; caller's responsibility to have moved it
        Map<String, Long> buckets = new LinkedHashMap<>();
        buckets.put("s1", 60L);

        Set<String> pulled = PollingBatchScheduler.pullForwardCandidateStrategyIds(dueBuckets, naturalDueAt, buckets, now);

        assertTrue(pulled.isEmpty());
    }
}
