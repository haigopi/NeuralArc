package com.neuralarc.service;

import com.neuralarc.model.MarketBar;
import com.neuralarc.model.MarketMode;
import com.neuralarc.model.RecommendationAction;
import com.neuralarc.model.ShortTermMarketMode;
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
    void shortTermRangeEntryDiscountScenarioUsesBehaviorAdjustedBase() {
        List<MarketBar> bars = shortTermRangeBars();

        StrategyRecommendation recommendation = engine.generateShortTermRecommendation(
                "AAPL", bars, new BigDecimal("209.00"), new BigDecimal("209.00"));

        assertEquals(ShortTermMarketMode.RANGE_ENTRY, recommendation.shortTermMarketMode());
        assertEquals(new BigDecimal("207.00"), recommendation.twoWeekLow());
        assertEquals(new BigDecimal("0.0110"), recommendation.expectedDipPct());
        assertEquals(new BigDecimal("206.70"), recommendation.behaviorAdjustedBasePrice());
        assertEquals(new BigDecimal("206.70"), recommendation.baseBuyPrice());
    }

    @Test
    void shortTermBreakdownScenarioProducesWatchAndDiscountedEntry() {
        List<MarketBar> bars = shortTermRangeBars();

        StrategyRecommendation recommendation = engine.generateShortTermRecommendation(
                "AAPL", bars, new BigDecimal("204.00"), new BigDecimal("209.00"));

        assertEquals(ShortTermMarketMode.BREAKDOWN, recommendation.shortTermMarketMode());
        assertEquals(RecommendationAction.WATCH, recommendation.recommendationAction());
        assertTrue(recommendation.baseBuyPrice().compareTo(new BigDecimal("204.00")) < 0);
    }

    @Test
    void shortTermBreakoutScenarioUsesCurrentPriceWithoutDiscount() {
        List<MarketBar> bars = shortTermBreakoutBars(new BigDecimal("1800.00"));

        StrategyRecommendation recommendation = engine.generateShortTermRecommendation(
                "AAPL", bars, new BigDecimal("218.00"), new BigDecimal("218.00"));

        assertEquals(ShortTermMarketMode.SHORT_TERM_BREAKOUT, recommendation.shortTermMarketMode());
        assertEquals(new BigDecimal("218.00"), recommendation.baseBuyPrice());
    }

    @Test
    void shortTermOverextendedScenarioProducesWatch() {
        List<MarketBar> bars = shortTermBreakoutBars(new BigDecimal("1000.00"));

        StrategyRecommendation recommendation = engine.generateShortTermRecommendation(
                "AAPL", bars, new BigDecimal("218.00"), new BigDecimal("218.00"));

        assertEquals(ShortTermMarketMode.OVEREXTENDED, recommendation.shortTermMarketMode());
        assertEquals(RecommendationAction.WATCH, recommendation.recommendationAction());
        assertEquals(recommendation.behaviorAdjustedBasePrice(), recommendation.baseBuyPrice());
    }

    @Test
    void shortTermWeakAvoidScenarioProducesAvoid() {
        List<MarketBar> bars = shortTermWeakAvoidBars();

        StrategyRecommendation recommendation = engine.generateShortTermRecommendation(
                "AAPL", bars, new BigDecimal("202.00"), new BigDecimal("209.00"));

        assertEquals(ShortTermMarketMode.WEAK_AVOID, recommendation.shortTermMarketMode());
        assertEquals(RecommendationAction.AVOID, recommendation.recommendationAction());
    }

    @Test
    void shortTermNoNegativeGapsStillUsesVolatilityAndIntradayDip() {
        List<MarketBar> bars = shortTermNoNegativeGapBars();

        StrategyRecommendation recommendation = engine.generateShortTermRecommendation(
                "AAPL", bars, new BigDecimal("150.00"), new BigDecimal("150.00"));

        assertEquals(new BigDecimal("0.0000"), recommendation.negativeGapPctAverage());
        assertTrue(recommendation.expectedDipPct().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void shortTermNotEnoughCandlesFallsBackToPointSevenFivePercentDiscount() {
        List<MarketBar> bars = shortTermHistoryBars();

        StrategyRecommendation recommendation = engine.generateShortTermRecommendation(
                "AAPL", bars, new BigDecimal("100.00"), new BigDecimal("100.00"));

        assertEquals(new BigDecimal("0.0075"), recommendation.expectedDipPct());
        assertEquals(new BigDecimal("99.25"), recommendation.behaviorAdjustedBasePrice());
    }

    @Test
    void closingPriceDiscountScenarioUsesBehaviorAdjustedDiscount() {
        List<MarketBar> bars = dipModelBars(new BigDecimal("209.00"));

        StrategyRecommendation recommendation = engine.generateLongTermRecommendation(
                "AAPL", bars, new BigDecimal("209.00"), new BigDecimal("209.00"));

        assertEquals(new BigDecimal("0.0110"), recommendation.expectedDipPct());
        assertEquals(new BigDecimal("206.70"), recommendation.behaviorAdjustedBasePrice());
        assertTrue(recommendation.adjustedBaseBuyPrice().compareTo(new BigDecimal("209.00")) < 0);
    }

    @Test
    void qcomStyleScenarioUsesDiscountedMarketAwareBase() {
        List<MarketBar> bars = accumulationBars();

        StrategyRecommendation recommendation = engine.generateLongTermRecommendation(
                "QCOM", bars, new BigDecimal("156.00"), new BigDecimal("156.00"));

        assertEquals(new BigDecimal("172.61"), recommendation.originalCalculatedBasePrice());
        assertEquals(new BigDecimal("156.00"), recommendation.effectiveMarketPrice());
        assertEquals(new BigDecimal("154.44"), recommendation.behaviorAdjustedBasePrice());
        assertEquals(new BigDecimal("154.44"), recommendation.adjustedBaseBuyPrice());
    }

    @Test
    void breakoutScenarioUsesEffectiveMarketPriceWithoutDiscount() {
        List<MarketBar> bars = breakoutBars(new BigDecimal("190.00"), new BigDecimal("120.00"), new BigDecimal("100.00"));

        StrategyRecommendation recommendation = engine.generateLongTermRecommendation(
                "AAPL", bars, new BigDecimal("190.00"), new BigDecimal("190.00"));

        assertEquals(MarketMode.BREAKOUT, recommendation.marketMode());
        assertEquals(new BigDecimal("190.00"), recommendation.adjustedBaseBuyPrice());
    }

    @Test
    void noNegativeGapsStillUsesVolatilityAndIntradayDip() {
        List<MarketBar> bars = noNegativeGapBars();

        StrategyRecommendation recommendation = engine.generateLongTermRecommendation(
                "AAPL", bars, new BigDecimal("150.00"), new BigDecimal("150.00"));

        assertEquals(new BigDecimal("0.0000"), recommendation.negativeGapPctAverage());
        assertTrue(recommendation.expectedDipPct().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void notEnoughTwoWeekCandlesFallsBackToOnePercentDiscount() {
        List<MarketBar> bars = shortHistoryBars();

        StrategyRecommendation recommendation = engine.generateLongTermRecommendation(
                "AAPL", bars, new BigDecimal("100.00"), new BigDecimal("100.00"));

        assertEquals(new BigDecimal("0.0100"), recommendation.expectedDipPct());
        assertEquals(new BigDecimal("99.00"), recommendation.behaviorAdjustedBasePrice());
    }

    @Test
    void overextendedScenarioProducesWatch() {
        List<MarketBar> bars = breakoutBars(new BigDecimal("190.00"), new BigDecimal("80.00"), new BigDecimal("100.00"));

        StrategyRecommendation recommendation = engine.generateLongTermRecommendation(
                "AAPL", bars, new BigDecimal("190.00"), new BigDecimal("190.00"));

        assertEquals(MarketMode.OVEREXTENDED, recommendation.marketMode());
        assertEquals(RecommendationAction.WATCH, recommendation.recommendationAction());
    }

    @Test
    void weakAvoidScenarioProducesAvoid() {
        List<MarketBar> bars = weakAvoidBars();

        StrategyRecommendation recommendation = engine.generateLongTermRecommendation(
                "AAPL", bars, new BigDecimal("118.00"), new BigDecimal("118.00"));

        assertEquals(MarketMode.WEAK_AVOID, recommendation.marketMode());
        assertEquals(RecommendationAction.AVOID, recommendation.recommendationAction());
    }

    @Test
    void finalSafetyPreventsNonBreakoutBuyAtOrAboveClose() {
        List<MarketBar> bars = accumulationBars();

        StrategyRecommendation recommendation = engine.generateLongTermRecommendation(
                "AAPL", bars, new BigDecimal("156.00"), new BigDecimal("156.00"));

        assertTrue(recommendation.marketMode() != MarketMode.BREAKOUT);
        assertTrue(recommendation.adjustedBaseBuyPrice().compareTo(recommendation.effectiveMarketPrice()) < 0);
    }

    private List<MarketBar> accumulationBars() {
        List<MarketBar> bars = new ArrayList<>();
        LocalDate start = LocalDate.of(2025, 8, 1);
        for (int i = 0; i < 200; i++) {
            BigDecimal close = i < 150 ? new BigDecimal("172.61") : new BigDecimal("172.61");
            BigDecimal open = i == 199 ? new BigDecimal("156.00") : close;
            BigDecimal low = i == 150 ? new BigDecimal("140.00") : close.subtract(new BigDecimal("2.00"));
            if (i >= 186) {
                open = close;
                low = open.multiply(new BigDecimal("0.98"));
            }
            BigDecimal high = close.add(new BigDecimal("2.00"));
            BigDecimal volume = i == 199 ? new BigDecimal("120.00") : new BigDecimal("100.00");
            bars.add(bar(start.plusDays(i), open, high, low, close, volume));
        }
        return bars;
    }

    private List<MarketBar> shortTermRangeBars() {
        List<MarketBar> bars = new ArrayList<>();
        LocalDate start = LocalDate.of(2026, 2, 1);
        for (int i = 0; i < 20; i++) {
            BigDecimal close = new BigDecimal("209.00");
            BigDecimal open = close;
            BigDecimal low = i == 10 ? new BigDecimal("207.00") : close.subtract(BigDecimal.ONE);
            BigDecimal high = i == 18 ? new BigDecimal("216.00") : close.add(new BigDecimal("4.00"));
            if (i >= 6) {
                open = i == 6 ? close : bars.get(i - 1).close();
                low = i == 10 ? new BigDecimal("207.00") : open.multiply(new BigDecimal("0.978"));
            }
            bars.add(bar(start.plusDays(i), open, high, low, close, new BigDecimal("1000.00")));
        }
        return bars;
    }

    private List<MarketBar> shortTermBreakoutBars(BigDecimal lastVolume) {
        List<MarketBar> bars = new ArrayList<>();
        LocalDate start = LocalDate.of(2026, 2, 1);
        for (int i = 0; i < 20; i++) {
            BigDecimal close = i == 19 ? new BigDecimal("218.00") : new BigDecimal("210.00");
            BigDecimal open = close.subtract(BigDecimal.ONE);
            BigDecimal low = close.subtract(new BigDecimal("2.00"));
            BigDecimal high = i < 19 ? new BigDecimal("216.00") : new BigDecimal("220.00");
            BigDecimal volume = i == 19 ? lastVolume : new BigDecimal("1000.00");
            bars.add(bar(start.plusDays(i), open, high, low, close, volume));
        }
        return bars;
    }

    private List<MarketBar> shortTermWeakAvoidBars() {
        List<MarketBar> bars = new ArrayList<>();
        LocalDate start = LocalDate.of(2026, 2, 1);
        for (int i = 0; i < 20; i++) {
            BigDecimal close = i < 15 ? new BigDecimal("215.00") : new BigDecimal("205.00");
            BigDecimal open = close.add(BigDecimal.ONE);
            BigDecimal low = i == 18 ? new BigDecimal("203.00") : close.subtract(BigDecimal.ONE);
            BigDecimal high = close.add(new BigDecimal("2.00"));
            bars.add(bar(start.plusDays(i), open, high, low, close, new BigDecimal("900.00")));
        }
        return bars;
    }

    private List<MarketBar> shortTermNoNegativeGapBars() {
        List<MarketBar> bars = new ArrayList<>();
        LocalDate start = LocalDate.of(2026, 2, 1);
        BigDecimal close = new BigDecimal("150.00");
        for (int i = 0; i < 20; i++) {
            BigDecimal open = close.multiply(new BigDecimal("1.001"));
            BigDecimal low = open.multiply(new BigDecimal("0.997"));
            BigDecimal high = close.add(new BigDecimal("2.00"));
            bars.add(bar(start.plusDays(i), open, high, low, close, new BigDecimal("1000.00")));
        }
        return bars;
    }

    private List<MarketBar> shortTermHistoryBars() {
        List<MarketBar> bars = new ArrayList<>();
        LocalDate start = LocalDate.of(2026, 2, 1);
        for (int i = 0; i < 10; i++) {
            BigDecimal close = new BigDecimal("100.00");
            BigDecimal open = close;
            BigDecimal low = close.subtract(BigDecimal.ONE);
            BigDecimal high = close.add(BigDecimal.ONE);
            bars.add(bar(start.plusDays(i), open, high, low, close, new BigDecimal("1000.00")));
        }
        return bars;
    }

    private List<MarketBar> dipModelBars(BigDecimal lastClose) {
        List<MarketBar> bars = new ArrayList<>();
        LocalDate start = LocalDate.of(2025, 8, 1);
        for (int i = 0; i < 200; i++) {
            BigDecimal close = i < 190 ? new BigDecimal("210.00") : lastClose;
            BigDecimal open = close;
            BigDecimal low = open.multiply(new BigDecimal("0.99"));
            if (i >= 186) {
                open = i == 186 ? close : bars.get(i - 1).close();
                low = open.multiply(new BigDecimal("0.978"));
            }
            BigDecimal high = close.add(new BigDecimal("2.00"));
            BigDecimal volume = new BigDecimal("120.00");
            bars.add(bar(start.plusDays(i), open, high, low, close, volume));
        }
        return bars;
    }

    private List<MarketBar> breakoutBars(BigDecimal effectivePrice, BigDecimal latestVolume, BigDecimal avgVolume) {
        List<MarketBar> bars = new ArrayList<>();
        LocalDate start = LocalDate.of(2025, 8, 1);
        for (int i = 0; i < 200; i++) {
            BigDecimal close = i < 180 ? new BigDecimal("160.00") : new BigDecimal("175.00");
            if (i == 199) {
                close = effectivePrice;
            }
            BigDecimal open = close.subtract(BigDecimal.ONE);
            BigDecimal low = i == 150 ? new BigDecimal("140.00") : close.subtract(new BigDecimal("2.00"));
            BigDecimal high = i >= 180 ? new BigDecimal("180.00") : close.add(new BigDecimal("2.00"));
            BigDecimal volume = i == 199 ? latestVolume : avgVolume;
            bars.add(bar(start.plusDays(i), open, high, low, close, volume));
        }
        return bars;
    }

    private List<MarketBar> noNegativeGapBars() {
        List<MarketBar> bars = new ArrayList<>();
        LocalDate start = LocalDate.of(2025, 8, 1);
        BigDecimal close = new BigDecimal("150.00");
        for (int i = 0; i < 200; i++) {
            BigDecimal open = close.multiply(new BigDecimal("1.001"));
            BigDecimal low = open.multiply(new BigDecimal("0.997"));
            BigDecimal high = close.add(new BigDecimal("2.00"));
            bars.add(bar(start.plusDays(i), open, high, low, close, new BigDecimal("100.00")));
        }
        return bars;
    }

    private List<MarketBar> shortHistoryBars() {
        List<MarketBar> bars = new ArrayList<>();
        LocalDate start = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < 9; i++) {
            BigDecimal close = new BigDecimal("100.00");
            BigDecimal open = close;
            BigDecimal low = close.subtract(BigDecimal.ONE);
            BigDecimal high = close.add(BigDecimal.ONE);
            bars.add(bar(start.plusDays(i), open, high, low, close, new BigDecimal("100.00")));
        }
        return bars;
    }

    private List<MarketBar> weakAvoidBars() {
        List<MarketBar> bars = new ArrayList<>();
        LocalDate start = LocalDate.of(2025, 8, 1);
        for (int i = 0; i < 200; i++) {
            BigDecimal close = i < 150 ? new BigDecimal("130.00") : new BigDecimal("118.00");
            BigDecimal open = close.subtract(BigDecimal.ONE);
            BigDecimal low = i == 150 ? new BigDecimal("110.00") : close.subtract(new BigDecimal("2.00"));
            BigDecimal high = i >= 180 ? new BigDecimal("150.00") : close.add(new BigDecimal("2.00"));
            bars.add(bar(start.plusDays(i), open, high, low, close, new BigDecimal("90.00")));
        }
        return bars;
    }

    private MarketBar bar(LocalDate date, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, BigDecimal volume) {
        return new MarketBar("AAPL", date + "T00:00:00Z", open, high, low, close, volume);
    }
}
