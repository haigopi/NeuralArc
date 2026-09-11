package com.neuralarc.ui;

import com.neuralarc.model.Position;
import com.neuralarc.model.TimeInForce;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static com.neuralarc.ui.AverageDownTestEntries.position;
import static com.neuralarc.ui.AverageDownTestEntries.withWorkingSell;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AverageLosingPositionsSelectionTest {
    @Test
    void doubleDownBuysTheSharesAlreadyHeldAndControlledAddBuysTheFixedCount() {
        Position held = position("AAPL", 6, "10.00", "8.00").cachedPosition();

        assertEquals(6, plan(AverageLosingPositionsSelection.QuantityMode.CURRENT_POSITION_QUANTITY, 3).quantityFor(held));
        assertEquals(3, plan(AverageLosingPositionsSelection.QuantityMode.FIXED_INPUT_QUANTITY, 3).quantityFor(held));
    }

    @Test
    void limitPriceSitsThePullbackBelowTheCachedMarketPrice() {
        Position held = position("AAPL", 6, "10.00", "8.00").cachedPosition();

        assertEquals(0, new BigDecimal("7.80").compareTo(limitPlan("2.50").limitPriceFor(held)));
        assertEquals(0, BigDecimal.ZERO.compareTo(limitPlan("2.50").limitPriceFor(new Position("AAPL"))),
                "no market price means no limit price, so the order is skipped rather than guessed");
    }

    @Test
    void estimatesAMarketBuyAtTheLastPriceAndALimitBuyAtItsLimit() {
        Position held = position("AAPL", 6, "10.00", "8.00").cachedPosition();
        AverageLosingPositionsSelection market = new AverageLosingPositionsSelection(
                AverageLosingPositionsSelection.OrderType.MARKET,
                AverageLosingPositionsSelection.QuantityMode.CURRENT_POSITION_QUANTITY, 1, BigDecimal.ZERO);

        assertEquals(0, new BigDecimal("8.00").compareTo(market.estimatedPriceFor(held)));
        assertEquals(0, new BigDecimal("7.80").compareTo(limitPlan("2.50").estimatedPriceFor(held)));
    }

    @Test
    void submitsOnlyTickedPositionsThatAreStillEligible() {
        ManagedStrategy kept = position("KLC", 1, "2.66", "2.58");
        ManagedStrategy untickedByUser = position("CURI", 1, "3.53", "2.66");
        ManagedStrategy lockedSinceOpening = withWorkingSell(position("RKTO", 1, "0.85", "0.69"));
        AverageLosingPositionsSelection selection = new AverageLosingPositionsSelection(
                AverageLosingPositionsSelection.OrderType.LIMIT_BELOW_MARKET,
                AverageLosingPositionsSelection.QuantityMode.CURRENT_POSITION_QUANTITY,
                1, BigDecimal.ONE, TimeInForce.DAY,
                Set.of(kept.strategy.id(), lockedSinceOpening.strategy.id(), "no-such-id"));

        List<ManagedStrategy> targets = selection.selectedFrom(
                List.of(kept, untickedByUser, lockedSinceOpening),
                PortfolioActionsSupport.BulkAction.AVERAGE_LOSING_POSITIONS::matches);

        assertEquals(List.of(kept), targets);
    }

    @Test
    void nothingTickedMeansNothingSubmitted() {
        assertTrue(limitPlan("1").selectedFrom(List.of(position("KLC", 1, "2.66", "2.58")), entry -> true).isEmpty());
    }

    private static AverageLosingPositionsSelection plan(AverageLosingPositionsSelection.QuantityMode mode, int quantity) {
        return new AverageLosingPositionsSelection(
                AverageLosingPositionsSelection.OrderType.LIMIT_BELOW_MARKET, mode, quantity, BigDecimal.ONE);
    }

    private static AverageLosingPositionsSelection limitPlan(String discountPercent) {
        return new AverageLosingPositionsSelection(
                AverageLosingPositionsSelection.OrderType.LIMIT_BELOW_MARKET,
                AverageLosingPositionsSelection.QuantityMode.CURRENT_POSITION_QUANTITY,
                1, new BigDecimal(discountPercent));
    }
}
