package com.neuralarc.orb;

import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class OrbStrategyFactoryTest {
    @Test
    void mapsRecommendationToStrategyWithoutSubmittingBrokerOrder() {
        OrbRecommendation recommendation = new OrbRecommendation("AAPL", new BigDecimal("101.00"), new BigDecimal("99.00"),
                new BigDecimal("101.10"), new BigDecimal("99.00"), new BigDecimal("104.13"), 88,
                "ORB long breakout", new BigDecimal("1.00"), new BigDecimal("2.02"), 15,
                OrbStatus.RANGE_CAPTURED, StrategyMode.PAPER, Instant.parse("2026-06-15T14:00:00Z"));

        Strategy strategy = new OrbStrategyFactory().toStrategy(recommendation, "orb-workspace", true, 30);

        assertEquals("AAPL", strategy.symbol());
        assertEquals("orb-workspace", strategy.workspaceId());
        assertEquals(StrategyStatus.CREATED, strategy.status());
        assertEquals("ORB_ARMED", strategy.latestOrderStatus());
        assertEquals(new BigDecimal("101.10"), strategy.baseBuyLimitPrice());
        assertEquals(new BigDecimal("99.00"), strategy.stopLossPrice());
        assertEquals(new BigDecimal("104.13"), strategy.targetSellPrice());
        assertTrue(strategy.lastEvent().contains("No broker order was submitted"));
    }
}
