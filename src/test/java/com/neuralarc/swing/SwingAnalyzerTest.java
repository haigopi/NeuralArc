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
        assertEquals(750_000L, cfg.minimumAverageVolume());
        assertEquals(new BigDecimal("5"), cfg.minimumStockPrice());
        assertEquals(new BigDecimal("0.6"), cfg.minimumRelativeVolume());
        assertNull(cfg.maximumStockPrice());
        assertEquals(SwingConfig.TrendFilter.ABOVE_MA_50, cfg.trendFilter());
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
        assertEquals(750_000L, cfg.minimumAverageVolume());
        assertEquals(new BigDecimal("5"), cfg.minimumStockPrice());
        assertEquals(new BigDecimal("0.6"), cfg.minimumRelativeVolume());
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
        SwingConfig cfg = strictConfig();

        // 1% off the recent high — below the strict 3% minimum.
        SwingCandidate shallow = candidate("SHAL", new BigDecimal("1"), new BigDecimal("1.5"), true, true);
        // 20% off the recent high — above the strict 15% maximum (trend likely broken).
        SwingCandidate deep = candidate("DEEP", new BigDecimal("20"), new BigDecimal("1.5"), true, true);

        assertTrue(analyzer.analyze(List.of(shallow, deep), cfg).isEmpty());
        assertTrue(log.stream().anyMatch(l -> l.contains("SHAL") && l.contains("too shallow")));
        assertTrue(log.stream().anyMatch(l -> l.contains("DEEP") && l.contains("too deep")));
    }

    @Test
    void defaultsKeepTheScanInsideARealSwingPullbackBand() {
        SwingAnalyzer analyzer = new SwingAnalyzer(FIXED, null);
        SwingConfig cfg = SwingConfig.defaults(StrategyMode.PAPER);

        assertTrue(analyzer.passesFilters(candidate("GOOD", new BigDecimal("6"), new BigDecimal("0.9"), true, false), cfg));
        assertFalse(analyzer.passesFilters(candidate("NOISE", new BigDecimal("0.2"), new BigDecimal("0.9"), true, false), cfg));
        assertFalse(analyzer.passesFilters(candidate("BROKEN", new BigDecimal("45"), new BigDecimal("0.9"), true, false), cfg));
    }

    @Test
    void textbookSwingSetupClearsTheRecommendationThreshold() {
        // The bug this guards: a healthy pullback in a stacked uptrend used to score in the low 40s,
        // because full marks went to the midpoint of the configured band (a 25% collapse), so the
        // scanner returned nothing no matter how good the candidate was.
        SwingAnalyzer analyzer = new SwingAnalyzer(FIXED, null);
        SwingConfig cfg = SwingConfig.defaults(StrategyMode.PAPER);

        int score = analyzer.score(strong("NVDA"), cfg);

        assertTrue(score >= SwingAnalyzer.MINIMUM_RECOMMENDATION_SCORE,
                "a textbook swing setup must be recommendable, scored " + score);
    }

    @Test
    void aPullbackThatDipsJustUnderTheFiftyDayStillScoresAsASupportTest() {
        SwingAnalyzer analyzer = new SwingAnalyzer(FIXED, null);
        SwingConfig cfg = SwingConfig.defaults(StrategyMode.PAPER);
        SwingCandidate justBelowSupport = withSupportProximity(strong("NVDA"), new BigDecimal("-1.5"));
        SwingCandidate wellAboveSupport = withSupportProximity(strong("NVDA"), new BigDecimal("14"));

        assertTrue(analyzer.score(justBelowSupport, cfg) > analyzer.score(wellAboveSupport, cfg));
    }

    @Test
    void rejectsWhenNotInConfirmedUptrend() {
        List<String> log = new ArrayList<>();
        SwingAnalyzer analyzer = new SwingAnalyzer(FIXED, log::add);
        SwingConfig cfg = strictConfig();
        SwingCandidate broken = candidate("DOWN", new BigDecimal("9"), new BigDecimal("1.5"), false, false);

        assertTrue(analyzer.analyze(List.of(broken), cfg).isEmpty());
        assertTrue(log.stream().anyMatch(l -> l.contains("DOWN") && l.contains("not in a confirmed uptrend")));
    }

    @Test
    void computesEntryStopAndTargetFlooredAtTheConfiguredProfitTarget() {
        SwingAnalyzer analyzer = new SwingAnalyzer(FIXED, null);
        SwingRecommendation rec = analyzer.analyze(List.of(strong("NVDA")),
                SwingConfig.defaults(StrategyMode.PAPER)).getFirst();
        assertEquals(new BigDecimal("99.75"), rec.plannedEntryPrice());   // 0.25% under the market
        assertEquals(new BigDecimal("93.76"), rec.stopLossPrice());   // 6% stop, floored to the cent
        // The 110 recent high is below the operator's 12% target, so the plan aims at the target it
        // was configured for rather than at a reward smaller than its own stop.
        assertEquals(new BigDecimal("111.72"), rec.targetPrice());
        assertEquals(new BigDecimal("12.00"), rec.targetProfitPercent());
        assertEquals(new BigDecimal("2.00"), rec.rewardRiskRatio());  // reward 12 / risk 6
        assertEquals(SwingStatus.RECOMMENDED, rec.status());
    }

    @Test
    void aTightPercentStopIsHeldATenCentGapUnderTheEntry() {
        // A 1% stop under a $5.48 entry is only six cents of room, which stops the position out on
        // ordinary noise. The stop is widened to the minimum gap instead.
        SwingConfig tightStop = new SwingConfig(null, null, 0L, null, null, null, null,
                new BigDecimal("1"), null, 0, null, StrategyMode.PAPER);
        SwingCandidate lowPriced = new SwingCandidate("LOW", "LOW Inc", new BigDecimal("5.50"),
                new BigDecimal("6.60"), new BigDecimal("14"), new BigDecimal("5.45"), new BigDecimal("1.0"),
                3_000_000L, new BigDecimal("1.5"), new BigDecimal("5.40"), new BigDecimal("5.30"),
                new BigDecimal("4.40"), true, true, true, true, new BigDecimal("4.17"), new BigDecimal("3.0"));

        SwingRecommendation rec = new SwingAnalyzer(FIXED, null).analyze(List.of(lowPriced), tightStop).getFirst();

        assertEquals(new BigDecimal("5.48"), rec.plannedEntryPrice());
        assertEquals(new BigDecimal("5.38"), rec.stopLossPrice());
        assertTrue(rec.plannedEntryPrice().subtract(rec.stopLossPrice())
                .compareTo(new BigDecimal("0.10")) >= 0, "the stop must clear the entry by at least a dime");
    }

    @Test
    void targetFollowsTheRecentHighWhenItIsAboveTheConfiguredTarget() {
        SwingAnalyzer analyzer = new SwingAnalyzer(FIXED, null);
        SwingCandidate deepPullback = new SwingCandidate("FAR", "FAR Inc", new BigDecimal("100"),
                new BigDecimal("120"), new BigDecimal("14"), new BigDecimal("99"), new BigDecimal("1.0"),
                3_000_000L, new BigDecimal("1.5"), new BigDecimal("98"), new BigDecimal("96"),
                new BigDecimal("80"), true, true, true, true, new BigDecimal("4.17"), new BigDecimal("3.0"));

        SwingRecommendation rec = analyzer.analyze(List.of(deepPullback),
                SwingConfig.defaults(StrategyMode.PAPER)).getFirst();

        assertEquals(new BigDecimal("120.00"), rec.targetPrice());
    }

    @Test
    void usesThePercentTargetWhenTheRecentHighIsBelowEntry() {
        SwingAnalyzer analyzer = new SwingAnalyzer(FIXED, null);
        // recentHigh (98) below entry (100) → use the 12% target-profit fallback.
        SwingCandidate freshHigh = new SwingCandidate("HI", "HI Inc", new BigDecimal("100"), new BigDecimal("98"),
                new BigDecimal("9"), new BigDecimal("99"), new BigDecimal("1.0"), 3_000_000L, new BigDecimal("1.5"),
                new BigDecimal("98"), new BigDecimal("96"), new BigDecimal("80"), true, true, true, true,
                new BigDecimal("4.17"), new BigDecimal("3.0"));
        SwingRecommendation rec = analyzer.analyze(List.of(freshHigh), SwingConfig.defaults(StrategyMode.PAPER)).getFirst();
        assertEquals(new BigDecimal("111.72"), rec.targetPrice());   // 99.75 * (1 + 12%)
    }

    @Test
    void neverPlansAboveTheMarket() {
        // The reported bug: a 215.695 market rounded half-up to a 215.70 limit, a cent above the market.
        SwingAnalyzer analyzer = new SwingAnalyzer(FIXED, null);
        SwingCandidate awkwardPrice = new SwingCandidate("NVDA", "NVDA Inc", new BigDecimal("215.695"),
                new BigDecimal("237"), new BigDecimal("9.0"), new BigDecimal("214"), new BigDecimal("1.0"),
                3_000_000L, new BigDecimal("1.5"), new BigDecimal("210"), new BigDecimal("205"),
                new BigDecimal("180"), true, true, true, true, new BigDecimal("4.17"), new BigDecimal("3.0"),
                BigDecimal.ZERO);

        SwingRecommendation rec = analyzer.analyze(List.of(awkwardPrice), SwingConfig.defaults(StrategyMode.PAPER)).getFirst();

        assertTrue(rec.plannedEntryPrice().compareTo(awkwardPrice.currentPrice()) <= 0,
                "planned " + rec.plannedEntryPrice() + " must not sit above the market " + awkwardPrice.currentPrice());
        assertEquals(new BigDecimal("215.15"), rec.plannedEntryPrice());
    }

    @Test
    void theWeeksLowIsTheFloorSoTheOrderCanStillFill() {
        SwingAnalyzer analyzer = new SwingAnalyzer(FIXED, null);
        // The stock has not traded below 99.90 all week, so the plan stops there rather than at 99.75.
        SwingCandidate shallowWeek = new SwingCandidate("NVDA", "NVDA Inc", new BigDecimal("100"),
                new BigDecimal("110"), new BigDecimal("9.09"), new BigDecimal("99"), new BigDecimal("1.0"),
                3_000_000L, new BigDecimal("1.5"), new BigDecimal("98"), new BigDecimal("96"),
                new BigDecimal("80"), true, true, true, true, new BigDecimal("4.17"), new BigDecimal("3.0"),
                new BigDecimal("99.90"));

        SwingRecommendation rec = analyzer.analyze(List.of(shallowWeek), SwingConfig.defaults(StrategyMode.PAPER)).getFirst();

        assertEquals(new BigDecimal("99.90"), rec.plannedEntryPrice());
    }

    private SwingCandidate strong(String symbol) {
        // ~9% pullback from a 110 recent high (near the ideal mid), stacked uptrend, near 50-day support.
        return new SwingCandidate(symbol, symbol + " Inc", new BigDecimal("100"), new BigDecimal("110"),
                new BigDecimal("9.09"), new BigDecimal("99"), new BigDecimal("1.0"), 3_000_000L,
                new BigDecimal("1.5"), new BigDecimal("98"), new BigDecimal("96"), new BigDecimal("80"),
                true, true, true, true, new BigDecimal("4.17"), new BigDecimal("3.0"));
    }

    private SwingCandidate withSupportProximity(SwingCandidate base, BigDecimal proximityPercent) {
        return new SwingCandidate(base.symbol(), base.companyName(), base.currentPrice(), base.recentHigh(),
                base.pullbackPercent(), base.previousClose(), base.dayChangePercent(), base.averageVolume(),
                base.relativeVolume(), base.movingAverage20(), base.movingAverage50(), base.movingAverage200(),
                base.aboveMa20(), base.aboveMa50(), base.aboveMa200(), base.ma50AboveMa200(),
                proximityPercent, base.atr());
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

    private SwingConfig strictConfig() {
        return new SwingConfig(new BigDecimal("3"), new BigDecimal("15"), 500_000L, new BigDecimal("5"),
                new BigDecimal("0.8"), null, SwingConfig.TrendFilter.ABOVE_MA_50_AND_200,
                new BigDecimal("6"), new BigDecimal("12"), 10, SwingConfig.ExecutionFrequency.MANUAL,
                StrategyMode.PAPER);
    }
}
