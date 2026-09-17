package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartPicksParallelExecutorTest {
    @Test
    void mapPreservingOrderRunsTasksInParallel() {
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxRunning = new AtomicInteger();
        CopyOnWriteArrayList<Integer> completedCounts = new CopyOnWriteArrayList<>();

        List<Integer> result = SmartPicksParallelExecutor.mapPreservingOrder(
                List.of(1, 2, 3, 4),
                "test-smart-picks-parallel",
                value -> {
                    int active = running.incrementAndGet();
                    maxRunning.accumulateAndGet(active, Math::max);
                    try {
                        Thread.sleep(120);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } finally {
                        running.decrementAndGet();
                    }
                    return value * 10;
                },
                completedCounts::add
        );

        assertEquals(List.of(10, 20, 30, 40), result);
        assertTrue(maxRunning.get() > 1, "expected more than one Smart Picks task to run at the same time");
        assertEquals(4, completedCounts.size());
        assertTrue(completedCounts.contains(4));
    }

    @Test
    void threadCountUsesBoundedPool() {
        assertEquals(1, SmartPicksParallelExecutor.threadCount(1));
        assertTrue(SmartPicksParallelExecutor.threadCount(20) <= 3,
                "each symbol costs six market-data requests, so the pool stays small to respect the rate limit");
        assertTrue(SmartPicksParallelExecutor.threadCount(20) >= 2);
    }

    @Test
    void analysisRunsInSmallSpacedBatchesRatherThanOneBurst() {
        // Twenty symbols at six requests each is ~120 calls; sent at once, Alpaca rate-limits them.
        List<List<Integer>> batches = BulkPlacementRunner.batches(
                java.util.stream.IntStream.rangeClosed(1, 20).boxed().toList(),
                SmartPicksTrendingStocksDialog.ANALYSIS_BATCH_SIZE);

        assertEquals(5, batches.size());
        assertTrue(batches.stream().allMatch(batch -> batch.size() <= SmartPicksTrendingStocksDialog.ANALYSIS_BATCH_SIZE));
        assertEquals(20, batches.stream().mapToInt(List::size).sum(), "no symbol is dropped by batching");
        assertTrue(SmartPicksTrendingStocksDialog.ANALYSIS_BATCH_PAUSE_MILLIS > 0,
                "batches must be spaced, or batching alone does not slow the burst");
    }

    @Test
    void theInFlightRequestCeilingStaysWellUnderTheRateLimit() {
        // Threads x requests-per-symbol is what actually hits Alpaca at any instant.
        int requestsPerSymbol = 6;
        int worstCaseInFlight = SmartPicksParallelExecutor.threadCount(20) * requestsPerSymbol;

        assertTrue(worstCaseInFlight <= 18, "worst case was " + worstCaseInFlight + " concurrent requests");
    }
}
