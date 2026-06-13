package com.neuralarc.ui;

import com.neuralarc.analytics.RiskAnalytics;
import com.neuralarc.service.ReconciliationService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskDashboardPresenterTest {
    private final RiskDashboardPresenter presenter = new RiskDashboardPresenter();

    @Test
    void rendersRiskFiguresAndReconciliationMismatches() {
        RiskAnalytics.Report risk = RiskAnalytics.analyze(List.of(
                new RiskAnalytics.Holding("NVDA", "ORB Engine", new BigDecimal("1800.00"), new BigDecimal("110.00")),
                new RiskAnalytics.Holding("AAPL", "VWAP Desk", new BigDecimal("200.00"), new BigDecimal("-90.00"))
        ));
        ReconciliationService.Report recon = new ReconciliationService().reconcile(
                List.of(new ReconciliationService.SymbolPosition("NVDA", new BigDecimal("15"), new BigDecimal("120.00"))),
                List.of(new ReconciliationService.SymbolPosition("NVDA", new BigDecimal("12"), new BigDecimal("120.00")))
        );

        String html = presenter.buildHtml("Paper", risk, recon);
        assertTrue(html.contains("Strategy Risk Dashboard — Paper"), html);
        assertTrue(html.contains("Capital allocated:"), html);
        assertTrue(html.contains("NVDA"), html);
        assertTrue(html.contains("over-concentrated"), html); // 90% top-symbol concentration
        assertTrue(html.contains("mismatch"), html);
        assertTrue(html.contains("never auto-corrects"), html);
    }

    @Test
    void rendersCleanReconciliationWhenAllMatch() {
        RiskAnalytics.Report risk = RiskAnalytics.analyze(List.of());
        ReconciliationService.Report recon = new ReconciliationService().reconcile(List.of(), List.of());
        String html = presenter.buildHtml("Live", risk, recon);
        assertTrue(html.contains("reconcile."), html);
        assertFalse(html.contains("mismatch(es)"), html);
    }
}
