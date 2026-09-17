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
import java.util.List;
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
        pending.setLatestOrderStatus("EARNINGS_HUNTER_RECOMMENDED");
        Strategy activeOrder = strategy("MSFT");
        activeOrder.setLatestOrderStatus("new");

        assertTrue(PendingBaseBuyPlacementSupport.isPendingBaseBuyPlacement(pending));
        assertFalse(PendingBaseBuyPlacementSupport.isPendingBaseBuyPlacement(activeOrder));
    }

    @Test
    void treatsProfitShieldRecommendationsAsPendingBaseBuys() {
        Strategy recommended = strategy("KO");
        recommended.setLatestOrderStatus("PROFIT_SHIELD_RECOMMENDED");
        Strategy monitoring = strategy("PEP");
        monitoring.setLatestOrderStatus("PROFIT_SHIELD_MONITORING");

        assertTrue(PendingBaseBuyPlacementSupport.isPendingBaseBuyPlacement(recommended));
        assertTrue(PendingBaseBuyPlacementSupport.isPendingBaseBuyPlacement(monitoring),
                "a monitoring row reads as armed but never submitted an order, so its base buy is still pending");
    }

    @Test
    void everyScannerMonitoringStatusIsStillWaitingToPlaceItsBaseBuy() {
        // The status a scanner writes when execution was requested. It marks the row armed without
        // submitting anything, so these rows are exactly the ones the bulk placement should pick up.
        for (String status : List.of(
                "GAP_ROCKET_MONITORING",
                "DIP_HUNTER_MONITORING",
                "VWAP_MONITORING",
                "SWING_MONITORING",
                "RANGE_RIDER_MONITORING",
                "EARNINGS_HUNTER_MONITORING",
                "PROFIT_SHIELD_MONITORING")) {
            Strategy monitoring = strategy("AAPL");
            monitoring.setLatestOrderStatus(status);

            assertTrue(PendingBaseBuyPlacementSupport.isPendingBaseBuyPlacement(monitoring), status);
        }
    }

    @Test
    void anArmedOpeningRangeRowIsAlsoWaitingToPlace() {
        Strategy armed = strategy("NVDA");
        armed.setLatestOrderStatus("ORB_ARMED");

        assertTrue(PendingBaseBuyPlacementSupport.isPendingBaseBuyPlacement(armed));
    }

    @Test
    void aStatusThatAlreadyReachedTheBrokerIsNeverPending() {
        // The guard on widening the gate: a row whose order really is at the broker must never be
        // resubmitted, however it got there.
        for (String status : List.of("accepted", "new", "filled", "expired", "invalid", "partially_filled", "")) {
            Strategy live = strategy("MSFT");
            live.setLatestOrderStatus(status);

            assertFalse(PendingBaseBuyPlacementSupport.isPendingBaseBuyPlacement(live), status);
        }
    }

    @Test
    void anUnknownScannerStateIsNotTreatedAsPending() {
        // Exact matching, not prefixes: a future terminal state must not become submittable by name.
        Strategy canceled = strategy("KO");
        canceled.setLatestOrderStatus("DIP_HUNTER_CANCELED");

        assertFalse(PendingBaseBuyPlacementSupport.isPendingBaseBuyPlacement(canceled));
    }

    @Test
    void treatsManualImportedPendingRowsAsPendingBaseBuys() {
        Strategy imported = strategy("MDB");
        imported.setName("MANUAL_ADDITION: MDB Paper");
        imported.setLatestOrderStatus("PAPER_PENDING");

        assertTrue(PendingBaseBuyPlacementSupport.isPendingBaseBuyPlacement(imported));
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
