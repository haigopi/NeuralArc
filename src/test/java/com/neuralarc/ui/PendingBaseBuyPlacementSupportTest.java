package com.neuralarc.ui;

import com.neuralarc.model.ProfitHoldType;
import com.neuralarc.model.StopLossType;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingBaseBuyPlacementSupportTest {
    @Test
    void lowersBaseBuyToTenPercentBelowTodayLowWhenLimitIsAboveLow() {
        BigDecimal adjusted = PendingBaseBuyPlacementSupport.adjustedBaseBuyLimit(
                new BigDecimal("110.00"),
                new BigDecimal("100.00")
        );

        assertEquals(new BigDecimal("90.00"), adjusted);
    }

    @Test
    void leavesBaseBuyUnchangedWhenLimitIsAtOrBelowTodayLow() {
        BigDecimal adjusted = PendingBaseBuyPlacementSupport.adjustedBaseBuyLimit(
                new BigDecimal("95.12"),
                new BigDecimal("100.00")
        );

        assertEquals(new BigDecimal("95.12"), adjusted);
    }

    @Test
    void detectsScannerPendingPlacementStatuses() {
        Strategy pending = strategy("AAPL");
        pending.setLatestOrderStatus("VWAP_RECOMMENDED");
        Strategy activeOrder = strategy("MSFT");
        activeOrder.setLatestOrderStatus("new");

        assertTrue(PendingBaseBuyPlacementSupport.isPendingBaseBuyPlacement(pending));
        assertFalse(PendingBaseBuyPlacementSupport.isPendingBaseBuyPlacement(activeOrder));
    }

    private static Strategy strategy(String symbol) {
        return new Strategy(
                UUID.randomUUID().toString(),
                symbol + " Strategy",
                symbol,
                StrategyMode.PAPER,
                StrategyStatus.ACTIVE,
                StrategyLifecycleState.CREATED,
                new BigDecimal("100.00"),
                1,
                new BigDecimal("90.00"),
                1,
                new BigDecimal("80.00"),
                1,
                true,
                StopLossType.FIXED_PRICE,
                new BigDecimal("75.00"),
                new BigDecimal("1.00"),
                false,
                BigDecimal.ZERO,
                true,
                new BigDecimal("110.00"),
                new BigDecimal("1000.00"),
                false,
                false,
                ProfitHoldType.PERCENT_TRAILING,
                new BigDecimal("1.00"),
                new BigDecimal("1.00"),
                BigDecimal.ZERO,
                false,
                10,
                new BigDecimal("1000.00"),
                10,
                Instant.now(),
                Instant.now()
        );
    }
}
