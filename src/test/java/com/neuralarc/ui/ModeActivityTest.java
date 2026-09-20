package com.neuralarc.ui;

import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModeActivityTest {
    @Test
    void withLiveSelectedNothingRunsForPaper() {
        assertFalse(ModeActivity.runsFor(StrategyMode.PAPER, StrategyMode.LIVE));
        assertTrue(ModeActivity.runsFor(StrategyMode.LIVE, StrategyMode.LIVE));
    }

    @Test
    void liveKeepsRunningWhilePaperIsViewedSoItsStopsAreNeverUnwatched() {
        assertTrue(ModeActivity.runsFor(StrategyMode.PAPER, StrategyMode.PAPER));
        assertTrue(ModeActivity.runsFor(StrategyMode.LIVE, StrategyMode.PAPER));
    }
}
