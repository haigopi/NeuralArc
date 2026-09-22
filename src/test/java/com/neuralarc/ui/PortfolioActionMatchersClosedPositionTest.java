package com.neuralarc.ui;

import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyStatus;
import org.junit.jupiter.api.Test;

import static com.neuralarc.ui.AverageDownTestEntries.position;
import static com.neuralarc.ui.AverageDownTestEntries.withState;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioActionMatchersClosedPositionTest {
    @Test
    void aFailedRowHoldingNothingIsClosed() {
        // The grid shows these as "Position Closed"; Remove All Closed Positions used to say there were none.
        assertTrue(PortfolioActionMatchers.isClosedPosition(
                withState(position("DTSS", 0, "0.55", "0.92"), StrategyStatus.FAILED, StrategyLifecycleState.FAILED)));
    }

    @Test
    void completedAndStoppedRowsHoldingNothingStayClosed() {
        assertTrue(PortfolioActionMatchers.isClosedPosition(
                withState(position("AAPL", 0, "1", "1"), StrategyStatus.COMPLETED, StrategyLifecycleState.COMPLETED)));
        assertTrue(PortfolioActionMatchers.isClosedPosition(
                withState(position("AAPL", 0, "1", "1"), StrategyStatus.STOPPED, StrategyLifecycleState.STOPPED)));
    }

    @Test
    void anyHeldSharesOrAWorkingOrderStateKeepARowOpen() {
        assertFalse(PortfolioActionMatchers.isClosedPosition(
                withState(position("TSLA", 5, "368.11", "376.21"), StrategyStatus.FAILED, StrategyLifecycleState.FAILED)));
        assertFalse(PortfolioActionMatchers.isClosedPosition(
                withState(position("NIO", 0, "1", "1"), StrategyStatus.ACTIVE, StrategyLifecycleState.BASE_BUY_PLACED)));
        assertFalse(PortfolioActionMatchers.isClosedPosition(
                withState(position("NIO", 0, "1", "1"), StrategyStatus.ARCHIVED, StrategyLifecycleState.STOPPED)));
    }
}
