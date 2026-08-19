package com.neuralarc.profitshield;

import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfitShieldAnalyzerTest {
    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-06-15T18:00:00Z"), ZoneOffset.UTC);

    private ProfitShieldAnalyzer analyzer() {
        return new ProfitShieldAnalyzer(FIXED, ignored -> { });
    }

    private static ProfitShieldConfig defaults() {
        return ProfitShieldConfig.defaults(StrategyMode.PAPER);
    }

    /** A textbook defensive name: quiet, shallow drawdown, near its high, full trend stack. */
    private static ProfitShieldCandidate resilient(String symbol) {
        return new ProfitShieldCandidate(symbol, symbol,
                new BigDecimal("100.00"), new BigDecimal("99.50"), new BigDecimal("0.50"),
                2_000_000L, new BigDecimal("1.00"),
                new BigDecimal("98.00"), new BigDecimal("95.00"), new BigDecimal("88.00"),
                true, true, true,
                new BigDecimal("1.20"), new BigDecimal("7.50"), new BigDecimal("2.00"),
                new BigDecimal("58.00"), new BigDecimal("95.00"), 126);
    }

    private static ProfitShieldCandidate copyWith(ProfitShieldCandidate base, BigDecimal atrPercent,
                                                  BigDecimal maxDrawdownPercent, BigDecimal distanceFromHighPercent) {
        return new ProfitShieldCandidate(base.symbol(), base.companyName(), base.currentPrice(), base.previousClose(),
                base.dayChangePercent(), base.averageVolume(), base.relativeVolume(), base.ma20(), base.ma50(),
                base.ma200(), base.aboveMa50(), base.aboveMa200(), base.risingTrendStack(),
                atrPercent, maxDrawdownPercent, distanceFromHighPercent, base.upSessionsPercent(),
                base.supportPrice(), base.sessionsAnalyzed());
    }

    @Test
    void recommendsAQuietResilientNameInAnIntactTrend() {
        List<ProfitShieldRecommendation> recommendations = analyzer().analyze(List.of(resilient("MSFT")), defaults());

        assertEquals(1, recommendations.size());
        ProfitShieldRecommendation r = recommendations.getFirst();
        assertEquals("MSFT", r.symbol());
        assertEquals(ProfitShieldStatus.RECOMMENDED, r.status());
        assertEquals(StrategyMode.PAPER, r.mode());
        assertTrue(r.strategyScore() >= ProfitShieldAnalyzer.MINIMUM_RECOMMENDATION_SCORE);
        assertTrue(r.protectionScore() > 0 && r.protectionScore() <= 60);
    }

    @Test
    void rejectsNamesThatMoveTooHardEachDay() {
        ProfitShieldCandidate noisy = copyWith(resilient("WILD"), new BigDecimal("6.00"),
                new BigDecimal("7.50"), new BigDecimal("2.00"));

        assertFalse(analyzer().passesFilters(noisy, defaults()));
        assertTrue(analyzer().analyze(List.of(noisy), defaults()).isEmpty());
    }

    @Test
    void rejectsNamesThatHaveAlreadyGivenBackTooMuch() {
        ProfitShieldCandidate battered = copyWith(resilient("DEEP"), new BigDecimal("1.20"),
                new BigDecimal("45.00"), new BigDecimal("2.00"));

        assertFalse(analyzer().passesFilters(battered, defaults()));
    }

    @Test
    void rejectsNamesStillFarBelowTheirOwnLookbackHigh() {
        ProfitShieldCandidate repairing = copyWith(resilient("REPR"), new BigDecimal("1.20"),
                new BigDecimal("18.00"), new BigDecimal("30.00"));

        assertFalse(analyzer().passesFilters(repairing, defaults()));
    }

    @Test
    void rejectsNamesWhoseLongTermTrendIsBroken() {
        ProfitShieldCandidate base = resilient("BRKN");
        ProfitShieldCandidate belowMa200 = new ProfitShieldCandidate(base.symbol(), base.companyName(),
                base.currentPrice(), base.previousClose(), base.dayChangePercent(), base.averageVolume(),
                base.relativeVolume(), base.ma20(), base.ma50(), new BigDecimal("120.00"),
                true, false, false, base.atrPercent(), base.maxDrawdownPercent(),
                base.distanceFromHighPercent(), base.upSessionsPercent(), base.supportPrice(), base.sessionsAnalyzed());

        assertFalse(analyzer().passesFilters(belowMa200, defaults()));

        ProfitShieldConfig relaxed = new ProfitShieldConfig(126, new BigDecimal("3"), new BigDecimal("20"),
                new BigDecimal("12"), 300_000L, new BigDecimal("5"), null,
                ProfitShieldConfig.TrendFilter.DISABLED, new BigDecimal("1"), new BigDecimal("3"),
                new BigDecimal("6"), 10, StrategyMode.PAPER, List.of());
        assertTrue(analyzer().passesFilters(belowMa200, relaxed));
    }

    @Test
    void scoresQuieterShallowerNamesHigher() {
        ProfitShieldCandidate calm = copyWith(resilient("CALM"), new BigDecimal("0.80"),
                new BigDecimal("5.00"), new BigDecimal("1.00"));
        ProfitShieldCandidate choppy = copyWith(resilient("CHOP"), new BigDecimal("2.80"),
                new BigDecimal("19.00"), new BigDecimal("11.00"));

        assertTrue(analyzer().score(calm, defaults()) > analyzer().score(choppy, defaults()));
        assertTrue(analyzer().protectionScore(calm, defaults()) > analyzer().protectionScore(choppy, defaults()));
    }

    @Test
    void ranksHighestScoreFirstAndCapsTheAddCount() {
        List<ProfitShieldCandidate> candidates = new ArrayList<>();
        candidates.add(copyWith(resilient("AAA"), new BigDecimal("2.50"), new BigDecimal("18.00"), new BigDecimal("10.00")));
        candidates.add(copyWith(resilient("BBB"), new BigDecimal("0.80"), new BigDecimal("5.00"), new BigDecimal("1.00")));
        candidates.add(copyWith(resilient("CCC"), new BigDecimal("1.50"), new BigDecimal("10.00"), new BigDecimal("5.00")));
        ProfitShieldConfig topTwo = new ProfitShieldConfig(126, new BigDecimal("3"), new BigDecimal("20"),
                new BigDecimal("12"), 300_000L, new BigDecimal("5"), null,
                ProfitShieldConfig.TrendFilter.ABOVE_MA_50_AND_200, new BigDecimal("1"), new BigDecimal("3"),
                new BigDecimal("6"), 2, StrategyMode.PAPER, List.of());

        List<ProfitShieldRecommendation> recommendations = analyzer().analyze(candidates, topTwo);

        assertEquals(2, recommendations.size());
        assertEquals("BBB", recommendations.getFirst().symbol());
        assertTrue(recommendations.get(0).strategyScore() >= recommendations.get(1).strategyScore());
    }

    @Test
    void plansEntryBelowCurrentPriceWithATargetAboveIt() {
        ProfitShieldRecommendation r = analyzer().analyze(List.of(resilient("MSFT")), defaults()).getFirst();

        // 1% entry discount off $100.00, then a 6% target above that entry.
        assertEquals(new BigDecimal("99.00"), r.plannedEntryPrice());
        assertEquals(new BigDecimal("104.94"), r.targetPrice());
        assertTrue(r.plannedEntryPrice().compareTo(r.currentPrice()) < 0);
        assertTrue(r.targetPrice().compareTo(r.plannedEntryPrice()) > 0);
    }

    @Test
    void tightensTheStopToTheSupportShelfWhenItSitsNearerThanTheFlatStop() {
        // Entry $99.00; a flat 3% stop would be $96.03, but the shelf at $95.00 * 0.995 = $94.53 is
        // further away, so the flat stop wins — it is the tighter of the two.
        ProfitShieldRecommendation far = analyzer().analyze(List.of(resilient("MSFT")), defaults()).getFirst();
        assertEquals(new BigDecimal("96.03"), far.stopLossPrice());

        // Move the shelf up to $98.00: 98.00 * 0.995 = $97.51, tighter than the flat stop, and still
        // wider than the half-stop floor of $97.515 -> clamped to the floor.
        ProfitShieldCandidate base = resilient("MSFT");
        ProfitShieldCandidate nearShelf = new ProfitShieldCandidate(base.symbol(), base.companyName(),
                base.currentPrice(), base.previousClose(), base.dayChangePercent(), base.averageVolume(),
                base.relativeVolume(), base.ma20(), base.ma50(), base.ma200(), base.aboveMa50(), base.aboveMa200(),
                base.risingTrendStack(), base.atrPercent(), base.maxDrawdownPercent(), base.distanceFromHighPercent(),
                base.upSessionsPercent(), new BigDecimal("98.00"), base.sessionsAnalyzed());

        ProfitShieldRecommendation near = analyzer().analyze(List.of(nearShelf), defaults()).getFirst();

        assertTrue(near.stopLossPrice().compareTo(far.stopLossPrice()) > 0, "a nearer shelf tightens the stop");
        assertTrue(near.stopLossPrice().compareTo(near.plannedEntryPrice()) < 0, "the stop stays below the entry");
    }

    @Test
    void neverTightensTheStopCloserThanHalfTheConfiguredStop() {
        // A shelf sitting almost at the entry must not produce a whipsaw-width stop.
        ProfitShieldCandidate base = resilient("MSFT");
        ProfitShieldCandidate hugging = new ProfitShieldCandidate(base.symbol(), base.companyName(),
                base.currentPrice(), base.previousClose(), base.dayChangePercent(), base.averageVolume(),
                base.relativeVolume(), base.ma20(), base.ma50(), base.ma200(), base.aboveMa50(), base.aboveMa200(),
                base.risingTrendStack(), base.atrPercent(), base.maxDrawdownPercent(), base.distanceFromHighPercent(),
                base.upSessionsPercent(), new BigDecimal("99.90"), base.sessionsAnalyzed());

        ProfitShieldRecommendation r = analyzer().analyze(List.of(hugging), defaults()).getFirst();

        // Entry $99.00, half of the 3% stop = 1.5% -> $97.515 -> $97.52.
        assertEquals(new BigDecimal("97.52"), r.stopLossPrice());
        // $97.515 rounds up to $97.52, so the reported risk is 1.49% rather than exactly half of 3%.
        assertEquals(new BigDecimal("1.49"), r.stopLossPercent());
    }

    @Test
    void reportsTheEffectiveStopPercentThatMatchesThePlannedPrices() {
        ProfitShieldRecommendation r = analyzer().analyze(List.of(resilient("MSFT")), defaults()).getFirst();

        assertEquals(new BigDecimal("3.00"), r.stopLossPercent());
    }

    @Test
    void returnsNothingWithoutCandidates() {
        assertTrue(analyzer().analyze(List.of(), defaults()).isEmpty());
        assertTrue(analyzer().analyze(null, defaults()).isEmpty());
    }
}
