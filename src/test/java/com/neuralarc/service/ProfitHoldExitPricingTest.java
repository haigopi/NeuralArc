package com.neuralarc.service;

import com.neuralarc.model.ProfitHoldType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfitHoldExitPricingTest {
    @Test
    void theExitSitsTheTrailAmountUnderThePeak() {
        // The TSLA case: hold of $2, peak 386.67 -> the resting sell belongs at 384.67.
        ProfitHoldExitPricing.Plan plan = ProfitHoldExitPricing.plan(new BigDecimal("386.67"),
                ProfitHoldType.FIXED_AMOUNT_TRAILING, new BigDecimal("2.00"), BigDecimal.ZERO,
                new BigDecimal("368.11"));

        assertEquals(new BigDecimal("384.67"), plan.limitPrice());
        assertFalse(plan.flooredToCost());
        assertTrue(plan.describe(new BigDecimal("386.67")).contains("peak $386.67"));
    }

    @Test
    void aPercentTrailIsTakenOffThePeak() {
        ProfitHoldExitPricing.Plan plan = ProfitHoldExitPricing.plan(new BigDecimal("400.00"),
                ProfitHoldType.PERCENT_TRAILING, BigDecimal.ZERO, new BigDecimal("1.5"), new BigDecimal("300"));

        assertEquals(new BigDecimal("394.00"), plan.limitPrice());
    }

    @Test
    void theExitNeverSitsBelowWhatTheSharesCost() {
        ProfitHoldExitPricing.Plan plan = ProfitHoldExitPricing.plan(new BigDecimal("370.00"),
                ProfitHoldType.FIXED_AMOUNT_TRAILING, new BigDecimal("5.00"), BigDecimal.ZERO,
                new BigDecimal("368.11"));

        assertEquals(new BigDecimal("368.11"), plan.limitPrice(), "a profit hold must not exit at a loss");
        assertTrue(plan.flooredToCost());
        assertEquals(new BigDecimal("365.00"), plan.trailedPrice(), "what the trail alone wanted is still reported");
        assertTrue(plan.describe(new BigDecimal("370.00")).contains("stays in profit"));
    }

    @Test
    void anUnknownPeakYieldsNoOrder() {
        assertEquals(BigDecimal.ZERO, ProfitHoldExitPricing.plan(BigDecimal.ZERO,
                ProfitHoldType.FIXED_AMOUNT_TRAILING, new BigDecimal("2"), BigDecimal.ZERO,
                new BigDecimal("10")).limitPrice());
    }

    @Test
    void theRestingOrderFollowsTheStockUpAndNeverDown() {
        assertTrue(ProfitHoldExitPricing.shouldRaise(new BigDecimal("384.67"), new BigDecimal("385.67")));
        assertTrue(ProfitHoldExitPricing.shouldRaise(null, new BigDecimal("384.67")), "nothing resting yet");
        assertFalse(ProfitHoldExitPricing.shouldRaise(new BigDecimal("384.67"), new BigDecimal("384.00")),
                "the trail must never chase the stock down");
        assertFalse(ProfitHoldExitPricing.shouldRaise(new BigDecimal("384.67"), new BigDecimal("384.675")),
                "half a cent is not worth a cancel and replace");
    }
}
