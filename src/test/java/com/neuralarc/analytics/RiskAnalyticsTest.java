package com.neuralarc.analytics;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
