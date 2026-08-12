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
        assertEquals(new BigDecimal("2"), cfg.minimumAverageRangePercent());
        assertEquals(new BigDecimal("12"), cfg.maximumAverageRangePercent());
        assertEquals(new BigDecimal("60"), cfg.minimumSameDayFillRatePercent());
        assertEquals(1_000_000L, cfg.minimumAverageVolume());
        assertEquals(new BigDecimal("10"), cfg.minimumStockPrice());
        assertNull(cfg.maximumStockPrice());
        assertEquals(new BigDecimal("0.25"), cfg.entryBufferPercent());
        assertEquals(new BigDecimal("0.25"), cfg.exitBufferPercent());
        assertEquals(new BigDecimal("2"), cfg.stopLossPercent());
        assertEquals(StrategyMode.LIVE, cfg.mode());
    }

    @Test
    void rejectedValuesResetToDefaults() {
        RangeRiderConfig cfg = new RangeRiderConfig(-3, new BigDecimal("-1"), new BigDecimal("0"),
                new BigDecimal("-5"), -10L, new BigDecimal("0"), null, new BigDecimal("-1"),
                new BigDecimal("-1"), new BigDecimal("-2"), -4, null, StrategyMode.PAPER);
        assertEquals(15, cfg.lookbackSessions());
        assertEquals(new BigDecimal("2"), cfg.minimumAverageRangePercent());
        assertEquals(new BigDecimal("12"), cfg.maximumAverageRangePercent());
        assertEquals(new BigDecimal("60"), cfg.minimumSameDayFillRatePercent());
        assertEquals(1_000_000L, cfg.minimumAverageVolume());
        assertEquals(new BigDecimal("0.25"), cfg.entryBufferPercent());
        assertEquals(new BigDecimal("2"), cfg.stopLossPercent());
        assertEquals(10, cfg.maxStocksToAdd());
    }

    @Test
    void zeroBuffersAreHonoredRatherThanReplacedByDefaults() {
        RangeRiderConfig cfg = new RangeRiderConfig(15, new BigDecimal("2"), new BigDecimal("12"),
                BigDecimal.ZERO, 1_000_000L, new BigDecimal("10"), null, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("2"), 10, RangeRiderConfig.ExecutionFrequency.MANUAL, StrategyMode.PAPER);
        assertEquals(BigDecimal.ZERO, cfg.entryBufferPercent());
        assertEquals(BigDecimal.ZERO, cfg.exitBufferPercent());
        assertEquals(BigDecimal.ZERO, cfg.minimumSameDayFillRatePercent());
    }

    @Test
    void plansTheBuyAboveTheAverageLowAndTheSellBelowTheAverageHigh() {
        RangeRiderAnalyzer analyzer = new RangeRiderAnalyzer(FIXED, null);
        RangeRiderConfig cfg = RangeRiderConfig.defaults(StrategyMode.PAPER);
        RangeRiderCandidate c = steadyRange("NVDA", 15, "98", "102", "100", 4_000_000L, "98.00");

        BigDecimal entry = analyzer.plannedEntryPrice(c, cfg);
        BigDecimal target = analyzer.plannedTargetPrice(c, cfg);

        assertEquals(new BigDecimal("98.25"), entry, "entry sits 0.25% above the $98.00 average low");
        assertEquals(new BigDecimal("101.75"), target, "target sits 0.25% below the $102.00 average high");
        assertTrue(target.compareTo(entry) > 0);
    }

    @Test
    void averagesTheDailyOpenHighAndLowAcrossTheLookbackWindow() {
        RangeRiderCandidate c = steadyRange("NVDA", 15, "98", "102", "100", 4_000_000L, "98.00");

        assertEquals(15, c.sessionsAnalyzed());
        assertEquals(new BigDecimal("98.00"), c.averageLow());
        assertEquals(new BigDecimal("102.00"), c.averageHigh());
        assertEquals(new BigDecimal("100.00"), c.averageOpen());
    }

    @Test
    void recommendsAConsistentDailyRangeAndReportsAFullFillRate() {
        List<String> log = new ArrayList<>();
        RangeRiderAnalyzer analyzer = new RangeRiderAnalyzer(FIXED, log::add);
        RangeRiderCandidate c = steadyRange("NVDA", 15, "98", "102", "100", 4_000_000L, "98.00");

        List<RangeRiderRecommendation> result = analyzer.analyze(List.of(c), RangeRiderConfig.defaults(StrategyMode.PAPER));

        assertEquals(1, result.size());
        RangeRiderRecommendation r = result.getFirst();
        assertEquals("NVDA", r.symbol());
        assertEquals(new BigDecimal("98.25"), r.plannedEntryPrice());
        assertEquals(new BigDecimal("101.75"), r.targetPrice());
        assertEquals(new BigDecimal("3.56"), r.expectedGainPercent());
        assertEquals(new BigDecimal("100.00"), r.sameDayFillRatePercent());
        assertEquals(new BigDecimal("100.00"), r.entryTouchRatePercent());
        assertEquals(15, r.sessionsAnalyzed());
        // Stop loss is 2% below the planned entry, not below the average low.
        assertEquals(new BigDecimal("96.29"), r.stopLossPrice());
        assertTrue(r.strategyScore() >= RangeRiderAnalyzer.MINIMUM_RECOMMENDATION_SCORE);
        assertEquals(StrategyMode.PAPER, r.mode());
        assertEquals(RangeRiderStatus.RECOMMENDED, r.status());
    }

    @Test
    void rejectsARangeTooSmallToPayForARoundTrip() {
        List<String> log = new ArrayList<>();
        RangeRiderAnalyzer analyzer = new RangeRiderAnalyzer(FIXED, log::add);
        RangeRiderCandidate flat = steadyRange("FLAT", 15, "99.50", "100.50", "100", 4_000_000L, "99.50");

        assertTrue(analyzer.analyze(List.of(flat), RangeRiderConfig.defaults(StrategyMode.PAPER)).isEmpty());
        assertTrue(log.stream().anyMatch(line -> line.contains("Rejected FLAT")
                && line.contains("average daily range too small")));
    }

    @Test
    void rejectsARangeTooWideToPlanAgainst() {
        List<String> log = new ArrayList<>();
        RangeRiderAnalyzer analyzer = new RangeRiderAnalyzer(FIXED, log::add);
        RangeRiderCandidate wild = steadyRange("WILD", 15, "80", "120", "100", 4_000_000L, "80.00");

        assertTrue(analyzer.analyze(List.of(wild), RangeRiderConfig.defaults(StrategyMode.PAPER)).isEmpty());
        assertTrue(log.stream().anyMatch(line -> line.contains("Rejected WILD")
                && line.contains("average daily range too wide")));
    }

    @Test
    void rejectsWhenTheSameDayRoundTripRarelyCompleted() {
        List<String> log = new ArrayList<>();
        RangeRiderAnalyzer analyzer = new RangeRiderAnalyzer(FIXED, log::add);
        // Five wide sessions reach both planned prices; five narrow ones never trade down to the entry.
        List<RangeRiderSession> sessions = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            sessions.add(session(i, "100", "105", "95", "100"));
        }
        for (int i = 5; i < 10; i++) {
            sessions.add(session(i, "100", "100.50", "100", "100"));
        }
        RangeRiderCandidate c = candidate("HALF", sessions, 4_000_000L, "97.00");

        assertTrue(analyzer.analyze(List.of(c), RangeRiderConfig.defaults(StrategyMode.PAPER)).isEmpty());
        assertTrue(log.stream().anyMatch(line -> line.contains("Rejected HALF")
                && line.contains("50.00% of the last 10 sessions")));
    }

    @Test
    void entryTouchRateAndSameDayFillRateAreDistinctSignals() {
        RangeRiderAnalyzer analyzer = new RangeRiderAnalyzer(FIXED, null);
        RangeRiderConfig cfg = RangeRiderConfig.defaults(StrategyMode.PAPER);
        // Every session trades down to the entry, but only half of them run far enough back up.
        List<RangeRiderSession> sessions = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            sessions.add(session(i, "100", "105", "95", "100"));
        }
        for (int i = 5; i < 10; i++) {
            sessions.add(session(i, "100", "101", "95", "100"));
        }
        RangeRiderCandidate c = candidate("SPLIT", sessions, 4_000_000L, "95.00");

        BigDecimal entry = analyzer.plannedEntryPrice(c, cfg);
        BigDecimal target = analyzer.plannedTargetPrice(c, cfg);

        assertEquals(new BigDecimal("100.00"), analyzer.entryTouchRatePercent(c, entry));
        assertEquals(new BigDecimal("50.00"), analyzer.sameDayFillRatePercent(c, entry, target));
    }

    @Test
    void rejectsIlliquidNamesBelowTheVolumeFloor() {
        List<String> log = new ArrayList<>();
        RangeRiderAnalyzer analyzer = new RangeRiderAnalyzer(FIXED, log::add);
        RangeRiderCandidate thin = steadyRange("THIN", 15, "98", "102", "100", 50_000L, "98.00");

        assertTrue(analyzer.analyze(List.of(thin), RangeRiderConfig.defaults(StrategyMode.PAPER)).isEmpty());
        assertTrue(log.stream().anyMatch(line -> line.contains("Rejected THIN")
                && line.contains("average volume below minimum")));
    }

    @Test
    void rejectsPricesOutsideTheConfiguredBounds() {
        RangeRiderAnalyzer analyzer = new RangeRiderAnalyzer(FIXED, null);
        RangeRiderConfig capped = new RangeRiderConfig(15, new BigDecimal("2"), new BigDecimal("12"),
                new BigDecimal("60"), 1_000_000L, new BigDecimal("10"), new BigDecimal("50"),
                new BigDecimal("0.25"), new BigDecimal("0.25"), new BigDecimal("2"), 10,
                RangeRiderConfig.ExecutionFrequency.MANUAL, StrategyMode.PAPER);
        RangeRiderCandidate pricey = steadyRange("RICH", 15, "98", "102", "100", 4_000_000L, "98.00");

        assertTrue(analyzer.analyze(List.of(pricey), capped).isEmpty());
    }

    @Test
    void sortsByScoreAndHonorsMaxStocksToAdd() {
        RangeRiderAnalyzer analyzer = new RangeRiderAnalyzer(FIXED, null);
        RangeRiderConfig cfg = new RangeRiderConfig(15, new BigDecimal("2"), new BigDecimal("12"),
                new BigDecimal("60"), 1_000_000L, new BigDecimal("10"), null, new BigDecimal("0.25"),
                new BigDecimal("0.25"), new BigDecimal("2"), 1,
                RangeRiderConfig.ExecutionFrequency.MANUAL, StrategyMode.PAPER);
        // Same range shape; the deeper-volume name outscores the thinner one.
        RangeRiderCandidate liquid = steadyRange("BIG", 15, "98", "102", "100", 8_000_000L, "98.00");
        RangeRiderCandidate lighter = steadyRange("SMALL", 15, "98", "102", "100", 1_200_000L, "98.00");

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

    private static RangeRiderCandidate steadyRange(String symbol, int sessionCount, String low, String high,
                                                   String open, long averageVolume, String currentPrice) {
        List<RangeRiderSession> sessions = new ArrayList<>();
        for (int i = 0; i < sessionCount; i++) {
            sessions.add(session(i, open, high, low, open));
        }
        return candidate(symbol, sessions, averageVolume, currentPrice);
    }

    private static RangeRiderSession session(int dayOffset, String open, String high, String low, String close) {
        return new RangeRiderSession(LocalDate.of(2026, 5, 1).plusDays(dayOffset),
                new BigDecimal(open), new BigDecimal(high), new BigDecimal(low), new BigDecimal(close));
    }

    private static RangeRiderCandidate candidate(String symbol, List<RangeRiderSession> sessions,
                                                 long averageVolume, String currentPrice) {
        BigDecimal averageOpen = mean(sessions.stream().map(RangeRiderSession::open).toList());
        BigDecimal averageHigh = mean(sessions.stream().map(RangeRiderSession::high).toList());
        BigDecimal averageLow = mean(sessions.stream().map(RangeRiderSession::low).toList());
        BigDecimal averageRange = mean(sessions.stream().map(RangeRiderSession::rangePercent).toList())
                .setScale(2, RoundingMode.HALF_UP);
        return new RangeRiderCandidate(symbol, symbol, new BigDecimal(currentPrice),
                averageOpen.setScale(2, RoundingMode.HALF_UP), averageHigh.setScale(2, RoundingMode.HALF_UP),
                averageLow.setScale(2, RoundingMode.HALF_UP), averageRange, new BigDecimal("90.00"),
                new BigDecimal("100.00"), BigDecimal.ZERO, averageVolume, BigDecimal.ONE, sessions);
    }

    private static BigDecimal mean(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 6, RoundingMode.HALF_UP);
    }
}
