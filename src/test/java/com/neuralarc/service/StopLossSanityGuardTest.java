package com.neuralarc.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StopLossSanityGuardTest {
    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @Test
    void flagsStopSittingAboveBothCurrentPriceAndCostBasis() {
        // The reported MOVE case: avg cost 15.12, stop 15.55, price 12.54.
        assertTrue(StopLossSanityGuard.isMisconfigured(bd("15.55"), bd("12.54"), bd("15.12")));
    }

    @Test
    void aLegitimateStopLossTriggerIsNeverTreatedAsMisconfigured() {
        // This is the critical case: a real stop sits below cost, and price falling to it is a
        // genuine trigger. Correcting here would silently disable stop-loss protection.
        assertFalse(StopLossSanityGuard.isMisconfigured(bd("13.00"), bd("12.90"), bd("15.12")));
        assertFalse(StopLossSanityGuard.isMisconfigured(bd("13.00"), bd("9.00"), bd("15.12")));
    }

    @Test
    void aStopAboveCostButNotYetReachedIsLeftAloneUntilItWouldFire() {
        assertFalse(StopLossSanityGuard.isMisconfigured(bd("15.55"), bd("16.00"), bd("15.12")));
    }

    @Test
    void stopExactlyAtCostAndPriceIsTreatedAsMisconfigured() {
        assertTrue(StopLossSanityGuard.isMisconfigured(bd("15.00"), bd("15.00"), bd("15.00")));
    }

    @Test
    void nonPositiveInputsAreNeverFlagged() {
        assertFalse(StopLossSanityGuard.isMisconfigured(BigDecimal.ZERO, bd("12.54"), bd("15.12")));
        assertFalse(StopLossSanityGuard.isMisconfigured(bd("15.55"), BigDecimal.ZERO, bd("15.12")));
        assertFalse(StopLossSanityGuard.isMisconfigured(bd("15.55"), bd("12.54"), BigDecimal.ZERO));
        assertFalse(StopLossSanityGuard.isMisconfigured(null, bd("12.54"), bd("15.12")));
    }

    @Test
    void correctedStopIsTenPercentBelowCurrentPrice() {
        assertEquals(bd("11.29"), StopLossSanityGuard.correctedStopPrice(bd("12.54")));
        assertEquals(bd("90.00"), StopLossSanityGuard.correctedStopPrice(bd("100.00")));
    }

    @Test
    void correctedStopRestoresAValidRelationshipToCurrentPrice() {
        BigDecimal latestPrice = bd("12.54");
        BigDecimal corrected = StopLossSanityGuard.correctedStopPrice(latestPrice);

        assertTrue(corrected.compareTo(latestPrice) < 0, "corrected stop must sit below the current price");
        assertFalse(StopLossSanityGuard.isMisconfigured(corrected, latestPrice, bd("15.12")),
                "the corrected stop must not immediately be flagged again");
    }
}
