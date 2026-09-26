package com.neuralarc.ui;

import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyStatus;
import org.junit.jupiter.api.Test;

import static com.neuralarc.ui.AverageDownTestEntries.position;
import static com.neuralarc.ui.AverageDownTestEntries.withState;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioActionMatchersStopLossTest {
    @Test
    void anArmedStopLossCanBeCanceled() {
        ManagedStrategy entry = withState(position("AAPL", 10, "180", "170"),
                StrategyStatus.ACTIVE, StrategyLifecycleState.BASE_BUY_FILLED);
        entry.strategy.setAutomatedStopLossEnabled(true);

        assertTrue(PortfolioActionMatchers.hasCancelableStopLoss(entry));
    }

    @Test
    void soCanOneAlreadyMonitoringEvenIfTheFlagWasCleared() {
        ManagedStrategy entry = withState(position("AAPL", 10, "180", "170"),
                StrategyStatus.ACTIVE, StrategyLifecycleState.STOP_LOSS_ACTIVE);
        entry.strategy.setAutomatedStopLossEnabled(false);

        assertTrue(PortfolioActionMatchers.hasCancelableStopLoss(entry),
                "a stop loss that is actively monitoring still has something to cancel");
    }

    @Test
    void aPausedStrategyStillCountsBecauseItsStopLossComesBackOnResume() {
        ManagedStrategy entry = withState(position("AAPL", 10, "180", "170"),
                StrategyStatus.PAUSED, StrategyLifecycleState.BASE_BUY_FILLED);
        entry.strategy.setAutomatedStopLossEnabled(true);

        assertTrue(PortfolioActionMatchers.hasCancelableStopLoss(entry));
    }

    @Test
    void strategiesWithNothingToCancelAreLeftAlone() {
        ManagedStrategy noStop = withState(position("AAPL", 10, "180", "170"),
                StrategyStatus.ACTIVE, StrategyLifecycleState.BASE_BUY_FILLED);
        noStop.strategy.setAutomatedStopLossEnabled(false);
        assertFalse(PortfolioActionMatchers.hasCancelableStopLoss(noStop));

        ManagedStrategy archived = withState(position("AAPL", 0, "0", "170"),
                StrategyStatus.ARCHIVED, StrategyLifecycleState.COMPLETED);
        archived.strategy.setAutomatedStopLossEnabled(true);
        assertFalse(PortfolioActionMatchers.hasCancelableStopLoss(archived),
                "an archived row's stop loss can never fire again");

        assertFalse(PortfolioActionMatchers.hasCancelableStopLoss(null));
    }

    @Test
    void theActionDescribesWhatThePositionsAreLeftWithout() {
        PortfolioActionsSupport.BulkAction action = PortfolioActionsSupport.BulkAction.CANCEL_STOP_LOSSES;

        assertTrue(action.confirmHeading(3).contains("3"));
        assertTrue(action.confirmDetail().contains("no automatic downside protection"));
        assertTrue(action.emptyMessage().toLowerCase().contains("no strategy"));
    }
}
