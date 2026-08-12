package com.neuralarc.rangerider;

import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RangeRiderAnalyzerTest {
    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-06-15T18:00:00Z"), ZoneOffset.UTC);

    @Test
    void defaultsMatchDialogRequirements() {
        RangeRiderConfig cfg = RangeRiderConfig.defaults(StrategyMode.LIVE);
        assertEquals(15, cfg.lookbackSessions());
        assertEquals(new BigDecimal("1"), cfg.minimumAverageRangePercent());
        assertEquals(new BigDecimal("12"), cfg.maximumAverageRangePercent());
        assertEquals(new BigDecimal("50"), cfg.minimumSameDayFillRatePercent());
        assertEquals(1_000_000L, cfg.minimumAverageVolume());
        assertEquals(new BigDecimal("10"), cfg.minimumStockPrice());
        assertNull(cfg.maximumStockPrice());
        assertEquals(new BigDecimal("50"), cfg.targetCapturePercent());
        assertEquals(new BigDecimal("0.5"), cfg.minimumExpectedGainPercent());
        assertEquals(new BigDecimal("2"), cfg.stopLossPercent());
        assertEquals(StrategyMode.LIVE, cfg.mode());
    }

    @Test
    void rejectedValuesResetToDefaults() {
        RangeRiderConfig cfg = new RangeRiderConfig(-3, new BigDecimal("-1"), new BigDecimal("0"),
                new BigDecimal("-5"), -10L, new BigDecimal("0"), null, new BigDecimal("-1"),
                new BigDecimal("-1"), new BigDecimal("-2"), -4, null, StrategyMode.PAPER);
        assertEquals(15, cfg.lookbackSessions());
        assertEquals(new BigDecimal("1"), cfg.minimumAverageRangePercent());
        assertEquals(new BigDecimal("12"), cfg.maximumAverageRangePercent());
        assertEquals(new BigDecimal("50"), cfg.minimumSameDayFillRatePercent());
        assertEquals(1_000_000L, cfg.minimumAverageVolume());
        assertEquals(new BigDecimal("50"), cfg.targetCapturePercent());
        assertEquals(new BigDecimal("0.5"), cfg.minimumExpectedGainPercent());
        assertEquals(new BigDecimal("2"), cfg.stopLossPercent());
        assertEquals(10, cfg.maxStocksToAdd());
    }

    @Test
    void zeroGatesAreHonoredButCaptureIsClampedToAUsableRange() {
        RangeRiderConfig cfg = new RangeRiderConfig(15, new BigDecimal("1"), new BigDecimal("12"),
                BigDecimal.ZERO, 1_000_000L, new BigDecimal("10"), null, new BigDecimal("250"), BigDecimal.ZERO,
                new BigDecimal("2"), 10, RangeRiderConfig.ExecutionFrequency.MANUAL, StrategyMode.PAPER);
        assertEquals(BigDecimal.ZERO, cfg.minimumSameDayFillRatePercent());
        assertEquals(BigDecimal.ZERO, cfg.minimumExpectedGainPercent());
        assertEquals(BigDecimal.valueOf(100), cfg.targetCapturePercent(),
                "capturing more than the whole typical move is not a thing");
    }

    @Test
    void anchorsThePlanToTheReferenceCloseUsingTheTypicalDipAndRally() {
        RangeRiderAnalyzer analyzer = new RangeRiderAnalyzer(FIXED, null);
        RangeRiderConfig cfg = RangeRiderConfig.defaults(StrategyMode.PAPER);
        // Averages: open 100, low 98, high 102 → typical dip 2%, typical rally 2%.
        RangeRiderCandidate c = steadyRange("NVDA", 15, "98", "102", "100", 4_000_000L, "100.00");

        assertEquals(new BigDecimal("2.0000"), c.averageDipPercent());
        assertEquals(new BigDecimal("2.0000"), c.averageRallyPercent());
        // Capturing half of each: buy 1% under, sell 1% over the $100 reference close.
        assertEquals(new BigDecimal("99.00"), analyzer.plannedEntryPrice(c, cfg));
        assertEquals(new BigDecimal("101.00"), analyzer.plannedTargetPrice(c, cfg));
    }

    @Test
    void repricesThePlanWhenTheStockHasDriftedAwayFromTheOldAverages() {
        RangeRiderAnalyzer analyzer = new RangeRiderAnalyzer(FIXED, null);
        RangeRiderConfig cfg = RangeRiderConfig.defaults(StrategyMode.PAPER);
        // Same 2% dip/rally shape, but the stock now closes at $200 rather than near its $100 averages.
        RangeRiderCandidate drifted = steadyRange("MOVED", 15, "98", "102", "100", 4_000_000L, "200.00");

        BigDecimal entry = analyzer.plannedEntryPrice(drifted, cfg);

        assertEquals(new BigDecimal("198.00"), entry,
                "the plan follows the stock; a stale $98 level from three weeks ago would never fill");
        assertEquals(new BigDecimal("202.00"), analyzer.plannedTargetPrice(drifted, cfg));
    }

    @Test
    void averagesTheDailyOpenHighAndLowAcrossTheLookbackWindow() {
        RangeRiderCandidate c = steadyRange("NVDA", 15, "98", "102", "100", 4_000_000L, "100.00");

        assertEquals(15, c.sessionsAnalyzed());
        assertEquals(new BigDecimal("98.00"), c.averageLow());
        assertEquals(new BigDecimal("102.00"), c.averageHigh());
        assertEquals(new BigDecimal("100.00"), c.averageOpen());
    }

    @Test
    void recommendsAConsistentDailyRangeAndReportsAFullFillRate() {
        List<String> log = new ArrayList<>();
        RangeRiderAnalyzer analyzer = new RangeRiderAnalyzer(FIXED, log::add);
        RangeRiderCandidate c = steadyRange("NVDA", 15, "98", "102", "100", 4_000_000L, "100.00");

        List<RangeRiderRecommendation> result = analyzer.analyze(List.of(c), RangeRiderConfig.defaults(StrategyMode.PAPER));

        assertEquals(1, result.size());
        RangeRiderRecommendation r = result.getFirst();
        assertEquals("NVDA", r.symbol());
        assertEquals(new BigDecimal("99.00"), r.plannedEntryPrice());
        assertEquals(new BigDecimal("101.00"), r.targetPrice());
        assertEquals(new BigDecimal("2.02"), r.expectedGainPercent());
        assertEquals(new BigDecimal("100.00"), r.sameDayFillRatePercent());
        assertEquals(new BigDecimal("100.00"), r.entryTouchRatePercent());
        assertEquals(15, r.sessionsAnalyzed());
        assertEquals(new BigDecimal("2.00"), r.averageDipPercent());
        assertEquals(new BigDecimal("2.00"), r.averageRallyPercent());
        // Stop loss is 2% below the planned entry, not below the average low.
        assertEquals(new BigDecimal("97.02"), r.stopLossPrice());
        assertTrue(r.strategyScore() >= RangeRiderAnalyzer.MINIMUM_RECOMMENDATION_SCORE);
        assertEquals(StrategyMode.PAPER, r.mode());
        assertEquals(RangeRiderStatus.RECOMMENDED, r.status());
    }

    /**
     * Regression guard for the scan that returned nothing: a stock that completes the round trip on
     * half of its sessions is a genuinely good daily-income candidate and must survive the defaults.
     */
    @Test
    void aRealisticHalfTheTimeFillRateStillQualifiesUnderTheDefaults() {
        RangeRiderAnalyzer analyzer = new RangeRiderAnalyzer(FIXED, null);
        RangeRiderCandidate c = candidate("REAL", mixedSessions(), 4_000_000L, "100.00");

        List<RangeRiderRecommendation> result = analyzer.analyze(List.of(c), RangeRiderConfig.defaults(StrategyMode.PAPER));

        assertEquals(1, result.size());
        assertEquals(new BigDecimal("50.00"), result.getFirst().sameDayFillRatePercent());
    }

    @Test
    void rejectsWhenTheSameDayRoundTripFallsBelowTheConfiguredMinimum() {
        List<String> log = new ArrayList<>();
        RangeRiderAnalyzer analyzer = new RangeRiderAnalyzer(FIXED, log::add);
        RangeRiderConfig demanding = withMinimumFillRate("60");
        RangeRiderCandidate c = candidate("HALF", mixedSessions(), 4_000_000L, "100.00");

        assertTrue(analyzer.analyze(List.of(c), demanding).isEmpty());
        assertTrue(log.stream().anyMatch(line -> line.contains("Rejected HALF")
                && line.contains("50.00% of the last 10 sessions")));
    }

    @Test
    void rejectsARangeTooSmallToPayForARoundTrip() {
        List<String> log = new ArrayList<>();
        RangeRiderAnalyzer analyzer = new RangeRiderAnalyzer(FIXED, log::add);
        RangeRiderCandidate flat = steadyRange("FLAT", 15, "99.80", "100.20", "100", 4_000_000L, "100.00");

        assertTrue(analyzer.analyze(List.of(flat), RangeRiderConfig.defaults(StrategyMode.PAPER)).isEmpty());
        assertTrue(log.stream().anyMatch(line -> line.contains("Rejected FLAT")
                && line.contains("below the 1% minimum")));
    }

    @Test
    void rejectsARangeTooWideToPlanAgainst() {
        List<String> log = new ArrayList<>();
        RangeRiderAnalyzer analyzer = new RangeRiderAnalyzer(FIXED, log::add);
        RangeRiderCandidate wild = steadyRange("WILD", 15, "80", "120", "100", 4_000_000L, "100.00");

        assertTrue(analyzer.analyze(List.of(wild), RangeRiderConfig.defaults(StrategyMode.PAPER)).isEmpty());
        assertTrue(log.stream().anyMatch(line -> line.contains("Rejected WILD")
                && line.contains("too wide to plan against")));
    }

    @Test
    void entryTouchRateAndSameDayFillRateAreDistinctSignals() {
        RangeRiderAnalyzer analyzer = new RangeRiderAnalyzer(FIXED, null);
        RangeRiderConfig cfg = RangeRiderConfig.defaults(StrategyMode.PAPER);
        // Every session trades down to the planned dip, but only half run far enough back up.
        List<RangeRiderSession> sessions = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            sessions.add(session(i, "100", "105", "95", "100"));
        }
        for (int i = 5; i < 10; i++) {
            sessions.add(session(i, "100", "101", "95", "100"));
        }
        RangeRiderCandidate c = candidate("SPLIT", sessions, 4_000_000L, "100.00");

        assertEquals(new BigDecimal("100.00"), analyzer.entryTouchRatePercent(c, cfg));
        assertEquals(new BigDecimal("50.00"), analyzer.sameDayFillRatePercent(c, cfg));
    }

    @Test
    void fillRateIgnoresDriftBecauseEachSessionIsMeasuredAgainstItsOwnOpen() {
        RangeRiderAnalyzer analyzer = new RangeRiderAnalyzer(FIXED, null);
        RangeRiderConfig cfg = RangeRiderConfig.defaults(StrategyMode.PAPER);
        // Identical 2%-dip / 2%-rally shape every day, but the price ladders up ~2% per session.
        List<RangeRiderSession> sessions = new ArrayList<>();
        double open = 100;
        for (int i = 0; i < 15; i++) {
            sessions.add(new RangeRiderSession(LocalDate.of(2026, 5, 1).plusDays(i),
                    price(open), price(open * 1.02), price(open * 0.98), price(open)));
            open *= 1.02;
        }
        RangeRiderCandidate c = candidate("LADDER", sessions, 4_000_000L, "130.00");

        assertEquals(new BigDecimal("100.00"), analyzer.sameDayFillRatePercent(c, cfg),
                "a steady daily shape stays fully tradeable no matter how far the price has travelled");
    }

    @Test
    void rejectsIlliquidNamesBelowTheVolumeFloor() {
        List<String> log = new ArrayList<>();
        RangeRiderAnalyzer analyzer = new RangeRiderAnalyzer(FIXED, log::add);
        RangeRiderCandidate thin = steadyRange("THIN", 15, "98", "102", "100", 50_000L, "100.00");

        assertTrue(analyzer.analyze(List.of(thin), RangeRiderConfig.defaults(StrategyMode.PAPER)).isEmpty());
        assertTrue(log.stream().anyMatch(line -> line.contains("Rejected THIN")
                && line.contains("average volume below minimum")));
    }

    @Test
    void rejectsPricesOutsideTheConfiguredBounds() {
        RangeRiderAnalyzer analyzer = new RangeRiderAnalyzer(FIXED, null);
        RangeRiderConfig capped = new RangeRiderConfig(15, new BigDecimal("1"), new BigDecimal("12"),
                new BigDecimal("50"), 1_000_000L, new BigDecimal("10"), new BigDecimal("50"),
                new BigDecimal("50"), new BigDecimal("0.5"), new BigDecimal("2"), 10,
                RangeRiderConfig.ExecutionFrequency.MANUAL, StrategyMode.PAPER);
        RangeRiderCandidate pricey = steadyRange("RICH", 15, "98", "102", "100", 4_000_000L, "100.00");

        assertTrue(analyzer.analyze(List.of(pricey), capped).isEmpty());
    }

    @Test
    void sortsByScoreAndHonorsMaxStocksToAdd() {
        RangeRiderAnalyzer analyzer = new RangeRiderAnalyzer(FIXED, null);
        RangeRiderConfig cfg = new RangeRiderConfig(15, new BigDecimal("1"), new BigDecimal("12"),
                new BigDecimal("50"), 1_000_000L, new BigDecimal("10"), null, new BigDecimal("50"),
                new BigDecimal("0.5"), new BigDecimal("2"), 1,
                RangeRiderConfig.ExecutionFrequency.MANUAL, StrategyMode.PAPER);
        // Same range shape; the deeper-volume name outscores the thinner one.
        RangeRiderCandidate liquid = steadyRange("BIG", 15, "98", "102", "100", 8_000_000L, "100.00");
        RangeRiderCandidate lighter = steadyRange("SMALL", 15, "98", "102", "100", 1_200_000L, "100.00");

        List<RangeRiderRecommendation> result = analyzer.analyze(List.of(lighter, liquid), cfg);

        assertEquals(1, result.size(), "maxStocksToAdd caps the list");
        assertEquals("BIG", result.getFirst().symbol());
    }

    @Test
    void emptyAndNullInputsProduceNoRecommendations() {
        RangeRiderAnalyzer analyzer = new RangeRiderAnalyzer(FIXED, null);
        assertTrue(analyzer.analyze(null, RangeRiderConfig.defaults(StrategyMode.PAPER)).isEmpty());
        assertTrue(analyzer.analyze(List.of(), null).isEmpty());
    }

    // ---------------------------------------------------------------------
    // Helpers: build candidates whose averages are derived from their sessions,
    // exactly as RangeRiderLiveScanner does, so the replayed plan stays honest.

    /** Five sessions wide enough to complete the round trip, five that never reach the dip. */
    private static List<RangeRiderSession> mixedSessions() {
        List<RangeRiderSession> sessions = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            sessions.add(session(i, "100", "105", "95", "100"));
        }
        for (int i = 5; i < 10; i++) {
            sessions.add(session(i, "100", "100.50", "100", "100"));
        }
        return sessions;
    }

    private static RangeRiderConfig withMinimumFillRate(String minimum) {
        RangeRiderConfig d = RangeRiderConfig.defaults(StrategyMode.PAPER);
        return new RangeRiderConfig(d.lookbackSessions(), d.minimumAverageRangePercent(),
                d.maximumAverageRangePercent(), new BigDecimal(minimum), d.minimumAverageVolume(),
                d.minimumStockPrice(), d.maximumStockPrice(), d.targetCapturePercent(), d.minimumExpectedGainPercent(),
                d.stopLossPercent(), d.maxStocksToAdd(), d.executionFrequency(), d.mode(), List.of());
    }

    private static RangeRiderCandidate steadyRange(String symbol, int sessionCount, String low, String high,
                                                   String open, long averageVolume, String referencePrice) {
        List<RangeRiderSession> sessions = new ArrayList<>();
        for (int i = 0; i < sessionCount; i++) {
            sessions.add(session(i, open, high, low, open));
        }
        return candidate(symbol, sessions, averageVolume, referencePrice);
    }

    private static RangeRiderSession session(int dayOffset, String open, String high, String low, String close) {
        return new RangeRiderSession(LocalDate.of(2026, 5, 1).plusDays(dayOffset),
                new BigDecimal(open), new BigDecimal(high), new BigDecimal(low), new BigDecimal(close));
    }

    private static BigDecimal price(double value) {
        return BigDecimal.valueOf(Math.round(value * 100) / 100.0);
    }

    private static RangeRiderCandidate candidate(String symbol, List<RangeRiderSession> sessions,
                                                 long averageVolume, String referencePrice) {
        BigDecimal averageOpen = mean(sessions.stream().map(RangeRiderSession::open).toList());
        BigDecimal averageHigh = mean(sessions.stream().map(RangeRiderSession::high).toList());
        BigDecimal averageLow = mean(sessions.stream().map(RangeRiderSession::low).toList());
        BigDecimal averageRange = mean(sessions.stream().map(RangeRiderSession::rangePercent).toList())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal dip = averageOpen.subtract(averageLow)
                .multiply(BigDecimal.valueOf(100)).divide(averageOpen, 4, RoundingMode.HALF_UP);
        BigDecimal rally = averageHigh.subtract(averageOpen)
                .multiply(BigDecimal.valueOf(100)).divide(averageOpen, 4, RoundingMode.HALF_UP);
        return new RangeRiderCandidate(symbol, symbol, new BigDecimal(referencePrice),
                averageOpen.setScale(2, RoundingMode.HALF_UP), averageHigh.setScale(2, RoundingMode.HALF_UP),
                averageLow.setScale(2, RoundingMode.HALF_UP), averageRange, dip, rally,
                new BigDecimal("90.00"), new BigDecimal("100.00"), BigDecimal.ZERO,
                averageVolume, BigDecimal.ONE, sessions);
    }

    private static BigDecimal mean(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 6, RoundingMode.HALF_UP);
    }
}
