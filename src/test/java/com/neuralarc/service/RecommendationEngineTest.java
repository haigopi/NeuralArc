package com.neuralarc.service;

import com.neuralarc.model.MarketBar;
import com.neuralarc.model.RecommendationAction;
import com.neuralarc.model.StrategyRecommendation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendationEngineTest {
    private final RecommendationEngine engine = new RecommendationEngine(new TechnicalIndicatorService());

    @Test
    void shortTermCalculationUsesTwoWeekLowWhenCurrentPriceIsAboveSupport() {
        List<MarketBar> bars = shortTermBars();

        StrategyRecommendation recommendation = engine.generateShortTermRecommendation("AAPL", bars, new BigDecimal("100.00"));

        assertTrue(recommendation.isApplicable());
        assertFalse(recommendation.breakdownMode());
        assertEquals(new BigDecimal("98.00"), recommendation.baseBuyPrice());
        assertTrue(recommendation.buy1Price().compareTo(recommendation.baseBuyPrice()) < 0);
        assertTrue(recommendation.buy2Price().compareTo(recommendation.buy1Price()) < 0);
        assertTrue(recommendation.stopLossPrice().compareTo(recommendation.buy2Price()) < 0);
        assertTrue(recommendation.sellPrice().compareTo(new BigDecimal("106.00")) >= 0);
        assertTrue(recommendation.sellPrice().compareTo(recommendation.target1Price()) >= 0);
    }

    @Test
    void shortTermCalculationEntersBreakdownModeBelowTwoWeekLow() {
        List<MarketBar> bars = shortTermBars();

        StrategyRecommendation recommendation = engine.generateShortTermRecommendation("AAPL", bars, new BigDecimal("94.00"));

        assertTrue(recommendation.breakdownMode());
        assertEquals(new BigDecimal("90.00"), recommendation.baseBuyPrice());
        assertTrue(recommendation.warningMessage().contains("Breakdown mode"));
    }

    @Test
    void longTermSupportBuyModeUsesSixMonthLow() {
        List<MarketBar> bars = longTermBars(new BigDecimal("99.00"), false);

        StrategyRecommendation recommendation = engine.generateLongTermRecommendation("AAPL", bars, new BigDecimal("102.00"));

        assertTrue(recommendation.isApplicable());
        assertEquals(new BigDecimal("100.00"), recommendation.baseBuyPrice());
        assertFalse(recommendation.warningMessage().contains("middle of the six-month range"));
    }

    @Test
    void longTermBreakoutModeUsesCurrentPrice() {
        List<MarketBar> bars = longTermBars(new BigDecimal("99.00"), false);

        StrategyRecommendation recommendation = engine.generateLongTermRecommendation("AAPL", bars, new BigDecimal("300.00"));

        assertEquals(new BigDecimal("300.00"), recommendation.baseBuyPrice());
        assertFalse(recommendation.warningMessage().contains("middle of the six-month range"));
    }

    @Test
    void longTermMiddleRangeDefaultsToWatchBias() {
        List<MarketBar> bars = longTermBars(new BigDecimal("80.00"), true);

        StrategyRecommendation recommendation = engine.generateLongTermRecommendation("AAPL", bars, new BigDecimal("130.00"));

        assertEquals(RecommendationAction.WATCH, recommendation.recommendationAction());
        assertTrue(recommendation.warningMessage().contains("middle of the six-month range"));
    }

    @Test
    void riskRewardCalculationIsReflectedInOutput() {
        List<MarketBar> bars = shortTermBars();

        StrategyRecommendation recommendation = engine.generateShortTermRecommendation("AAPL", bars, new BigDecimal("100.00"));

        assertEquals(new BigDecimal("0.50"), recommendation.riskRewardRatio());
    }

    @Test
    void confidenceScoreReflectsInputs() {
        List<MarketBar> bars = shortTermBars();

        StrategyRecommendation recommendation = engine.generateShortTermRecommendation("AAPL", bars, new BigDecimal("100.00"));

        assertEquals(55, recommendation.confidenceScore());
    }

    private List<MarketBar> shortTermBars() {
        List<MarketBar> bars = new ArrayList<>();
        LocalDate start = LocalDate.of(2026, 4, 1);
        for (int i = 0; i < 20; i++) {
            BigDecimal low = new BigDecimal(i < 10 ? "101.00" : "98.00");
            BigDecimal high = new BigDecimal(i < 10 ? "103.00" : "106.00");
            BigDecimal close = new BigDecimal(i < 19 ? "101.00" : "102.00");
            BigDecimal volume = new BigDecimal(i < 19 ? "1000" : "2000");
            bars.add(bar(start.plusDays(i), close.subtract(BigDecimal.ONE), high, low, close, volume));
        }
        return bars;
    }

    private List<MarketBar> longTermBars(BigDecimal latestVolume, boolean neutralTrend) {
        List<MarketBar> bars = new ArrayList<>();
        LocalDate start = LocalDate.of(2025, 8, 1);
        for (int i = 0; i < 200; i++) {
            BigDecimal close;
            if (neutralTrend) {
                close = i < 150 ? new BigDecimal("120.00") : new BigDecimal("125.00");
            } else {
                close = BigDecimal.valueOf(90 + (i / 2.0));
            }
            BigDecimal low = i == 150 ? new BigDecimal("100.00") : close.subtract(new BigDecimal("3.00"));
            BigDecimal high = i == 180 ? new BigDecimal("160.00") : close.add(new BigDecimal("3.00"));
            BigDecimal volume = i == 199 ? latestVolume : new BigDecimal("90.00");
            bars.add(bar(start.plusDays(i), close.subtract(BigDecimal.ONE), high, low, close, volume));
        }
        return bars;
    }

    private MarketBar bar(LocalDate date, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, BigDecimal volume) {
        return new MarketBar("AAPL", date + "T00:00:00Z", open, high, low, close, volume);
    }
}
