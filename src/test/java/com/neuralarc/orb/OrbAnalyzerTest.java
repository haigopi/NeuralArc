package com.neuralarc.orb;

import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrbAnalyzerTest {
    @Test
    void scoresCompleteRangeAndComputesPlannedPrices() {
        OrbAnalyzer analyzer = new OrbAnalyzer(Clock.fixed(Instant.parse("2026-06-15T14:00:00Z"), ZoneOffset.UTC), null);
        OrbConfig config = OrbConfig.defaults(StrategyMode.PAPER);
        OpeningRangeSnapshot snapshot = new OpeningRangeSnapshot("nvda", Instant.parse("2026-06-15T13:30:00Z"),
                Instant.parse("2026-06-15T13:45:00Z"), new BigDecimal("101.00"), new BigDecimal("99.00"),
                new BigDecimal("500000"), 15, true, "");

        List<OrbRecommendation> recommendations = analyzer.analyze(List.of(snapshot),
                List.of(new OrbCandidate("NVDA", new BigDecimal("101.25"), null, new BigDecimal("3.0"), null, new BigDecimal("0.20"), "manual")),
                config);

        assertEquals(1, recommendations.size());
        OrbRecommendation rec = recommendations.getFirst();
        assertEquals("NVDA", rec.symbol());
        assertEquals(new BigDecimal("96.19"), rec.plannedEntry());
        assertEquals(new BigDecimal("99.00"), rec.stop());
        assertEquals(new BigDecimal("99.08"), rec.target());
        assertTrue(rec.score() > 0);
        assertEquals(OrbStatus.RANGE_CAPTURED, rec.status());
    }

    @Test
    void plannedEntryCapsAtFivePercentBelowLowerOfOpenOrCurrentPrice() {
        OrbAnalyzer analyzer = new OrbAnalyzer(Clock.fixed(Instant.parse("2026-06-15T14:00:00Z"), ZoneOffset.UTC), null);
        OrbConfig config = OrbConfig.defaults(StrategyMode.PAPER);
        OpeningRangeSnapshot snapshot = new OpeningRangeSnapshot("aapl", Instant.parse("2026-06-15T13:30:00Z"),
                Instant.parse("2026-06-15T13:45:00Z"), new BigDecimal("101.00"), new BigDecimal("99.00"),
                new BigDecimal("500000"), 15, true, "");

        List<OrbRecommendation> recommendations = analyzer.analyze(List.of(snapshot),
                List.of(new OrbCandidate("AAPL", new BigDecimal("98.00"), new BigDecimal("100.00"),
                        new BigDecimal("3.0"), null, new BigDecimal("0.20"), "manual")),
                config);

        assertEquals(1, recommendations.size());
        assertEquals(new BigDecimal("93.10"), recommendations.getFirst().plannedEntry());
    }

    @Test
    void rejectsSnapshotsPastLatestEntryTime() {
        // Clock is 11:30 AM ET; latestEntryTimeEt defaults to 11:00 ET → should reject.
        Clock noonEt = Clock.fixed(Instant.parse("2026-06-15T15:30:00Z"), ZoneId.of("America/New_York"));
        List<String> log = new ArrayList<>();
        OrbAnalyzer analyzer = new OrbAnalyzer(noonEt, log::add);
        OpeningRangeSnapshot snapshot = new OpeningRangeSnapshot("TSLA", Instant.parse("2026-06-15T13:30:00Z"),
                Instant.parse("2026-06-15T13:45:00Z"), new BigDecimal("202.00"), new BigDecimal("198.00"),
                new BigDecimal("1000000"), 15, true, "");

        assertTrue(analyzer.analyze(List.of(snapshot), List.of(), OrbConfig.defaults(StrategyMode.PAPER)).isEmpty());
        assertTrue(log.stream().anyMatch(line -> line.contains("past latest entry time")));
    }

    @Test
    void acceptsSnapshotBeforeLatestEntryTime() {
        // Clock is 10:00 AM ET (14:00 UTC in EDT); latestEntryTimeEt defaults to 11:00 ET → should accept.
        Clock earlyEt = Clock.fixed(Instant.parse("2026-06-15T14:00:00Z"), ZoneId.of("America/New_York"));
        OrbAnalyzer analyzer = new OrbAnalyzer(earlyEt, null);
        OpeningRangeSnapshot snapshot = new OpeningRangeSnapshot("AAPL", Instant.parse("2026-06-15T13:30:00Z"),
                Instant.parse("2026-06-15T13:45:00Z"), new BigDecimal("150.00"), new BigDecimal("148.00"),
                new BigDecimal("800000"), 15, true, "");

        assertFalse(analyzer.analyze(List.of(snapshot), List.of(), OrbConfig.defaults(StrategyMode.PAPER)).isEmpty());
    }

    @Test
    void defaultConfigRejectedValuesResetToDefaults() {
        OrbConfig negativeInputs = new OrbConfig(15, new BigDecimal("-1"), null,
                new BigDecimal("-5"), new BigDecimal("0"), 10,
                new BigDecimal("0"), null, new BigDecimal("-2"), new BigDecimal("-0.5"),
                LocalTime.of(11, 0), List.of(), true, false, StrategyMode.PAPER);

        assertEquals(new BigDecimal("0.10"), negativeInputs.entryBufferPercent());
        assertEquals(new BigDecimal("1.00"), negativeInputs.riskPercent());
        assertEquals(new BigDecimal("3.00"), negativeInputs.takeProfitPercent());
        assertEquals(new BigDecimal("1.00"), negativeInputs.minimumPrice());
        assertEquals(new BigDecimal("1.50"), negativeInputs.minimumRelativeVolume());
        assertEquals(new BigDecimal("0.20"), negativeInputs.minimumRangePercent());
    }

    @Test
    void rejectsIncompleteRangesAndLogsReason() {
        List<String> log = new ArrayList<>();
        OrbAnalyzer analyzer = new OrbAnalyzer(Clock.systemUTC(), log::add);
        OpeningRangeSnapshot incomplete = new OpeningRangeSnapshot("AMD", Instant.now(), Instant.now(), null, null,
                BigDecimal.ZERO, 0, false, "missing opening-range bars");

        assertTrue(analyzer.analyze(List.of(incomplete), List.of(), OrbConfig.defaults(null)).isEmpty());
        assertTrue(log.getFirst().contains("missing opening-range bars"));
    }
}
