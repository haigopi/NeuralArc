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
        assertTrue(SmartPicksParallelExecutor.threadCount(20) <= 6);
        assertTrue(SmartPicksParallelExecutor.threadCount(20) >= 2);
    }
}
