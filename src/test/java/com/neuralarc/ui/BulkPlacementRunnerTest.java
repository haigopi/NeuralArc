package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BulkPlacementRunnerTest {
    @Test
    void splitsIntoBatchesOfFive() {
        List<Integer> items = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            items.add(i);
        }

        List<List<Integer>> batches = BulkPlacementRunner.batches(items, BulkPlacementRunner.DEFAULT_BATCH_SIZE);

        assertEquals(3, batches.size());
        assertEquals(List.of(1, 2, 3, 4, 5), batches.get(0));
        assertEquals(List.of(6, 7, 8, 9, 10), batches.get(1));
        assertEquals(List.of(11, 12), batches.get(2));
    }

    @Test
    void emptyInputProducesNoBatches() {
        assertTrue(BulkPlacementRunner.batches(List.of(), 5).isEmpty());
        assertTrue(BulkPlacementRunner.batches(null, 5).isEmpty());
    }

    @Test
    void reportsProgressAfterEveryBatchAndPausesBetweenThem() {
        List<Long> pauses = new ArrayList<>();
        BulkPlacementRunner runner = new BulkPlacementRunner(5, 250L, pauses::add);
        List<BulkPlacementRunner.Progress> updates = new ArrayList<>();

        BulkPlacementRunner.Progress finalProgress = runner.run(
                List.of("A", "B", "C", "D", "E", "F", "G"),
                item -> true,
                updates::add
        );

        assertEquals(2, updates.size());
        assertEquals(5, updates.get(0).completed());
        assertEquals(2, updates.get(0).remaining());
        assertEquals(7, updates.get(1).completed());
        assertEquals(7, finalProgress.placed());
        assertEquals(0, finalProgress.failed());
        assertTrue(finalProgress.finished());
        // Only between batches, never after the last one.
        assertEquals(List.of(250L), pauses);
    }

    @Test
    void placesEveryBatchInOrder() {
        BulkPlacementRunner runner = new BulkPlacementRunner(5, 0L, millis -> { });
        List<String> placedInOrder = new ArrayList<>();

        runner.run(List.of("A", "B", "C", "D", "E", "F"), item -> placedInOrder.add(item), null);

        assertEquals(List.of("A", "B", "C", "D", "E", "F"), placedInOrder);
    }

    @Test
    void rejectedAndThrowingPlacementsCountAsFailedWithoutStoppingTheRun() {
        BulkPlacementRunner runner = new BulkPlacementRunner(2, 0L, millis -> { });

        BulkPlacementRunner.Progress progress = runner.run(
                List.of("ok", "rejected", "boom", "ok"),
                item -> switch (item) {
                    case "rejected" -> false;
                    case "boom" -> throw new IllegalStateException("broker rejected the order");
                    default -> true;
                },
                null
        );

        assertEquals(4, progress.completed());
        assertEquals(2, progress.placed());
        assertEquals(2, progress.failed());
    }

    @Test
    void interruptedRunStopsAfterTheBatchInFlight() {
        BulkPlacementRunner runner = new BulkPlacementRunner(2, 10L, millis -> {
            throw new InterruptedException("cancelled");
        });
        List<String> attempted = new ArrayList<>();

        BulkPlacementRunner.Progress progress = runner.run(
                List.of("A", "B", "C", "D"),
                item -> attempted.add(item),
                null
        );

        assertEquals(List.of("A", "B"), attempted);
        assertEquals(2, progress.completed());
        assertEquals(2, progress.remaining());
        assertFalse(progress.finished());
        assertTrue(Thread.interrupted(), "the interrupt must be restored for the caller");
    }
}
