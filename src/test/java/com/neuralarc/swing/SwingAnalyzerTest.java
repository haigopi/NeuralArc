package com.neuralarc.swing;

import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SwingAnalyzerTest {
    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-06-15T18:00:00Z"), ZoneOffset.UTC);

    @Test
    void defaultsMatchDialogRequirements() {
        SwingConfig cfg = SwingConfig.defaults(StrategyMode.LIVE);
        assertEquals(new BigDecimal("3"), cfg.minimumPullbackPercent());
        assertEquals(new BigDecimal("15"), cfg.maximumPullbackPercent());
        assertEquals(500_000L, cfg.minimumAverageVolume());
        assertEquals(new BigDecimal("5"), cfg.minimumStockPrice());
        assertEquals(new BigDecimal("0.8"), cfg.minimumRelativeVolume());
        assertNull(cfg.maximumStockPrice());
        assertEquals(SwingConfig.TrendFilter.ABOVE_MA_50_AND_200, cfg.trendFilter());
        assertEquals(new BigDecimal("6"), cfg.stopLossPercent());
        assertEquals(new BigDecimal("12"), cfg.targetProfitPercent());
        assertEquals(StrategyMode.LIVE, cfg.mode());
    }

    @Test
    void rejectedValuesResetToDefaults() {
        SwingConfig cfg = new SwingConfig(new BigDecimal("-1"), new BigDecimal("0"), -5L,
                new BigDecimal("0"), new BigDecimal("-2"), null, null, new BigDecimal("-3"),
                new BigDecimal("-7"), -4, null, StrategyMode.PAPER);
        assertEquals(new BigDecimal("3"), cfg.minimumPullbackPercent());
        assertEquals(new BigDecimal("15"), cfg.maximumPullbackPercent());
        assertEquals(500_000L, cfg.minimumAverageVolume());
        assertEquals(new BigDecimal("5"), cfg.minimumStockPrice());
        assertEquals(new BigDecimal("0.8"), cfg.minimumRelativeVolume());
        assertEquals(new BigDecimal("6"), cfg.stopLossPercent());
        assertEquals(new BigDecimal("12"), cfg.targetProfitPercent());
        assertEquals(10, cfg.maxStocksToAdd());
    }

    @Test
    void analyzeAddsOnlyRecommendationsAtOrAboveThresholdSortedByScore() {
        List<String> log = new ArrayList<>();
        SwingAnalyzer analyzer = new SwingAnalyzer(FIXED, log::add);
        List<SwingRecommendation> result = analyzer.analyze(List.of(strong("NVDA"), weak("ABC")),
                SwingConfig.defaults(StrategyMode.PAPER));
        assertEquals(1, result.size());
        assertEquals("NVDA", result.getFirst().symbol());
        assertTrue(result.getFirst().strategyScore() >= SwingAnalyzer.MINIMUM_RECOMMENDATION_SCORE);
        assertEquals(StrategyMode.PAPER, result.getFirst().mode());
        assertTrue(log.stream().anyMatch(line -> line.contains("Rejected ABC")));
    }

    @Test
    void rejectsTooShallowAndTooDeepPullbacks() {
        List<String> log = new ArrayList<>();
        SwingAnalyzer analyzer = new SwingAnalyzer(FIXED, log::add);
        SwingConfig cfg = SwingConfig.defaults(StrategyMode.PAPER);

        // 1% off the recent high — below the 3% minimum.
        SwingCandidate shallow = candidate("SHAL", new BigDecimal("1"), new BigDecimal("1.5"), true, true);
        // 20% off the recent high — above the 15% maximum (trend likely broken).
        SwingCandidate deep = candidate("DEEP", new BigDecimal("20"), new BigDecimal("1.5"), true, true);

        assertTrue(analyzer.analyze(List.of(shallow, deep), cfg).isEmpty());
        assertTrue(log.stream().anyMatch(l -> l.contains("SHAL") && l.contains("too shallow")));
        assertTrue(log.stream().anyMatch(l -> l.contains("DEEP") && l.contains("too deep")));
    }

    @Test
    void rejectsWhenNotInConfirmedUptrend() {
        List<String> log = new ArrayList<>();
        SwingAnalyzer analyzer = new SwingAnalyzer(FIXED, log::add);
        SwingConfig cfg = SwingConfig.defaults(StrategyMode.PAPER); // ABOVE_MA_50_AND_200
        SwingCandidate broken = candidate("DOWN", new BigDecimal("9"), new BigDecimal("1.5"), false, false);

        assertTrue(analyzer.analyze(List.of(broken), cfg).isEmpty());
        assertTrue(log.stream().anyMatch(l -> l.contains("DOWN") && l.contains("not in a confirmed uptrend")));
    }

    @Test
    void computesEntryStopAndRecentHighTarget() {
        SwingAnalyzer analyzer = new SwingAnalyzer(FIXED, null);
        SwingRecommendation rec = analyzer.analyze(List.of(strong("NVDA")),
                SwingConfig.defaults(StrategyMode.PAPER)).getFirst();
        assertEquals(new BigDecimal("100.00"), rec.plannedEntryPrice());
        assertEquals(new BigDecimal("94.00"), rec.stopLossPrice());   // 6% stop
        assertEquals(new BigDecimal("110.00"), rec.targetPrice());    // target = recent high
        assertEquals(new BigDecimal("10.00"), rec.targetProfitPercent());
        // reward 10 / risk 6 ≈ 1.67
        assertEquals(new BigDecimal("1.67"), rec.rewardRiskRatio());
        assertEquals(SwingStatus.RECOMMENDED, rec.status());
    }

    @Test
    void fallsBackToPercentTargetWhenRecentHighBelowEntry() {
        SwingAnalyzer analyzer = new SwingAnalyzer(FIXED, null);
        // recentHigh (98) below entry (100) → use the 12% target-profit fallback.
        SwingCandidate freshHigh = new SwingCandidate("HI", "HI Inc", new BigDecimal("100"), new BigDecimal("98"),
                new BigDecimal("9"), new BigDecimal("99"), new BigDecimal("1.0"), 3_000_000L, new BigDecimal("1.5"),
                new BigDecimal("98"), new BigDecimal("96"), new BigDecimal("80"), true, true, true, true,
                new BigDecimal("4.17"), new BigDecimal("3.0"));
        SwingRecommendation rec = analyzer.analyze(List.of(freshHigh), SwingConfig.defaults(StrategyMode.PAPER)).getFirst();
        assertEquals(new BigDecimal("112.00"), rec.targetPrice());   // 100 * (1 + 12%)
    }

    private SwingCandidate strong(String symbol) {
        // ~9% pullback from a 110 recent high (near the ideal mid), stacked uptrend, near 50-day support.
        return new SwingCandidate(symbol, symbol + " Inc", new BigDecimal("100"), new BigDecimal("110"),
                new BigDecimal("9.09"), new BigDecimal("99"), new BigDecimal("1.0"), 3_000_000L,
                new BigDecimal("1.5"), new BigDecimal("98"), new BigDecimal("96"), new BigDecimal("80"),
                true, true, true, true, new BigDecimal("4.17"), new BigDecimal("3.0"));
    }

    private SwingCandidate weak(String symbol) {
        // 1% pullback (below min) → filtered before scoring.
        return new SwingCandidate(symbol, symbol + " Inc", new BigDecimal("50"), new BigDecimal("50.5"),
                new BigDecimal("1.0"), new BigDecimal("49"), new BigDecimal("-0.2"), 300_000L,
                new BigDecimal("0.5"), new BigDecimal("49"), new BigDecimal("48"), new BigDecimal("40"),
                true, true, true, true, new BigDecimal("4.0"), new BigDecimal("2.0"));
    }

    private SwingCandidate candidate(String symbol, BigDecimal pullback, BigDecimal relVol,
                                     boolean aboveMa50, boolean ma50AboveMa200) {
        return new SwingCandidate(symbol, symbol + " Inc", new BigDecimal("50"), new BigDecimal("55"),
                pullback, new BigDecimal("50.5"), new BigDecimal("-1.0"), 2_000_000L, relVol,
                new BigDecimal("49"), new BigDecimal("48"), new BigDecimal("45"), true, aboveMa50, true,
                ma50AboveMa200, new BigDecimal("4.0"), new BigDecimal("2.0"));
    }
}
