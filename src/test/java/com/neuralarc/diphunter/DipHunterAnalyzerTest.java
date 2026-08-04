package com.neuralarc.diphunter;

import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DipHunterAnalyzerTest {
    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-06-15T18:00:00Z"), ZoneOffset.UTC);

    @Test
    void defaultsMatchDialogRequirements() {
        DipHunterConfig cfg = DipHunterConfig.defaults(StrategyMode.LIVE);
        assertEquals(new BigDecimal("0.1"), cfg.minimumPullbackPercent());
        assertEquals(new BigDecimal("50"), cfg.maximumPullbackPercent());
        assertEquals(100_000L, cfg.minimumAverageVolume());
        assertEquals(new BigDecimal("0.5"), cfg.minimumStockPrice());
        assertEquals(new BigDecimal("0.5"), cfg.minimumRelativeVolume());
        assertNull(cfg.maximumStockPrice());
        assertEquals(DipHunterConfig.TrendFilter.DISABLED, cfg.trendFilter());
        assertEquals(DipHunterConfig.BounceConfirmation.MANUAL_REVIEW, cfg.bounceConfirmation());
        assertEquals(StrategyMode.LIVE, cfg.mode());
    }

    @Test
    void rejectedValuesResetToDefaults() {
        DipHunterConfig cfg = new DipHunterConfig(new BigDecimal("-1"), new BigDecimal("0"), -5L,
                new BigDecimal("0"), new BigDecimal("-2"), null, null, null,
                new BigDecimal("-3"), new BigDecimal("0"), -4, null, StrategyMode.PAPER);
        assertEquals(new BigDecimal("0.1"), cfg.minimumPullbackPercent());
        assertEquals(new BigDecimal("50"), cfg.maximumPullbackPercent());
        assertEquals(100_000L, cfg.minimumAverageVolume());
        assertEquals(new BigDecimal("0.5"), cfg.minimumStockPrice());
        assertEquals(new BigDecimal("0.5"), cfg.minimumRelativeVolume());
        assertEquals(new BigDecimal("5"), cfg.stopLossPercent());
        assertEquals(new BigDecimal("10"), cfg.takeProfitPercent());
        assertEquals(10, cfg.maxStocksToAdd());
    }

    @Test
    void analyzeAddsOnlyRecommendationsAtOrAboveThresholdSortedByScore() {
        List<String> log = new ArrayList<>();
        DipHunterAnalyzer analyzer = new DipHunterAnalyzer(FIXED, log::add);
        List<DipHunterRecommendation> result = analyzer.analyze(List.of(strong("NVDA"), weak("ABC")),
                DipHunterConfig.defaults(StrategyMode.PAPER));
        assertEquals(1, result.size());
        assertEquals("NVDA", result.getFirst().symbol());
        assertTrue(result.getFirst().strategyScore() >= DipHunterAnalyzer.MINIMUM_RECOMMENDATION_SCORE);
        assertEquals(StrategyMode.PAPER, result.getFirst().mode());
        assertTrue(log.stream().anyMatch(line -> line.contains("Rejected ABC")));
    }

    @Test
    void rejectsTooShallowAndTooDeepPullbacks() {
        List<String> log = new ArrayList<>();
        DipHunterAnalyzer analyzer = new DipHunterAnalyzer(FIXED, log::add);
        DipHunterConfig cfg = strictConfig();

        // 1% pullback — below the 3% minimum.
        DipHunterCandidate shallow = candidate("SHAL", new BigDecimal("1"), new BigDecimal("2.0"), true, true, true);
        // 25% pullback — above the 15% maximum (falling knife).
        DipHunterCandidate deep = candidate("DEEP", new BigDecimal("25"), new BigDecimal("2.0"), true, true, true);

        assertTrue(analyzer.analyze(List.of(shallow, deep), cfg).isEmpty());
        assertTrue(log.stream().anyMatch(l -> l.contains("SHAL") && l.contains("pullback below minimum")));
        assertTrue(log.stream().anyMatch(l -> l.contains("DEEP") && l.contains("pullback too deep")));
    }

    @Test
    void defaultsAllowWidePullbackRangeWithoutTrendOrReversalConfirmation() {
        DipHunterAnalyzer analyzer = new DipHunterAnalyzer(FIXED, null);
        DipHunterConfig cfg = DipHunterConfig.defaults(StrategyMode.PAPER);

        assertTrue(analyzer.passesFilters(candidate("SHALLOW", new BigDecimal("0.2"), new BigDecimal("0.6"), false, false, false), cfg));
        assertTrue(analyzer.passesFilters(candidate("DEEP", new BigDecimal("45"), new BigDecimal("0.6"), false, false, false), cfg));
        assertTrue(analyzer.passesFilters(candidate("CHEAP", new BigDecimal("5"), new BigDecimal("0.6"), false, false, false), cfg));
    }

    @Test
    void rejectsWhenNotInUptrend() {
        List<String> log = new ArrayList<>();
        DipHunterAnalyzer analyzer = new DipHunterAnalyzer(FIXED, log::add);
        DipHunterConfig cfg = strictConfig();
        DipHunterCandidate belowBothMas = candidate("DOWN", new BigDecimal("7"), new BigDecimal("2.0"), false, false, true);

        assertTrue(analyzer.analyze(List.of(belowBothMas), cfg).isEmpty());
        assertTrue(log.stream().anyMatch(l -> l.contains("DOWN") && l.contains("not in an uptrend")));
    }

    @Test
    void rejectsWhenNoIntradayReversalAndConfirmationRequired() {
        List<String> log = new ArrayList<>();
        DipHunterAnalyzer analyzer = new DipHunterAnalyzer(FIXED, log::add);
        DipHunterConfig cfg = strictConfig();
        DipHunterCandidate noReversal = candidate("FLAT", new BigDecimal("7"), new BigDecimal("2.0"), true, true, false);

        assertTrue(analyzer.analyze(List.of(noReversal), cfg).isEmpty());
        assertTrue(log.stream().anyMatch(l -> l.contains("FLAT") && l.contains("no intraday reversal")));
    }

    @Test
    void computesPlannedStopAndTargetFromEntry() {
        DipHunterAnalyzer analyzer = new DipHunterAnalyzer(FIXED, null);
        DipHunterRecommendation rec = analyzer.analyze(List.of(strong("NVDA")),
                DipHunterConfig.defaults(StrategyMode.PAPER)).getFirst();
        assertEquals(new BigDecimal("100.00"), rec.plannedEntryPrice());
        assertEquals(new BigDecimal("95.00"), rec.stopLossPrice());   // 5% stop
        assertEquals(new BigDecimal("110.00"), rec.takeProfitPrice()); // 10% target
        assertEquals(DipHunterStatus.RECOMMENDED, rec.status());
    }

    private DipHunterCandidate strong(String symbol) {
        // 7% pullback, 3x rel vol, above both MAs, intraday reversal, tight spread → high score.
        return new DipHunterCandidate(symbol, symbol + " Inc", new BigDecimal("7"), new BigDecimal("-1.0"),
                3_000_000L, new BigDecimal("3.0"), new BigDecimal("100"), new BigDecimal("101"),
                new BigDecimal("107.53"), new BigDecimal("95"), new BigDecimal("90"),
                true, true, true, new BigDecimal("0.5"), new BigDecimal("100"));
    }

    private DipHunterCandidate weak(String symbol) {
        // 1% pullback (below min) → filtered before scoring.
        return new DipHunterCandidate(symbol, symbol + " Inc", new BigDecimal("1"), new BigDecimal("-0.2"),
                300_000L, new BigDecimal("1.0"), new BigDecimal("8"), new BigDecimal("8.1"),
                new BigDecimal("8.08"), new BigDecimal("7"), new BigDecimal("6.5"),
                true, true, false, new BigDecimal("4.0"), new BigDecimal("8"));
    }

    private DipHunterCandidate candidate(String symbol, BigDecimal pullback, BigDecimal relVol,
                                         boolean aboveMa20, boolean aboveMa50, boolean reversal) {
        return new DipHunterCandidate(symbol, symbol + " Inc", pullback, new BigDecimal("-1.0"),
                2_000_000L, relVol, new BigDecimal("50"), new BigDecimal("50.5"),
                new BigDecimal("55"), new BigDecimal("48"), new BigDecimal("45"),
                aboveMa20, aboveMa50, reversal, new BigDecimal("0.6"), new BigDecimal("50"));
    }

    private DipHunterConfig strictConfig() {
        return new DipHunterConfig(new BigDecimal("3"), new BigDecimal("15"), 500_000L, new BigDecimal("5"),
                new BigDecimal("1.2"), null, DipHunterConfig.TrendFilter.ABOVE_MA_20_OR_50,
                DipHunterConfig.BounceConfirmation.INTRADAY_REVERSAL, new BigDecimal("5"), new BigDecimal("10"),
                10, DipHunterConfig.ExecutionFrequency.MANUAL, StrategyMode.PAPER);
    }
}
