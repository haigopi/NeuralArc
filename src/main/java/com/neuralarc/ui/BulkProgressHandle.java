package com.neuralarc.ui;

import java.util.List;

/**
 * The thin end of the progress window, held by whatever is doing the work.
 *
 * <p>The work runs off the EDT and should not know whether anything is watching, so this is what it
 * talks to: one call per item, one call at the end. {@link #NONE} is a working implementation that
 * shows nothing, which is what a scheduled run or a test gets.
 */
interface BulkProgressHandle {
    BulkProgressHandle NONE = new BulkProgressHandle() {
        @Override
        public void advance(String item, BulkProgress.Outcome outcome) {
        }

        @Override
        public void finish(List<String> successes, List<String> skipped, List<String> failures) {
        }
    };

    /** One item is done with. Safe to call from any thread. */
    void advance(String item, BulkProgress.Outcome outcome);

    /**
     * The run is over. A run with no failures closes the window; one with failures leaves it open,
     * because an order that did not go in is not something to close over the operator's head.
     */
    void finish(List<String> successes, List<String> skipped, List<String> failures);

    /** The run failed outright, before it could report item by item. */
    default void abort(String reason) {
        finish(List.of(), List.of(), List.of(reason == null ? "Failed" : reason));
    }
}
