package com.neuralarc.gaprocket;

import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GapRocketAnalyzerTest {
    @Test
    void defaultsMatchDialogRequirements() {
        GapRocketConfig cfg = GapRocketConfig.defaults(StrategyMode.LIVE);
        assertEquals(BigDecimal.ZERO, cfg.minimumPremarketGapPercent());
        assertEquals(0L, cfg.minimumPremarketVolume());
        assertEquals(new BigDecimal("0.5"), cfg.minimumStockPrice());
        assertEquals(new BigDecimal("0.5"), cfg.minimumRelativeVolume());
        assertNull(cfg.maximumStockPrice());
        assertFalse(cfg.newsCatalystRequired());
        assertEquals(GapRocketConfig.MarketTrendFilter.DISABLED, cfg.marketTrendFilter());
        assertEquals(GapRocketConfig.EntryStyle.BREAKOUT_RETEST, cfg.entryStyle());
        assertEquals(GapRocketConfig.OpeningRangeDuration.FIFTEEN_MINUTES, cfg.openingRangeDuration());
        assertEquals(StrategyMode.LIVE, cfg.mode());
    }

    @Test
    void analyzeAddsOnlyRecommendationsAtOrAboveThresholdSortedByScore() {
        List<String> log = new ArrayList<>();
        GapRocketAnalyzer analyzer = new GapRocketAnalyzer(Clock.fixed(Instant.parse("2026-06-13T13:45:15Z"), ZoneOffset.UTC), log::add);
        List<GapRocketRecommendation> result = analyzer.analyze(List.of(strong("NVDA"), weak("ABC")), strictConfig());
        assertEquals(1, result.size());
        assertEquals("NVDA", result.getFirst().symbol());
        assertTrue(result.getFirst().strategyScore() >= GapRocketAnalyzer.MINIMUM_RECOMMENDATION_SCORE);
        assertEquals(StrategyMode.PAPER, result.getFirst().mode());
        assertTrue(log.stream().anyMatch(line -> line.contains("Rejected ABC")));
    }

    @Test
    void defaultsDoNotRequirePremarketGapVolumeTrendOrNews() {
        GapRocketAnalyzer analyzer = new GapRocketAnalyzer(Clock.fixed(Instant.parse("2026-06-13T13:45:15Z"), ZoneOffset.UTC), null);
        GapRocketCandidate candidate = new GapRocketCandidate("NVDA", "NVIDIA Corporation", BigDecimal.ZERO,
                0L, new BigDecimal("5"), new BigDecimal("205"), new BigDecimal("204.50"),
                new BigDecimal("205"), new BigDecimal("203"), null, "",
                false, false, new BigDecimal("0.4"), false, new BigDecimal("205"));

        assertTrue(analyzer.passesFilters(candidate, GapRocketConfig.defaults(StrategyMode.PAPER)));
    }

    @Test
    void paperAndLiveRecommendationsAreIsolatedByConfigMode() {
        GapRocketAnalyzer analyzer = new GapRocketAnalyzer(Clock.systemUTC(), null);
        assertEquals(StrategyMode.PAPER, analyzer.analyze(List.of(strong("TSLA")), GapRocketConfig.defaults(StrategyMode.PAPER)).getFirst().mode());
        assertEquals(StrategyMode.LIVE, analyzer.analyze(List.of(strong("TSLA")), GapRocketConfig.defaults(StrategyMode.LIVE)).getFirst().mode());
    }

    @Test
    void plannedEntryDoesNotFallBelowCurrentPriceWhenPremarketHighIsStale() {
        GapRocketAnalyzer analyzer = new GapRocketAnalyzer(Clock.fixed(Instant.parse("2026-06-13T13:45:15Z"), ZoneOffset.UTC), null);
        GapRocketCandidate staleHigh = new GapRocketCandidate("NVDA", "NVIDIA Corporation", new BigDecimal("7.2"),
                5_200_000L, new BigDecimal("6.1"), new BigDecimal("205"), new BigDecimal("188.80"),
                new BigDecimal("126"), new BigDecimal("124"), GapRocketConfig.CatalystType.EARNINGS,
                "Earnings beat", true, true, new BigDecimal("0.35"), true, new BigDecimal("200.80"));

        GapRocketRecommendation recommendation = analyzer.analyze(List.of(staleHigh), GapRocketConfig.defaults(StrategyMode.PAPER)).getFirst();

        assertEquals(new BigDecimal("205.00"), recommendation.plannedEntryPrice());
        assertEquals(new BigDecimal("194.75"), recommendation.stopLossPrice());
        assertEquals(new BigDecimal("225.50"), recommendation.takeProfitPrice());
    }

    private GapRocketCandidate strong(String symbol) {
        return new GapRocketCandidate(symbol, symbol + " Inc", new BigDecimal("12"), 5_000_000L, new BigDecimal("6"),
                new BigDecimal("125"), new BigDecimal("110"), new BigDecimal("126"), new BigDecimal("118"),
                GapRocketConfig.CatalystType.EARNINGS, "Strong earnings catalyst", true, false, new BigDecimal("0.4"), true, new BigDecimal("123"));
    }

    private GapRocketCandidate weak(String symbol) {
        return new GapRocketCandidate(symbol, symbol + " Inc", new BigDecimal("5"), 1_000_000L, new BigDecimal("2"),
                new BigDecimal("5"), new BigDecimal("4.8"), new BigDecimal("5.1"), new BigDecimal("4.9"),
                GapRocketConfig.CatalystType.GENERAL_BREAKING_NEWS, "Minor headline", true, false, new BigDecimal("2"), false, new BigDecimal("5"));
    }

    private GapRocketConfig strictConfig() {
        return new GapRocketConfig(new BigDecimal("2"), 500_000L, new BigDecimal("5"), new BigDecimal("1.5"),
                null, true, null, GapRocketConfig.MarketTrendFilter.EITHER_SPY_OR_QQQ_GREEN,
                GapRocketConfig.EntryStyle.BREAKOUT_RETEST, GapRocketConfig.OpeningRangeDuration.FIFTEEN_MINUTES,
                new BigDecimal("5"), new BigDecimal("10"), 10, GapRocketConfig.ExecutionFrequency.MANUAL,
                StrategyMode.PAPER);
    }
}
