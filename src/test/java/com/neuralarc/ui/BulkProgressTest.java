package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BulkProgressTest {
    @Test
    void theCountMovesWithTheRun() {
        BulkProgress progress = new BulkProgress("Re-enter Inactive Stocks", 4);

        assertEquals("0 of 4 · starting", progress.headline("starting"));
        assertEquals(0, progress.percent());

        progress.record("AAPL: placed at $12.40", BulkProgress.Outcome.DONE);
        progress.record("MSFT: skipped, still held at the broker", BulkProgress.Outcome.SKIPPED);

        assertEquals("2 of 4 · MSFT", progress.headline("MSFT"));
        assertEquals(50, progress.percent());
        assertFalse(progress.finished());
    }

    @Test
    void aRunIsOnlyCleanWhenNothingFailed() {
        BulkProgress progress = new BulkProgress("Sell All Positions", 2);

        progress.record("AAPL", BulkProgress.Outcome.DONE);
        assertTrue(progress.clean());

        progress.record("MSFT: failed, insufficient shares", BulkProgress.Outcome.FAILED);
        assertFalse(progress.clean(), "a window must not close over an order that did not go in");
        assertTrue(progress.finished());
        assertEquals(1, progress.failures().size());
    }

    @Test
    void theSummaryLeavesOutWhatDidNotHappen() {
        BulkProgress clean = new BulkProgress("Cancel All", 2);
        clean.record("AAPL", BulkProgress.Outcome.DONE);
        clean.record("MSFT", BulkProgress.Outcome.DONE);
        assertEquals("2 done", clean.summary());

        BulkProgress mixed = new BulkProgress("Cancel All", 3);
        mixed.record("AAPL", BulkProgress.Outcome.DONE);
        mixed.record("MSFT", BulkProgress.Outcome.SKIPPED);
        mixed.record("TSLA: failed, rejected", BulkProgress.Outcome.FAILED);
        assertEquals("1 done · 1 skipped · 1 failed", mixed.summary());
    }

    @Test
    void theFinalCountIsTheActionsOwnNotTheRunningTally() {
        BulkProgress progress = new BulkProgress("Cancel All", 3);
        progress.record("AAPL", BulkProgress.Outcome.DONE);

        progress.settle(List.of("AAPL", "MSFT"), List.of(), List.of("TSLA: failed, rejected"));

        assertEquals("2 done · 1 failed", progress.summary());
        assertEquals(3, progress.completed());
        assertFalse(progress.clean());
    }

    @Test
    void aRunThatNeverReportedItemByItemStillListsItsResults() {
        BulkProgress progress = new BulkProgress("Import", 3);

        progress.settle(List.of("AAPL"), List.of("MSFT"), List.of("TSLA: failed"));

        assertEquals(3, progress.lines().size());
        assertEquals(1, progress.failures().size());
        assertEquals("TSLA: failed", progress.failures().getFirst().text());
    }

    @Test
    void anEmptyRunIsShownAsFinishedRatherThanStuckAtNothing() {
        BulkProgress progress = new BulkProgress("Nothing To Do", 0);

        assertEquals(100, progress.percent());
        assertTrue(progress.finished());
        assertTrue(progress.clean());
    }

    @Test
    void theResultLineSaysHowItEnded() {
        assertEquals(BulkProgress.Outcome.DONE, BulkProgress.classify("AAPL: placed at $12.40"));
        assertEquals(BulkProgress.Outcome.DONE, BulkProgress.classify("AAPL: re-posted at $12.40"));
        assertEquals(BulkProgress.Outcome.SKIPPED, BulkProgress.classify("AAPL: skipped, still held"));
        assertEquals(BulkProgress.Outcome.FAILED, BulkProgress.classify("AAPL: failed, rejected"));
        assertEquals(BulkProgress.Outcome.FAILED, BulkProgress.classify("Failed: connection reset"),
                "the whole-run failure line is a failure too");
    }

    @Test
    void finishedLinesSplitIntoTheBucketsTheWindowExpects() {
        List<String> outcomes = List.of(
                "AAPL: placed at $12.40", "MSFT: skipped, no recent prices", "TSLA: failed, rejected");

        assertEquals(List.of("AAPL: placed at $12.40"), BulkProgress.only(outcomes, BulkProgress.Outcome.DONE));
        assertEquals(List.of("MSFT: skipped, no recent prices"),
                BulkProgress.only(outcomes, BulkProgress.Outcome.SKIPPED));
        assertEquals(List.of("TSLA: failed, rejected"), BulkProgress.only(outcomes, BulkProgress.Outcome.FAILED));
    }

    @Test
    void aSilentHandleSwallowsEverythingSoWorkNeedsNoScreen() {
        BulkProgressHandle.NONE.advance("AAPL", BulkProgress.Outcome.DONE);
        BulkProgressHandle.NONE.finish(List.of("AAPL"), List.of(), List.of());
        BulkProgressHandle.NONE.abort("broker unreachable");
    }
}
