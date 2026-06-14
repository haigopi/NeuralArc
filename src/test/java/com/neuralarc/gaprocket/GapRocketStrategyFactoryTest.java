package com.neuralarc.gaprocket;

import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GapRocketStrategyFactoryTest {
    @Test
    void factoryAddsRecommendationToWorkspaceWithoutSubmittingBrokerOrder() {
        GapRocketRecommendation recommendation = new GapRocketRecommendation(
                "NVDA", "NVIDIA Corporation", new BigDecimal("7.2"), 5_200_000L, new BigDecimal("6.1"),
                new BigDecimal("125.00"), new BigDecimal("116.60"), new BigDecimal("126.10"), new BigDecimal("121.40"),
                GapRocketConfig.CatalystType.EARNINGS, "Earnings beat", 92, GapRocketConfig.EntryStyle.BREAKOUT_RETEST,
                GapRocketConfig.OpeningRangeDuration.FIFTEEN_MINUTES, new BigDecimal("126.10"), new BigDecimal("1"),
                new BigDecimal("123.75"), new BigDecimal("2"), new BigDecimal("128.62"), GapRocketStatus.RECOMMENDED,
                StrategyMode.PAPER, Instant.parse("2026-06-13T13:45:15Z"));

        Strategy strategy = new GapRocketStrategyFactory().toStrategy(recommendation, "workspace-1", true, 30);

        assertEquals("workspace-1", strategy.workspaceId());
        assertEquals("NVDA", strategy.symbol());
        assertEquals(StrategyMode.PAPER, strategy.mode());
        assertEquals(StrategyStatus.CREATED, strategy.status());
        assertEquals(StrategyLifecycleState.CREATED, strategy.currentState());
        assertEquals("GAP_ROCKET_MONITORING", strategy.latestOrderStatus());
        assertEquals("", strategy.latestAlpacaOrderId());
        assertTrue(strategy.lastEvent().contains("No broker order was submitted"));
    }

    @Test
    void sampleScannerProducesCandidatesThatCanPopulateGrid() {
        List<GapRocketRecommendation> recommendations = new GapRocketAnalyzer(null, null)
                .analyze(new GapRocketSampleScanner().candidates(), GapRocketConfig.defaults(StrategyMode.PAPER));
        assertFalse(recommendations.isEmpty());
        assertTrue(recommendations.stream().allMatch(r -> r.strategyScore() >= GapRocketAnalyzer.MINIMUM_RECOMMENDATION_SCORE));
    }
}
