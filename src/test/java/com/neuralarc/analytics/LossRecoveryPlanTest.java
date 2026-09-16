package com.neuralarc.analytics;

import com.neuralarc.model.MarketBar;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LossRecoveryPlanTest {
    @Test
    void addsEnoughSharesForTheBlendedCostToClearTheExitPrice() {
        // Held 100 at 110, market 100, month highs centred on 105.
        LossRecoveryPlan.Plan plan = LossRecoveryPlan.forPosition(
                100, new BigDecimal("110"), new BigDecimal("100"), month("105"), BigDecimal.ZERO);

        assertTrue(plan.feasible(), plan.reason());
        assertEquals(new BigDecimal("105.00"), plan.exitPrice());
        assertEquals(new BigDecimal("99.75"), plan.addLimitPrice(), "the add is priced under the market");
        // (110 - 105) * 100 / (105 - 99.75) = 95.24 -> 96 shares clears the exit price.
        assertEquals(96, plan.addShares());
        assertTrue(plan.newAverageCost().compareTo(plan.exitPrice()) <= 0,
                "blended cost " + plan.newAverageCost() + " must clear the exit " + plan.exitPrice());
        assertTrue(plan.resultAtExit().signum() >= 0, "the plan should end at or above break-even");
        assertEquals(new BigDecimal("-1000.00"), plan.lossNow());
    }

    @Test
    void theExitPriceIsALevelTheStockActuallyReached() {
        LossRecoveryPlan.Plan plan = LossRecoveryPlan.forPosition(
                100, new BigDecimal("110"), new BigDecimal("100"), month("105"), BigDecimal.ZERO);

        assertEquals(21, plan.sessionsAnalyzed());
        assertTrue(plan.sessionsAtOrAboveExit() > 0,
                "the exit must be a price the month actually traded at, reached on "
                        + plan.sessionsAtOrAboveExit() + " sessions");
        assertTrue(plan.exitPrice().compareTo(plan.monthHigh()) <= 0);
    }

    @Test
    void cappedCapitalCutsTheLossInsteadOfPretendingToClearIt() {
        LossRecoveryPlan.Plan plan = LossRecoveryPlan.forPosition(
                100, new BigDecimal("110"), new BigDecimal("100"), month("105"), new BigDecimal("2000"));

        assertTrue(plan.feasible());
        assertEquals(20, plan.addShares(), "2,000 / 99.75 affords 20 shares");
        assertTrue(plan.partialRecovery(), "the loss is reduced, not cleared");
        assertTrue(plan.resultAtExit().compareTo(plan.lossNow()) > 0, "still better than doing nothing");
        assertTrue(plan.warnings().stream().anyMatch(w -> w.contains("cuts the loss rather than clearing it")),
                plan.warnings().toString());
    }

    @Test
    void whenTheExitAlreadyClearsTheCostNoSharesAreAdded() {
        LossRecoveryPlan.Plan plan = LossRecoveryPlan.forPosition(
                100, new BigDecimal("104"), new BigDecimal("100"), month("105"), BigDecimal.ZERO);

        assertTrue(plan.feasible());
        assertEquals(0, plan.addShares());
        assertTrue(plan.warnings().stream().anyMatch(w -> w.contains("No extra shares are needed")));
    }

    @Test
    void aStockThatNeverTradesAboveTheAddPriceGetsNoPlan() {
        // Highs sit below the market: nothing to recover into.
        LossRecoveryPlan.Plan plan = LossRecoveryPlan.forPosition(
                100, new BigDecimal("110"), new BigDecimal("100"), month("98"), BigDecimal.ZERO);

        assertFalse(plan.feasible());
        assertTrue(plan.reason().contains("not traded above"), plan.reason());
    }

    @Test
    void aDisproportionateAddIsFlaggedRatherThanQuietlyRecommended() {
        // Deeply under water: clearing the cost needs many times the shares held.
        LossRecoveryPlan.Plan plan = LossRecoveryPlan.forPosition(
                100, new BigDecimal("160"), new BigDecimal("100"), month("105"), BigDecimal.ZERO);

        assertTrue(plan.feasible());
        assertTrue(plan.addShares() > 300);
        assertTrue(plan.warnings().stream().anyMatch(w -> w.contains("more than three times the shares")),
                plan.warnings().toString());
        assertTrue(plan.warnings().stream().anyMatch(w -> w.contains("consider booking the loss instead")));
    }

    @Test
    void everyPlanThatSpendsMoneySaysWhatItRisks() {
        LossRecoveryPlan.Plan plan = LossRecoveryPlan.forPosition(
                100, new BigDecimal("110"), new BigDecimal("100"), month("105"), BigDecimal.ZERO);

        assertTrue(plan.warnings().stream().anyMatch(w -> w.contains("Averaging down commits")),
                plan.warnings().toString());
    }

    @Test
    void aPositionInProfitOrWithoutHistoryGetsNoPlan() {
        assertFalse(LossRecoveryPlan.forPosition(100, new BigDecimal("90"), new BigDecimal("100"),
                month("105"), BigDecimal.ZERO).feasible());
        assertTrue(LossRecoveryPlan.forPosition(100, new BigDecimal("110"), new BigDecimal("100"),
                List.of(), BigDecimal.ZERO).reason().contains("no daily history"));
        assertTrue(LossRecoveryPlan.forPosition(0, new BigDecimal("110"), new BigDecimal("100"),
                month("105"), BigDecimal.ZERO).reason().contains("no open position"));
    }

    @Test
    void theAddIsPricedAtTodaysLowOnceTheSessionHasTraded() {
        LossRecoveryPlan.Plan plan = LossRecoveryPlan.forPosition(
                100, new BigDecimal("110"), new BigDecimal("100"), month("105"), BigDecimal.ZERO,
                new BigDecimal("98.40"));

        assertEquals(new BigDecimal("98.40"), plan.addLimitPrice(), "today's low is a price the market has paid");
        // (110 - 105) * 100 / (105 - 98.40) = 75.76 -> 76 shares clears the exit price.
        assertEquals(76, plan.addShares());
    }

    @Test
    void todaysLowAboveTheMarketIsStillClampedToTheMarket() {
        // A stale or bad low must never plan a buy above the price on screen.
        LossRecoveryPlan.Plan plan = LossRecoveryPlan.forPosition(
                100, new BigDecimal("110"), new BigDecimal("100"), month("105"), BigDecimal.ZERO,
                new BigDecimal("104.00"));

        assertEquals(new BigDecimal("100.00"), plan.addLimitPrice());
    }

    @Test
    void withoutTodaysBarTheAddFallsBackToADiscountUnderTheMarket() {
        LossRecoveryPlan.Plan plan = LossRecoveryPlan.forPosition(
                100, new BigDecimal("110"), new BigDecimal("100"), month("105"), BigDecimal.ZERO, BigDecimal.ZERO);

        assertEquals(new BigDecimal("99.75"), plan.addLimitPrice());
    }

    /** 21 sessions whose highs centre on {@code medianHigh}, lows a little under the market. */
    private static List<MarketBar> month(String medianHigh) {
        BigDecimal median = new BigDecimal(medianHigh);
        List<MarketBar> bars = new ArrayList<>();
        for (int session = -10; session <= 10; session++) {
            BigDecimal high = median.add(BigDecimal.valueOf(session).multiply(new BigDecimal("0.10")));
            bars.add(new MarketBar("TEST", "2026-09-" + String.format("%02d", session + 11),
                    high.subtract(new BigDecimal("1.00")), high, high.subtract(new BigDecimal("6.00")),
                    high.subtract(new BigDecimal("0.50")), new BigDecimal("1000000")));
        }
        return bars;
    }
}
