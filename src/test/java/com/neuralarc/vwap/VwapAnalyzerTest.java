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
        assertEquals(new BigDecimal("0.4"), cfg.minimumDiscountPercent());
        assertEquals(new BigDecimal("4"), cfg.maximumDiscountPercent());
        assertEquals(1_000_000L, cfg.minimumAverageVolume());
        assertEquals(new BigDecimal("5"), cfg.minimumStockPrice());
        assertEquals(new BigDecimal("0.8"), cfg.minimumRelativeVolume());
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
        assertEquals(new BigDecimal("0.4"), cfg.minimumDiscountPercent());
        assertEquals(new BigDecimal("4"), cfg.maximumDiscountPercent());
        assertEquals(1_000_000L, cfg.minimumAverageVolume());
        assertEquals(new BigDecimal("5"), cfg.minimumStockPrice());
        assertEquals(new BigDecimal("0.8"), cfg.minimumRelativeVolume());
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

        // 0.2% below VWAP — inside the day's noise, below the 0.4% minimum.
        VwapCandidate shallow = candidate("SHAL", new BigDecimal("0.2"), new BigDecimal("2.0"), true, true);
        // 12% below VWAP — far above the 4% maximum (a breakdown, not a stretch).
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
        assertEquals(new BigDecimal("99.75"), rec.plannedEntryPrice());   // 0.25% under the market
        assertEquals(new BigDecimal("95.76"), rec.stopLossPrice());   // 4% stop
        assertEquals(new BigDecimal("101.20"), rec.targetPrice());    // target = VWAP
        assertEquals(new BigDecimal("1.45"), rec.reversionUpsidePercent());
        assertEquals(VwapStatus.RECOMMENDED, rec.status());
    }

    @Test
    void aNormalVolatileSessionIsNoLongerRejectedAsAWideSpread() {
        // The candidate's range field is the day's high-to-low move, not a bid/ask spread. A 5% range
        // on a liquid large cap used to be rejected outright as "spread too wide".
        VwapAnalyzer analyzer = new VwapAnalyzer(FIXED, null);
        VwapCandidate volatileButOrderly = new VwapCandidate("INTC", "INTC Inc", new BigDecimal("100"),
                new BigDecimal("101.2"), new BigDecimal("1.2"), new BigDecimal("101"), new BigDecimal("-1.0"),
                30_000_000L, new BigDecimal("1.2"), new BigDecimal("95"), new BigDecimal("90"), true, true,
                new BigDecimal("5.0"));

        assertTrue(analyzer.passesFilters(volatileButOrderly, VwapConfig.defaults(StrategyMode.PAPER)));
    }

    @Test
    void aDisorderlySessionIsStillRejected() {
        List<String> log = new ArrayList<>();
        VwapAnalyzer analyzer = new VwapAnalyzer(FIXED, log::add);
        VwapCandidate disorderly = new VwapCandidate("WILD", "WILD Inc", new BigDecimal("100"),
                new BigDecimal("101.2"), new BigDecimal("1.2"), new BigDecimal("101"), new BigDecimal("-1.0"),
                30_000_000L, new BigDecimal("1.2"), new BigDecimal("95"), new BigDecimal("90"), true, true,
                new BigDecimal("18"));

        assertFalse(analyzer.passesFilters(disorderly, VwapConfig.defaults(StrategyMode.PAPER)));
        assertTrue(log.stream().anyMatch(l -> l.contains("WILD") && l.contains("intraday range too wide")));
    }

    @Test
    void anOrdinaryStretchBelowVwapClearsTheRecommendationThreshold() {
        // The bug this guards: scoring peaked at the midpoint of the configured band, so with a 1-8%
        // filter the ideal trade was a 4.5% collapse and ordinary reversion setups never reached 60.
        VwapAnalyzer analyzer = new VwapAnalyzer(FIXED, null);

        int score = analyzer.score(strong("NVDA"), VwapConfig.defaults(StrategyMode.PAPER));

        assertTrue(score >= VwapAnalyzer.MINIMUM_RECOMMENDATION_SCORE,
                "an ordinary VWAP stretch must be recommendable, scored " + score);
    }

    @Test
    void neverPlansAboveTheMarketAndFloorsAtTheWeeksLow() {
        VwapAnalyzer analyzer = new VwapAnalyzer(FIXED, null);
        VwapCandidate awkwardPrice = new VwapCandidate("NVDA", "NVDA Inc", new BigDecimal("215.695"),
                new BigDecimal("218.30"), new BigDecimal("1.2"), new BigDecimal("217"), new BigDecimal("-1.0"),
                3_000_000L, new BigDecimal("2.0"), new BigDecimal("205"), new BigDecimal("190"), true, true,
                new BigDecimal("2.5"), BigDecimal.ZERO);

        VwapRecommendation rec = analyzer.analyze(List.of(awkwardPrice), VwapConfig.defaults(StrategyMode.PAPER)).getFirst();

        assertTrue(rec.plannedEntryPrice().compareTo(awkwardPrice.currentPrice()) <= 0,
                "planned " + rec.plannedEntryPrice() + " must not sit above the market");
        assertEquals(new BigDecimal("215.15"), rec.plannedEntryPrice());
    }

    private VwapCandidate strong(String symbol) {
        // 1.2% below VWAP (inside the reversion band), 2x rel vol, above both MAs, orderly session.
        return new VwapCandidate(symbol, symbol + " Inc", new BigDecimal("100"), new BigDecimal("101.2"),
                new BigDecimal("1.2"), new BigDecimal("101"), new BigDecimal("-1.0"), 3_000_000L,
                new BigDecimal("2.0"), new BigDecimal("95"), new BigDecimal("90"), true, true, new BigDecimal("2.5"));
    }

    private VwapCandidate weak(String symbol) {
        // 0.2% below VWAP (below min) → filtered before scoring.
        return new VwapCandidate(symbol, symbol + " Inc", new BigDecimal("8"), new BigDecimal("8.016"),
                new BigDecimal("0.2"), new BigDecimal("8.1"), new BigDecimal("-0.2"), 300_000L,
                new BigDecimal("1.0"), new BigDecimal("7"), new BigDecimal("6.5"), true, true, new BigDecimal("4.0"));
    }

    private VwapCandidate candidate(String symbol, BigDecimal discount, BigDecimal relVol,
                                    boolean aboveMa50, boolean aboveMa200) {
        return new VwapCandidate(symbol, symbol + " Inc", new BigDecimal("50"), new BigDecimal("52"),
                discount, new BigDecimal("50.5"), new BigDecimal("-1.0"), 2_000_000L, relVol,
                new BigDecimal("48"), new BigDecimal("45"), aboveMa50, aboveMa200, new BigDecimal("0.6"));
    }
}
