package com.neuralarc.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualSellLossGuardTest {
    @Test
    void aSellBelowCostIsRefusedAndSaysWhatItWouldCost() {
        Optional<String> refusal = ManualSellLossGuard.refusal(
                "ORCL", 200, new BigDecimal("110.00"), new BigDecimal("100.50"));

        assertTrue(refusal.isPresent());
        assertTrue(refusal.get().contains("$1,900.00"), refusal.get());
        assertTrue(refusal.get().contains("200 shares at $100.50"), refusal.get());
        assertTrue(refusal.get().contains("average cost of $110.00"), refusal.get());
    }

    @Test
    void theRefusalPointsAtTheExitsThatStillWork() {
        String refusal = ManualSellLossGuard.refusal(
                "ORCL", 10, new BigDecimal("110.00"), new BigDecimal("100.00")).orElseThrow();

        assertTrue(refusal.contains("stop loss and sell trigger still exit this position"), refusal);
    }

    @Test
    void sellingAtOrAboveCostIsAllowed() {
        assertEquals(Optional.empty(), ManualSellLossGuard.refusal(
                "ORCL", 10, new BigDecimal("110.00"), new BigDecimal("110.00")), "break-even books no loss");
        assertEquals(Optional.empty(), ManualSellLossGuard.refusal(
                "ORCL", 10, new BigDecimal("110.00"), new BigDecimal("125.00")));
    }

    @Test
    void anUnknownPriceOrCostNeverBlocksAnExit() {
        // Refusing on a missing number would strand the operator with no way out and no reason given.
        assertEquals(Optional.empty(), ManualSellLossGuard.refusal(
                "ORCL", 10, new BigDecimal("110.00"), BigDecimal.ZERO));
        assertEquals(Optional.empty(), ManualSellLossGuard.refusal(
                "ORCL", 10, BigDecimal.ZERO, new BigDecimal("100.00")));
        assertEquals(Optional.empty(), ManualSellLossGuard.refusal(
                "ORCL", 10, null, new BigDecimal("100.00")));
        assertEquals(Optional.empty(), ManualSellLossGuard.refusal(
                "ORCL", 0, new BigDecimal("110.00"), new BigDecimal("100.00")));
    }

    @Test
    void aPositionWithoutASymbolStillReadsSensibly() {
        String refusal = ManualSellLossGuard.refusal(
                null, 1, new BigDecimal("10.00"), new BigDecimal("9.00")).orElseThrow();

        assertTrue(refusal.contains("this position"), refusal);
        assertTrue(refusal.contains("1 share at $9.00"), refusal);
    }
}
