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
        assertEquals(new BigDecimal("5"), cfg.minimumPremarketGapPercent());
        assertEquals(1_000_000L, cfg.minimumPremarketVolume());
        assertEquals(new BigDecimal("5"), cfg.minimumStockPrice());
        assertEquals(new BigDecimal("2"), cfg.minimumRelativeVolume());
        assertNull(cfg.maximumStockPrice());
        assertTrue(cfg.newsCatalystRequired());
        assertEquals(GapRocketConfig.EntryStyle.BREAKOUT_RETEST, cfg.entryStyle());
        assertEquals(GapRocketConfig.OpeningRangeDuration.FIFTEEN_MINUTES, cfg.openingRangeDuration());
        assertEquals(StrategyMode.LIVE, cfg.mode());
    }

    @Test
    void analyzeAddsOnlyRecommendationsAtOrAboveThresholdSortedByScore() {
        List<String> log = new ArrayList<>();
        GapRocketAnalyzer analyzer = new GapRocketAnalyzer(Clock.fixed(Instant.parse("2026-06-13T13:45:15Z"), ZoneOffset.UTC), log::add);
        List<GapRocketRecommendation> result = analyzer.analyze(List.of(strong("NVDA"), weak("ABC")), GapRocketConfig.defaults(StrategyMode.PAPER));
        assertEquals(1, result.size());
        assertEquals("NVDA", result.getFirst().symbol());
        assertTrue(result.getFirst().strategyScore() >= 70);
        assertEquals(StrategyMode.PAPER, result.getFirst().mode());
        assertTrue(log.stream().anyMatch(line -> line.contains("Rejected ABC")));
    }

    @Test
    void paperAndLiveRecommendationsAreIsolatedByConfigMode() {
        GapRocketAnalyzer analyzer = new GapRocketAnalyzer(Clock.systemUTC(), null);
        assertEquals(StrategyMode.PAPER, analyzer.analyze(List.of(strong("TSLA")), GapRocketConfig.defaults(StrategyMode.PAPER)).getFirst().mode());
        assertEquals(StrategyMode.LIVE, analyzer.analyze(List.of(strong("TSLA")), GapRocketConfig.defaults(StrategyMode.LIVE)).getFirst().mode());
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
}
