package com.neuralarc.analytics;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskAnalyticsTest {
    private RiskAnalytics.Holding holding(String symbol, String workspace, String value, String pnl) {
        return new RiskAnalytics.Holding(symbol, workspace, new BigDecimal(value), new BigDecimal(pnl));
    }

    @Test
    void computesExposureConcentrationAndWinners() {
        List<RiskAnalytics.Holding> holdings = List.of(
                holding("NVDA", "ORB Engine", "1200.00", "150.00"),
                holding("NVDA", "VWAP Desk", "600.00", "-40.00"),   // same symbol, different workspace
                holding("AAPL", "ORB Engine", "200.00", "-90.00")
        );
        RiskAnalytics.Report report = RiskAnalytics.analyze(holdings);

        assertEquals(new BigDecimal("2000.00"), report.totalCapital());
        // NVDA exposure aggregated across workspaces = 1800 of 2000 = 90%
        assertEquals("NVDA", report.exposureBySymbol().get(0).key());
        assertEquals(90.0, report.exposureBySymbol().get(0).percentOfTotal(), 0.001);
        assertEquals(90.0, report.topSymbolConcentrationPercent(), 0.001);
        // Largest winner = NVDA (150 - 40 = 110), largest loser = AAPL (-90)
        assertEquals("NVDA", report.largestWinnerSymbol());
        assertEquals(new BigDecimal("110.00"), report.largestWinnerPnl());
        assertEquals("AAPL", report.largestLoserSymbol());
        assertEquals(new BigDecimal("-90.00"), report.largestLoserPnl());
    }

    @Test
    void ranksWorkspacesByPnl() {
        List<RiskAnalytics.Holding> holdings = List.of(
                holding("NVDA", "ORB Engine", "1000.00", "120.00"),
                holding("AAPL", "VWAP Desk", "500.00", "300.00")
        );
        RiskAnalytics.Report report = RiskAnalytics.analyze(holdings);
        assertEquals("VWAP Desk", report.workspacePnlRanking().get(0).key());   // higher pnl first
        assertEquals("ORB Engine", report.workspacePnlRanking().get(1).key());
    }

    @Test
    void emptyHoldingsProduceZeroes() {
        RiskAnalytics.Report report = RiskAnalytics.analyze(List.of());
        assertEquals(new BigDecimal("0.00"), report.totalCapital());
        assertEquals(0.0, report.topSymbolConcentrationPercent(), 0.001);
        assertEquals(0, report.exposureBySymbol().size());
    }

    private RiskAnalytics.PositionInput position(String symbol, String entry, String current, String stop, String target) {
        return new RiskAnalytics.PositionInput(symbol, "ORB Engine", new BigDecimal("10"),
                new BigDecimal(entry), new BigDecimal(current),
                stop == null ? null : new BigDecimal(stop),
                target == null ? null : new BigDecimal(target));
    }

    @Test
    void classifiesUnderwaterAboveStopAsWaitToBookLoss() {
        // Entry 100, now 96, stop 90 → losing but above the stop → hold.
        RiskAnalytics.PositionRisk risk = RiskAnalytics.classify(
                List.of(position("NVDA", "100", "96", "90", "120"))).get(0);
        assertEquals(RiskAnalytics.RiskVerdict.WAIT_TO_BOOK_LOSS, risk.verdict());
        assertEquals(new BigDecimal("-40.00"), risk.unrealizedPnl());      // (96-100)*10
        assertEquals(-4.0, risk.unrealizedPercent(), 0.001);
        assertTrue(risk.advice().toLowerCase().contains("wait to book"));
    }

    @Test
    void classifiesAtOrBelowStopAsCutLoss() {
        // Now 89 with stop 90 → stop breached.
        RiskAnalytics.PositionRisk risk = RiskAnalytics.classify(
                List.of(position("AMD", "100", "89", "90", "120"))).get(0);
        assertEquals(RiskAnalytics.RiskVerdict.CUT_LOSS, risk.verdict());
        assertTrue(risk.advice().toLowerCase().contains("booking the loss"));
    }

    @Test
    void classifiesThinWinnerAsProtectGains() {
        // Up only 1% above entry → cushion too thin → possible loser.
        RiskAnalytics.PositionRisk risk = RiskAnalytics.classify(
                List.of(position("AAPL", "100", "101", "92", "130"))).get(0);
        assertEquals(RiskAnalytics.RiskVerdict.PROTECT_GAINS, risk.verdict());
        assertEquals(1.0, risk.unrealizedPercent(), 0.001);
        assertTrue(risk.advice().toLowerCase().contains("protect gains"));
    }

    @Test
    void classifiesComfortableWinnerAsOnTrack() {
        RiskAnalytics.PositionRisk risk = RiskAnalytics.classify(
                List.of(position("TSLA", "100", "115", "92", "130"))).get(0);
        assertEquals(RiskAnalytics.RiskVerdict.ON_TRACK_WINNER, risk.verdict());
        assertEquals(15.0, risk.unrealizedPercent(), 0.001);
        // Distance to stop and target are reported for the gauge.
        assertEquals(20.0, risk.distanceToStopPercent(), 0.1);   // (115-92)/115
        assertEquals(13.04, risk.distanceToTargetPercent(), 0.1); // (130-115)/115
    }

    @Test
    void skipsPositionsWithNoSharesOrNoEntry() {
        List<RiskAnalytics.PositionRisk> risks = RiskAnalytics.classify(List.of(
                new RiskAnalytics.PositionInput("X", "W", BigDecimal.ZERO, new BigDecimal("10"),
                        new BigDecimal("11"), new BigDecimal("9"), new BigDecimal("12")),
                new RiskAnalytics.PositionInput("Y", "W", new BigDecimal("5"), BigDecimal.ZERO,
                        new BigDecimal("11"), new BigDecimal("9"), new BigDecimal("12"))));
        assertTrue(risks.isEmpty());
    }

    @Test
    void underwaterWithNoStopStillWaitsToBookLoss() {
        RiskAnalytics.PositionRisk risk = RiskAnalytics.classify(
                List.of(position("META", "100", "95", null, null))).get(0);
        assertEquals(RiskAnalytics.RiskVerdict.WAIT_TO_BOOK_LOSS, risk.verdict());
        assertEquals(0.0, risk.distanceToStopPercent(), 0.001);  // no stop → 0
        assertTrue(risk.advice().toLowerCase().contains("no protective stop"));
    }
}
