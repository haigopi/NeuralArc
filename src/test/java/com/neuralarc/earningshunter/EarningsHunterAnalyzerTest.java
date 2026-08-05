package com.neuralarc.earningshunter;

import com.neuralarc.model.NewsArticle;
import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarningsHunterAnalyzerTest {
    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-08-04T14:00:00Z"), ZoneOffset.UTC);

    @Test
    void recommendsLiquidSymbolsWithFreshEarningsCatalyst() {
        EarningsHunterAnalyzer analyzer = new EarningsHunterAnalyzer(FIXED, null);
        List<EarningsHunterRecommendation> result = analyzer.analyze(List.of(candidate("NVDA")),
                EarningsHunterConfig.defaults(StrategyMode.PAPER));

        assertEquals(1, result.size());
        EarningsHunterRecommendation recommendation = result.getFirst();
        assertEquals("NVDA", recommendation.symbol());
        assertEquals(new BigDecimal("95.00"), recommendation.plannedEntryPrice());
        assertEquals(new BigDecimal("90.25"), recommendation.stopLossPrice());
        assertEquals(new BigDecimal("104.50"), recommendation.targetPrice());
        assertTrue(recommendation.strategyScore() >= EarningsHunterAnalyzer.MINIMUM_RECOMMENDATION_SCORE);
    }

    @Test
    void plannedEntryNeverChasesAboveCurrentPrice() {
        EarningsHunterAnalyzer analyzer = new EarningsHunterAnalyzer(FIXED, null);
        EarningsHunterRecommendation recommendation = analyzer.analyze(List.of(candidate("NVDA")),
                EarningsHunterConfig.defaults(StrategyMode.PAPER)).getFirst();

        assertTrue(recommendation.plannedEntryPrice().compareTo(recommendation.currentPrice()) < 0);
    }

    @Test
    void plannedEntryUsesMultiMonthDailyLowSupportWhenLowerThanFlatDiscount() {
        EarningsHunterAnalyzer analyzer = new EarningsHunterAnalyzer(FIXED, null);
        EarningsHunterCandidate candidate = new EarningsHunterCandidate("NVDA", "NVDA",
                new BigDecimal("100.00"), new BigDecimal("96.00"), new BigDecimal("4.17"),
                1_000_000L, new BigDecimal("1.50"), new BigDecimal("90.00"), new BigDecimal("82.00"),
                new BigDecimal("75.00"), new BigDecimal("85.00"),
                List.of(article("Company reports Q2 earnings beat", "Revenue above guidance")), Instant.now(FIXED));

        EarningsHunterRecommendation recommendation = analyzer.analyze(List.of(candidate),
                EarningsHunterConfig.defaults(StrategyMode.PAPER)).getFirst();

        assertEquals(new BigDecimal("85.00"), recommendation.plannedEntryPrice());
    }

    @Test
    void rejectsWhenCatalystScoreBelowConfiguredMinimum() {
        EarningsHunterAnalyzer analyzer = new EarningsHunterAnalyzer(FIXED, null);
        EarningsHunterConfig strict = new EarningsHunterConfig(7, 100_000L, new BigDecimal("0.5"),
                new BigDecimal("0.5"), null, new BigDecimal("90"), new BigDecimal("2"), new BigDecimal("5"),
                new BigDecimal("10"), 10, StrategyMode.PAPER, List.of());

        assertTrue(analyzer.analyze(List.of(candidate("LOW")), strict).isEmpty());
    }

    @Test
    void detectsEarningsTermsInNews() {
        assertTrue(EarningsHunterLiveScanner.containsEarningsTerm(article("Q2 earnings beat", "")));
        assertFalse(EarningsHunterLiveScanner.containsEarningsTerm(article("New office opens", "General update")));
    }

    private EarningsHunterCandidate candidate(String symbol) {
        return new EarningsHunterCandidate(symbol, symbol, new BigDecimal("100.00"), new BigDecimal("96.00"),
                new BigDecimal("4.17"), 1_000_000L, new BigDecimal("1.50"),
                new BigDecimal("96.00"), new BigDecimal("93.00"), new BigDecimal("90.00"), new BigDecimal("95.00"),
                List.of(article("Company reports Q2 earnings beat", "Revenue above guidance")), Instant.now(FIXED));
    }

    private static NewsArticle article(String headline, String summary) {
        return new NewsArticle(headline, summary, "benzinga", "https://example.com", Instant.now(FIXED), List.of("NVDA"));
    }
}
