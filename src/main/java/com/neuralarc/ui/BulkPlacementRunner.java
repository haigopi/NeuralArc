package com.neuralarc.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Places a bulk order run in small batches instead of one long burst.
 *
 * <p>A workspace can hold hundreds of pending rows, and submitting them all in one uninterrupted
 * sweep leaves the app looking hung and gives the broker no room to breathe. This runner walks the
 * list a few symbols at a time, reports progress after every batch, and pauses briefly between
 * batches. It performs no Swing work itself: the caller runs it on a background thread and marshals
 * the progress callback onto the EDT.
 */
final class BulkPlacementRunner {
    static final int DEFAULT_BATCH_SIZE = 5;
    static final long DEFAULT_PAUSE_BETWEEN_BATCHES_MILLIS = 250L;

    /** Places one item. Returns true when the broker accepted it. */
    @FunctionalInterface
    interface Placement<T> {
        boolean place(T item);
    }

    /** Injected so tests do not wait out the pause between batches. */
    @FunctionalInterface
    interface Pause {
        void sleep(long millis) throws InterruptedException;
    }

    /** How far the run has got. {@code completed} counts both placed and failed items. */
    record Progress(int placed, int failed, int completed, int total) {
        int remaining() {
            return Math.max(0, total - completed);
        }

        boolean finished() {
            return completed >= total;
        }
    }

    private final int batchSize;
    private final long pauseBetweenBatchesMillis;
    private final Pause pause;

    BulkPlacementRunner() {
        this(DEFAULT_BATCH_SIZE, DEFAULT_PAUSE_BETWEEN_BATCHES_MILLIS, Thread::sleep);
    }

    BulkPlacementRunner(int batchSize, long pauseBetweenBatchesMillis, Pause pause) {
        this.batchSize = Math.max(1, batchSize);
        this.pauseBetweenBatchesMillis = Math.max(0L, pauseBetweenBatchesMillis);
        this.pause = pause;
    }

    /** Splits {@code items} into consecutive batches of at most {@code batchSize}. */
    static <T> List<List<T>> batches(List<T> items, int batchSize) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        int size = Math.max(1, batchSize);
        List<List<T>> batches = new ArrayList<>();
        for (int start = 0; start < items.size(); start += size) {
            batches.add(List.copyOf(items.subList(start, Math.min(start + size, items.size()))));
        }
        return batches;
    }

    /**
     * Places every item, one batch at a time. A placement that throws counts as failed so a single
     * bad symbol cannot abandon the rest of the run. Interrupting the calling thread stops the run
     * after the batch in flight and returns the progress reached so far.
     */
    <T> Progress run(List<T> items, Placement<T> placement, Consumer<Progress> onBatchCompleted) {
        int total = items == null ? 0 : items.size();
        int placed = 0;
        int completed = 0;
        for (List<T> batch : batches(items, batchSize)) {
            for (T item : batch) {
                boolean accepted;
                try {
                    accepted = placement.place(item);
                } catch (RuntimeException ex) {
                    accepted = false;
                }
                if (accepted) {
                    placed++;
                }
                completed++;
            }
            Progress progress = new Progress(placed, completed - placed, completed, total);
            if (onBatchCompleted != null) {
                onBatchCompleted.accept(progress);
            }
            if (completed < total && !pauseBetweenBatches()) {
                return progress;
            }
        }
        return new Progress(placed, completed - placed, completed, total);
    }

    private boolean pauseBetweenBatches() {
        if (Thread.currentThread().isInterrupted()) {
            return false;
        }
        if (pauseBetweenBatchesMillis <= 0L) {
            return true;
        }
        try {
            pause.sleep(pauseBetweenBatchesMillis);
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
