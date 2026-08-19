package com.neuralarc.profitshield;

import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfitShieldStrategyFactoryTest {

    private static ProfitShieldRecommendation recommendation() {
        return new ProfitShieldRecommendation("MSFT", "MSFT",
                new BigDecimal("100.00"), new BigDecimal("99.50"), new BigDecimal("0.50"),
                2_000_000L, new BigDecimal("1.00"), new BigDecimal("1.20"), new BigDecimal("7.50"),
                new BigDecimal("2.00"), "Quiet 1.20% daily range.", 52, 84,
                new BigDecimal("99.00"), new BigDecimal("3.00"), new BigDecimal("96.03"),
                new BigDecimal("6"), new BigDecimal("104.94"),
                ProfitShieldStatus.RECOMMENDED, StrategyMode.PAPER, Instant.parse("2026-06-15T13:45:15Z"));
    }

    @Test
    void addsRecommendationToWorkspaceWithoutSubmittingBrokerOrder() {
        Strategy strategy = new ProfitShieldStrategyFactory().toStrategy(recommendation(), "workspace-1", false, 30);

        assertEquals("workspace-1", strategy.workspaceId());
        assertEquals("MSFT", strategy.symbol());
        assertEquals(StrategyMode.PAPER, strategy.mode());
        assertEquals(StrategyStatus.CREATED, strategy.status());
        assertEquals(StrategyLifecycleState.CREATED, strategy.currentState());
        assertEquals("PROFIT_SHIELD_RECOMMENDED", strategy.latestOrderStatus());
        assertEquals("", strategy.latestAlpacaOrderId());
        assertTrue(strategy.lastEvent().contains("No broker order was submitted"));
    }

    @Test
    void marksTheRowAsMonitoringWhenExecutionWasRequested() {
        Strategy strategy = new ProfitShieldStrategyFactory().toStrategy(recommendation(), "workspace-1", true, 30);

        assertEquals("PROFIT_SHIELD_MONITORING", strategy.latestOrderStatus());
        assertTrue(strategy.lastEvent().contains("monitoring"));
    }

    @Test
    void carriesThePlannedEntryStopAndTargetIntoTheStrategyRow() {
        Strategy strategy = new ProfitShieldStrategyFactory().toStrategy(recommendation(), "workspace-1", false, 30);

        assertEquals(new BigDecimal("99.00"), strategy.baseBuyLimitPrice());
        assertEquals(new BigDecimal("96.03"), strategy.stopLossPrice());
        assertEquals(new BigDecimal("104.94"), strategy.targetSellPrice());
    }

    @Test
    void keepsPollingSecondsAtLeastOne() {
        Strategy strategy = new ProfitShieldStrategyFactory().toStrategy(recommendation(), "workspace-1", false, 0);

        assertTrue(strategy.pollingIntervalSeconds() >= 1);
    }
}
