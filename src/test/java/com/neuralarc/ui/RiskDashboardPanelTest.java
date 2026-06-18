package com.neuralarc.ui;

import com.neuralarc.analytics.RiskAnalytics;
import com.neuralarc.service.ReconciliationService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke test: the chart-based dashboard panel assembles from real {@link RiskAnalytics} output
 * without throwing and reports a sane preferred size. Runs headless (no dialog/window is created).
 */
class RiskDashboardPanelTest {
    @Test
    void buildsFromAnalyticsOutputWithoutThrowing() {
        List<RiskAnalytics.Holding> holdings = List.of(
                new RiskAnalytics.Holding("NVDA", "ORB Engine", new BigDecimal("1150.00"), new BigDecimal("60.00")),
                new RiskAnalytics.Holding("AAPL", "Dip Hunter", new BigDecimal("980.00"), new BigDecimal("-40.00")),
                new RiskAnalytics.Holding("TSLA", "Gap Rocket", new BigDecimal("500.00"), new BigDecimal("90.00")));
        RiskAnalytics.Report report = RiskAnalytics.analyze(holdings);

        List<RiskAnalytics.PositionInput> inputs = List.of(
                new RiskAnalytics.PositionInput("NVDA", "ORB Engine", new BigDecimal("10"),
                        new BigDecimal("110"), new BigDecimal("115"), new BigDecimal("100"), new BigDecimal("130")),
                new RiskAnalytics.PositionInput("AAPL", "Dip Hunter", new BigDecimal("10"),
                        new BigDecimal("100"), new BigDecimal("96"), new BigDecimal("90"), new BigDecimal("120")),
                new RiskAnalytics.PositionInput("TSLA", "Gap Rocket", new BigDecimal("5"),
                        new BigDecimal("100"), new BigDecimal("89"), new BigDecimal("90"), new BigDecimal("130")));
        List<RiskAnalytics.PositionRisk> risks = RiskAnalytics.classify(inputs);

        ReconciliationService.Report reconciliation = new ReconciliationService().reconcile(
                List.of(new ReconciliationService.SymbolPosition("NVDA", new BigDecimal("10"), new BigDecimal("110"))),
                List.of(new ReconciliationService.SymbolPosition("NVDA", new BigDecimal("10"), new BigDecimal("110"))));

        RiskDashboardPanel panel = new RiskDashboardPanel("Paper", report, risks, reconciliation);

        assertNotNull(panel);
        assertTrue(panel.getComponentCount() > 0, "dashboard should assemble sections");
        assertTrue(panel.getPreferredSize().height > 0, "dashboard should report a height");
    }

    @Test
    void buildsWithEmptyDataWithoutThrowing() {
        RiskAnalytics.Report report = RiskAnalytics.analyze(List.of());
        ReconciliationService.Report reconciliation = new ReconciliationService().reconcile(List.of(), List.of());
        RiskDashboardPanel panel = new RiskDashboardPanel("Live", report, List.of(), reconciliation);
        assertNotNull(panel);
        assertTrue(panel.getComponentCount() > 0);
    }
}
