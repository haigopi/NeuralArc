package com.neuralarc.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyEntrySourceTest {
    @Test
    void aStrategyTheOperatorAddedByHandIsManual() {
        assertTrue(StrategyEntrySource.isManuallyAdded(strategy("NVDA Strategy", "", "")));
    }

    @Test
    void scannerAndEngineStrategiesAreNotManual() {
        assertFalse(StrategyEntrySource.isManuallyAdded(strategy("NVDA Strategy", "ORB_RECOMMENDED", "")));
        assertFalse(StrategyEntrySource.isManuallyAdded(strategy("orb_engine NVDA", "", "")));
        assertFalse(StrategyEntrySource.isManuallyAdded(strategy("history_reentry NVDA", "", "")));
        assertFalse(StrategyEntrySource.isManuallyAdded(strategy("MANUAL_ADDITION:NVDA", "PAPER_PENDING", "")));
    }

    @Test
    void smartPicksBrokerSyncAndPromotionAreNotManual() {
        assertFalse(StrategyEntrySource.isManuallyAdded(strategy("NVDA Strategy", "", "Placed from Smart Picks")));
        assertFalse(StrategyEntrySource.isManuallyAdded(strategy("NVDA Strategy", "", "Synced from Alpaca")));
        assertFalse(StrategyEntrySource.isManuallyAdded(strategy("NVDA Strategy", "", "Promoted live from paper")));
    }

    @Test
    void nothingIsManualWhenThereIsNoStrategy() {
        assertFalse(StrategyEntrySource.isManuallyAdded(null));
    }

    private static Strategy strategy(String name, String latestOrderStatus, String lastEvent) {
        Strategy strategy = new Strategy(
                "id", name, "NVDA", StrategyMode.PAPER, StrategyStatus.ACTIVE, StrategyLifecycleState.CREATED,
                new BigDecimal("184.00"), 2, BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, false,
                StopLossType.FIXED_PRICE, BigDecimal.ZERO, BigDecimal.ZERO, false, BigDecimal.ZERO,
                false, BigDecimal.ZERO, BigDecimal.ZERO, false, false, ProfitHoldType.PERCENT_TRAILING,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false, 20, new BigDecimal("1000.00"), 2,
                Instant.now(), Instant.now());
        strategy.setLatestOrderStatus(latestOrderStatus);
        strategy.setLastEvent(lastEvent);
        return strategy;
    }
}
