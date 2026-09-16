package com.neuralarc.analytics;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlannedEntryPriceTest {
    private static final BigDecimal DISCOUNT = PlannedEntryPrice.DEFAULT_DISCOUNT_PERCENT;

    @Test
    void aimsALittleUnderTheMarket() {
        assertEquals(new BigDecimal("99.75"),
                PlannedEntryPrice.atOrBelowMarket(new BigDecimal("100.00"), BigDecimal.ZERO, DISCOUNT));
    }

    @Test
    void roundingNeverCrossesTheMarket() {
        // The reported bug: 215.695 rounded half-up to a 215.70 limit, a cent above the market.
        BigDecimal current = new BigDecimal("215.695");

        BigDecimal planned = PlannedEntryPrice.atOrBelowMarket(current, BigDecimal.ZERO, BigDecimal.ZERO);

        assertEquals(new BigDecimal("215.69"), planned);
        assertTrue(planned.compareTo(current) <= 0, planned + " must not sit above " + current);
    }

    @Test
    void theWeeksLowIsTheFloorSoTheOrderCanStillFill() {
        // A 0.25% discount would plan 99.75, but the stock has not traded below 99.90 all week.
        assertEquals(new BigDecimal("99.90"),
                PlannedEntryPrice.atOrBelowMarket(new BigDecimal("100.00"), new BigDecimal("99.90"), DISCOUNT));
        // A week's low far below leaves the discount as the plan.
        assertEquals(new BigDecimal("99.75"),
                PlannedEntryPrice.atOrBelowMarket(new BigDecimal("100.00"), new BigDecimal("80.00"), DISCOUNT));
    }

    @Test
    void aWeeksLowAtOrAboveTheMarketNeverLiftsThePlan() {
        assertEquals(new BigDecimal("99.75"),
                PlannedEntryPrice.atOrBelowMarket(new BigDecimal("100.00"), new BigDecimal("104.00"), DISCOUNT));
    }

    @Test
    void withoutAPriceThereIsNoPlan() {
        assertEquals(new BigDecimal("0.00"), PlannedEntryPrice.atOrBelowMarket(null, BigDecimal.TEN, DISCOUNT));
        assertEquals(new BigDecimal("0.00"), PlannedEntryPrice.atOrBelowMarket(BigDecimal.ZERO, BigDecimal.TEN, DISCOUNT));
    }

    @Test
    void anAbsurdDiscountIsCappedRatherThanPlanningNearZero() {
        BigDecimal planned = PlannedEntryPrice.atOrBelowMarket(new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("900"));

        assertEquals(new BigDecimal("50.00"), planned);
    }

    @Test
    void theLastGuardClampsAStoredPlanToTheMarket() {
        assertEquals(new BigDecimal("215.69"),
                PlannedEntryPrice.notAboveMarket(new BigDecimal("215.70"), new BigDecimal("215.69")));
        assertEquals(new BigDecimal("99.75"),
                PlannedEntryPrice.notAboveMarket(new BigDecimal("99.75"), new BigDecimal("100.00")),
                "a plan already under the market is left alone");
        assertEquals(new BigDecimal("12.34"),
                PlannedEntryPrice.notAboveMarket(new BigDecimal("12.345"), BigDecimal.ZERO),
                "with no market price the plan is still rounded down");
    }
}
