package com.neuralarc.orb;

import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
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
        assertEquals(new BigDecimal("101.10"), rec.plannedEntry());
        assertEquals(new BigDecimal("99.00"), rec.stop());
        assertEquals(new BigDecimal("104.13"), rec.target());
        assertTrue(rec.score() > 0);
        assertEquals(OrbStatus.RANGE_CAPTURED, rec.status());
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
