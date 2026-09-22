package com.neuralarc.ui;

import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyStatus;
import com.neuralarc.model.TimeInForce;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.neuralarc.ui.AverageDownTestEntries.position;
import static com.neuralarc.ui.AverageDownTestEntries.withState;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharesAndTimeInForcePlanTest {
    @Test
    void aGlobalChangeReachesEveryIncludedRowButNotTheExcludedOnes() {
        SharesAndTimeInForcePlan plan = plan();
        plan.rows().get(1).included = false; // "everything except MSFT"

        plan.applyToIncluded(10, TimeInForce.GTC);

        List<SharesAndTimeInForcePlan.Change> changes = plan.changes();
        assertEquals(List.of("AAPL", "NVDA"), changes.stream().map(SharesAndTimeInForcePlan.Change::symbol).toList());
        assertTrue(changes.stream().allMatch(change -> change.quantity() == 10 && change.timeInForce() == TimeInForce.GTC));
    }

    @Test
    void aRowCanBeSetIndividuallyAfterTheGlobalChange() {
        SharesAndTimeInForcePlan plan = plan();
        plan.applyToIncluded(10, null);
        plan.rows().get(2).quantity = 3;

        assertEquals(3, plan.changes().get(2).quantity());
        assertEquals(TimeInForce.DAY, plan.changes().get(0).timeInForce(), "a blank global TIF keeps each row's own");
    }

    @Test
    void rowsLeftAsTheyWereAreNotChanges() {
        SharesAndTimeInForcePlan plan = plan();

        assertTrue(plan.changes().isEmpty());
        plan.applyToIncluded(0, TimeInForce.DAY); // 0 shares = keep; DAY is already DAY
        assertTrue(plan.changes().isEmpty());
    }

    @Test
    void raisingTheSharesRaisesTheStrategysOwnCapsSoTheBuyIsNotRefused() {
        Strategy strategy = position("AAPL", 0, "0", "180").strategy;
        strategy.setBaseBuyLimitPrice(new BigDecimal("180.00"));
        strategy.setBaseBuyQuantity(1);
        strategy.setMaxTotalQuantity(1);
        strategy.setMaxCapitalAllowed(new BigDecimal("180.00"));

        SharesAndTimeInForcePlan.apply(strategy, 5, TimeInForce.GTC);

        assertEquals(5, strategy.baseBuyQuantity());
        assertEquals(5, strategy.maxTotalQuantity());
        assertEquals(0, new BigDecimal("900.00").compareTo(strategy.maxCapitalAllowed()));
        assertEquals(TimeInForce.GTC, strategy.timeInForce());
    }

    @Test
    void onlyEntriesThatHaveNotFilledAreTargets() {
        assertTrue(SharesAndTimeInForceTargets.isTarget(
                withState(position("MSFT", 0, "0", "400"), StrategyStatus.ACTIVE, StrategyLifecycleState.BASE_BUY_PLACED)));
        assertFalse(SharesAndTimeInForceTargets.isTarget(
                withState(position("TSLA", 5, "368.11", "376"), StrategyStatus.ACTIVE, StrategyLifecycleState.STOP_LOSS_ACTIVE)),
                "a held position is not re-entered");
    }

    private static SharesAndTimeInForcePlan plan() {
        return new SharesAndTimeInForcePlan(List.of(
                new SharesAndTimeInForcePlan.Row("a", "AAPL", true, new BigDecimal("180"), 1, TimeInForce.DAY),
                new SharesAndTimeInForcePlan.Row("m", "MSFT", true, new BigDecimal("400"), 1, TimeInForce.DAY),
                new SharesAndTimeInForcePlan.Row("n", "NVDA", false, new BigDecimal("120"), 2, TimeInForce.DAY)));
    }
}
