package com.neuralarc.ui;

import com.neuralarc.model.StrategyLifecycleState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuplicatePositionReconcilerTest {
    @Test
    void flagsTheRowLeftWithNothingWhenOneBrokerPositionIsSharedByTwoRows() {
        List<String> overClaimed = DuplicatePositionReconciler.overClaimedStrategyIds(List.of(
                row("holder", StrategyLifecycleState.BASE_BUY_FILLED, 10, 10, false),
                row("stale", StrategyLifecycleState.BASE_BUY_FILLED, 10, 0, false)
        ));

        assertEquals(List.of("stale"), overClaimed);
    }

    @Test
    void leavesPartialSetsAloneWhileThePositionCoversThem() {
        List<String> overClaimed = DuplicatePositionReconciler.overClaimedStrategyIds(List.of(
                row("first", StrategyLifecycleState.BASE_BUY_FILLED, 10, 10, false),
                row("second", StrategyLifecycleState.BASE_BUY_FILLED, 5, 5, false)
        ));

        assertTrue(overClaimed.isEmpty());
    }

    @Test
    void leavesRowsWithAWorkingBrokerOrderAlone() {
        List<String> overClaimed = DuplicatePositionReconciler.overClaimedStrategyIds(List.of(
                row("holder", StrategyLifecycleState.BASE_BUY_FILLED, 10, 10, false),
                row("waiting", StrategyLifecycleState.SELL_PLACED, 4, 0, true)
        ));

        assertTrue(overClaimed.isEmpty());
    }

    @Test
    void leavesRowsThatAreStillWaitingToBuyAlone() {
        List<String> overClaimed = DuplicatePositionReconciler.overClaimedStrategyIds(List.of(
                row("holder", StrategyLifecycleState.BASE_BUY_FILLED, 10, 10, false),
                row("recommendation", StrategyLifecycleState.CREATED, 0, 0, false)
        ));

        assertTrue(overClaimed.isEmpty());
    }

    @Test
    void flagsARowThatShowsAsHoldingSharesItNeverBought() {
        List<String> overClaimed = DuplicatePositionReconciler.overClaimedStrategyIds(List.of(
                row("holder", StrategyLifecycleState.BASE_BUY_FILLED, 10, 10, false),
                row("phantom", StrategyLifecycleState.BASE_BUY_FILLED, 0, 0, false)
        ));

        assertEquals(List.of("phantom"), overClaimed);
    }

    @Test
    void staysQuietWhenTheBrokerHoldsNothingForTheSymbol() {
        // Nothing allocated anywhere: the existing missing-position rule owns that case, not this one.
        List<String> overClaimed = DuplicatePositionReconciler.overClaimedStrategyIds(List.of(
                row("first", StrategyLifecycleState.BASE_BUY_FILLED, 10, 0, false),
                row("second", StrategyLifecycleState.BASE_BUY_FILLED, 10, 0, false)
        ));

        assertTrue(overClaimed.isEmpty());
    }

    @Test
    void staysQuietForASingleRow() {
        List<String> overClaimed = DuplicatePositionReconciler.overClaimedStrategyIds(List.of(
                row("only", StrategyLifecycleState.BASE_BUY_FILLED, 10, 0, false)
        ));

        assertTrue(overClaimed.isEmpty());
    }

    private static DuplicatePositionReconciler.Row row(
            String id,
            StrategyLifecycleState state,
            int localClaim,
            int allocated,
            boolean pendingBrokerOrder
    ) {
        return new DuplicatePositionReconciler.Row(id, state, localClaim, allocated, pendingBrokerOrder);
    }
}
