package com.neuralarc.vwap;

import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VwapAnalyzerTest {
    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-06-15T18:00:00Z"), ZoneOffset.UTC);

    @Test
    void defaultsMatchDialogRequirements() {
        VwapConfig cfg = VwapConfig.defaults(StrategyMode.LIVE);
        assertEquals(new BigDecimal("1"), cfg.minimumDiscountPercent());
        assertEquals(new BigDecimal("8"), cfg.maximumDiscountPercent());
        assertEquals(500_000L, cfg.minimumAverageVolume());
        assertEquals(new BigDecimal("5"), cfg.minimumStockPrice());
        assertEquals(new BigDecimal("1.0"), cfg.minimumRelativeVolume());
        assertNull(cfg.maximumStockPrice());
        assertEquals(VwapConfig.TrendFilter.ABOVE_MA_50, cfg.trendFilter());
        assertEquals(new BigDecimal("4"), cfg.stopLossPercent());
        assertEquals(StrategyMode.LIVE, cfg.mode());
    }

    @Test
    void rejectedValuesResetToDefaults() {
        VwapConfig cfg = new VwapConfig(new BigDecimal("-1"), new BigDecimal("0"), -5L,
                new BigDecimal("0"), new BigDecimal("-2"), null, null, new BigDecimal("-3"),
                -4, null, StrategyMode.PAPER);
        assertEquals(new BigDecimal("1"), cfg.minimumDiscountPercent());
        assertEquals(new BigDecimal("8"), cfg.maximumDiscountPercent());
        assertEquals(500_000L, cfg.minimumAverageVolume());
        assertEquals(new BigDecimal("5"), cfg.minimumStockPrice());
        assertEquals(new BigDecimal("1.0"), cfg.minimumRelativeVolume());
        assertEquals(new BigDecimal("4"), cfg.stopLossPercent());
        assertEquals(10, cfg.maxStocksToAdd());
    }

    @Test
    void analyzeAddsOnlyRecommendationsAtOrAboveThresholdSortedByScore() {
        List<String> log = new ArrayList<>();
        VwapAnalyzer analyzer = new VwapAnalyzer(FIXED, log::add);
        List<VwapRecommendation> result = analyzer.analyze(List.of(strong("NVDA"), weak("ABC")),
                VwapConfig.defaults(StrategyMode.PAPER));
        assertEquals(1, result.size());
        assertEquals("NVDA", result.getFirst().symbol());
        assertTrue(result.getFirst().strategyScore() >= VwapAnalyzer.MINIMUM_RECOMMENDATION_SCORE);
        assertEquals(StrategyMode.PAPER, result.getFirst().mode());
        assertTrue(log.stream().anyMatch(line -> line.contains("Rejected ABC")));
    }

    @Test
    void rejectsTooShallowAndTooDeepDiscounts() {
        List<String> log = new ArrayList<>();
        VwapAnalyzer analyzer = new VwapAnalyzer(FIXED, log::add);
        VwapConfig cfg = VwapConfig.defaults(StrategyMode.PAPER);

        // 0.4% below VWAP — below the 1% minimum.
        VwapCandidate shallow = candidate("SHAL", new BigDecimal("0.4"), new BigDecimal("2.0"), true, true);
        // 12% below VWAP — above the 8% maximum (possible breakdown).
        VwapCandidate deep = candidate("DEEP", new BigDecimal("12"), new BigDecimal("2.0"), true, true);

        assertTrue(analyzer.analyze(List.of(shallow, deep), cfg).isEmpty());
        assertTrue(log.stream().anyMatch(l -> l.contains("SHAL") && l.contains("not far enough below VWAP")));
        assertTrue(log.stream().anyMatch(l -> l.contains("DEEP") && l.contains("too far below VWAP")));
    }

    @Test
    void rejectsWhenNotInUptrend() {
        List<String> log = new ArrayList<>();
        VwapAnalyzer analyzer = new VwapAnalyzer(FIXED, log::add);
        VwapConfig cfg = VwapConfig.defaults(StrategyMode.PAPER); // ABOVE_MA_50
        VwapCandidate belowMa = candidate("DOWN", new BigDecimal("3"), new BigDecimal("2.0"), false, false);

        assertTrue(analyzer.analyze(List.of(belowMa), cfg).isEmpty());
        assertTrue(log.stream().anyMatch(l -> l.contains("DOWN") && l.contains("not in an uptrend")));
    }

    @Test
    void computesEntryStopAndVwapTarget() {
        VwapAnalyzer analyzer = new VwapAnalyzer(FIXED, null);
        VwapRecommendation rec = analyzer.analyze(List.of(strong("NVDA")),
                VwapConfig.defaults(StrategyMode.PAPER)).getFirst();
        assertEquals(new BigDecimal("100.00"), rec.plannedEntryPrice());
        assertEquals(new BigDecimal("96.00"), rec.stopLossPrice());   // 4% stop
        assertEquals(new BigDecimal("104.00"), rec.targetPrice());    // target = VWAP
        assertEquals(new BigDecimal("4.00"), rec.reversionUpsidePercent());
        assertEquals(VwapStatus.RECOMMENDED, rec.status());
    }

    private VwapCandidate strong(String symbol) {
        // 4% below VWAP (near the ideal mid), 2x rel vol, above both MAs, tight spread → high score.
        return new VwapCandidate(symbol, symbol + " Inc", new BigDecimal("100"), new BigDecimal("104"),
                new BigDecimal("4"), new BigDecimal("101"), new BigDecimal("-1.0"), 3_000_000L,
                new BigDecimal("2.0"), new BigDecimal("95"), new BigDecimal("90"), true, true, new BigDecimal("0.5"));
    }

    private VwapCandidate weak(String symbol) {
        // 0.3% below VWAP (below min) → filtered before scoring.
        return new VwapCandidate(symbol, symbol + " Inc", new BigDecimal("8"), new BigDecimal("8.024"),
                new BigDecimal("0.3"), new BigDecimal("8.1"), new BigDecimal("-0.2"), 300_000L,
                new BigDecimal("1.0"), new BigDecimal("7"), new BigDecimal("6.5"), true, true, new BigDecimal("4.0"));
    }

    private VwapCandidate candidate(String symbol, BigDecimal discount, BigDecimal relVol,
                                    boolean aboveMa50, boolean aboveMa200) {
        return new VwapCandidate(symbol, symbol + " Inc", new BigDecimal("50"), new BigDecimal("52"),
                discount, new BigDecimal("50.5"), new BigDecimal("-1.0"), 2_000_000L, relVol,
                new BigDecimal("48"), new BigDecimal("45"), aboveMa50, aboveMa200, new BigDecimal("0.6"));
    }
}
