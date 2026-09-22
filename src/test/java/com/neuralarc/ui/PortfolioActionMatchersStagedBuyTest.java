package com.neuralarc.ui;

import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyStatus;
import org.junit.jupiter.api.Test;

import static com.neuralarc.ui.AverageDownTestEntries.position;
import static com.neuralarc.ui.AverageDownTestEntries.withState;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioActionMatchersStagedBuyTest {
    @Test
    void anUnfilledEntryBuyIsAJustAddedPositionHoldingNothing() {
        assertTrue(PortfolioActionMatchers.hasUnfilledEntryBuy(
                withState(position("MSFT", 0, "0", "400"), StrategyStatus.ACTIVE, StrategyLifecycleState.BASE_BUY_PLACED)));
        assertFalse(PortfolioActionMatchers.hasUnfilledEntryBuy(
                withState(position("MSFT", 2, "400", "390"), StrategyStatus.ACTIVE, StrategyLifecycleState.BASE_BUY_PLACED)),
                "some shares already filled");
        assertFalse(PortfolioActionMatchers.hasUnfilledEntryBuy(
                withState(position("MSFT", 0, "0", "400"), StrategyStatus.ACTIVE, StrategyLifecycleState.BUY_LIMIT_1_PLACED)));
    }

    @Test
    void aLossLevelBuyIsOneWorkingBelowAHeldPosition() {
        assertTrue(PortfolioActionMatchers.hasPendingLossLevelBuy(
                withState(position("AAPL", 10, "180", "170"), StrategyStatus.ACTIVE, StrategyLifecycleState.BUY_LIMIT_1_PLACED)));
        assertTrue(PortfolioActionMatchers.hasPendingLossLevelBuy(
                withState(position("AAPL", 15, "176", "160"), StrategyStatus.ACTIVE, StrategyLifecycleState.BUY_LIMIT_2_PLACED)));
        assertFalse(PortfolioActionMatchers.hasPendingLossLevelBuy(
                withState(position("AAPL", 0, "0", "170"), StrategyStatus.ACTIVE, StrategyLifecycleState.BASE_BUY_PLACED)),
                "an entry buy is not a loss level");
    }

    @Test
    void theTwoActionsNeverPickTheSameRow() {
        var entry = withState(position("MSFT", 0, "0", "400"), StrategyStatus.ACTIVE, StrategyLifecycleState.BASE_BUY_PLACED);
        var loss = withState(position("AAPL", 10, "180", "170"), StrategyStatus.ACTIVE, StrategyLifecycleState.BUY_LIMIT_1_PLACED);

        assertFalse(PortfolioActionMatchers.hasPendingLossLevelBuy(entry));
        assertFalse(PortfolioActionMatchers.hasUnfilledEntryBuy(loss));
    }
}
