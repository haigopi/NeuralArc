package com.neuralarc.ui;

import com.neuralarc.analytics.PortfolioSnapshot;
import com.neuralarc.analytics.RiskAnalytics;
import com.neuralarc.analytics.WorkspaceAccounting;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyWorkspace;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PortfolioSnapshotAssemblerTest {
    private static final SystemMetricsPresenter.PortfolioScopeMetrics METRICS = new SystemMetricsPresenter.PortfolioScopeMetrics(
            new BigDecimal("18420.55"), new BigDecimal("16980.40"), new BigDecimal("4512.75"),
            new BigDecimal("612.30"), 14, new BigDecimal("-1204.88"), 19, 6, 11);
    private static final WorkspaceAccounting.Snapshot ACCOUNTING = new WorkspaceAccounting.Snapshot(
            new BigDecimal("412.10"), new BigDecimal("-592.58"), new BigDecimal("-180.48"), new BigDecimal("88.00"),
            new BigDecimal("21493.15"), 33, 25, 64.0, new BigDecimal("612.30"), new BigDecimal("-1204.88"));
    private static final SystemMetricsPresenter.PortfolioScopeMetrics NO_METRICS =
            new SystemMetricsPresenter().computePortfolioScopeMetrics(null, null);
    private static final WorkspaceAccounting.Snapshot NO_ACCOUNTING = new WorkspaceAccounting.Snapshot(
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0.0,
            BigDecimal.ZERO, BigDecimal.ZERO);

    @Test
    void figuresCombineTheBarMetricsWithTheWorkspaceAccounting() {
        PortfolioSnapshot.Figures figures = PortfolioSnapshotAssembler.figures(METRICS, ACCOUNTING);

        assertEquals(new BigDecimal("18420.55"), figures.marketValue());
        assertEquals(new BigDecimal("16980.40"), figures.invested());
        assertEquals(new BigDecimal("4512.75"), figures.upcomingBuys());
        assertEquals(new BigDecimal("21493.15"), figures.committed());
        assertEquals(new BigDecimal("412.10"), figures.realized());
        assertEquals(new BigDecimal("-592.58"), figures.unrealized());
        assertEquals(new BigDecimal("88.00"), figures.today());
        assertEquals(25, figures.sells());
        assertEquals(14, figures.gainingCount());
        assertEquals(19, figures.losingCount());
        assertEquals(6, figures.pendingBuyPositions());
        assertEquals(11, figures.pendingSellPositions());
        assertEquals(33, figures.openPositions());
    }

    @Test
    void workspacesWithNothingToReportAreLeftOut() {
        StrategyWorkspace growth = workspace("w1", "Growth", "GRW");
        StrategyWorkspace idle = workspace("w2", "Idle", "IDL");

        PortfolioSnapshot snapshot = PortfolioSnapshotAssembler.assemble("Lunch", ZonedDateTime.now(ZoneId.of("America/New_York")),
                "Paper", "Funds Available: $2,000.00", List.of(growth, idle),
                id -> id == null || "w1".equals(id) ? METRICS : NO_METRICS,
                id -> id == null || "w1".equals(id) ? ACCOUNTING : NO_ACCOUNTING,
                null);

        assertEquals(List.of("Growth"), snapshot.workspaces().stream().map(PortfolioSnapshot.WorkspaceRow::name).toList());
        assertEquals(new BigDecimal("16980.40"), snapshot.allWorkspaces().invested());
        assertEquals("$2,000.00", snapshot.fundsAvailable());
        assertEquals("Paper", snapshot.modeLabel());
    }

    @Test
    void theRiskAnalysisComesFromTheDashboardsInputs() {
        PortfolioSnapshotAssembler.RiskInputs inputs = new PortfolioSnapshotAssembler.RiskInputs(
                List.of(new RiskAnalytics.Holding("GOOG", "Growth", new BigDecimal("1614.80"), new BigDecimal("114.80"))),
                List.of(new RiskAnalytics.PositionInput("GOOG", "Growth", new BigDecimal("10"), new BigDecimal("150"),
                        new BigDecimal("161.48"), new BigDecimal("140"), new BigDecimal("180"))),
                List.of());

        PortfolioSnapshot snapshot = PortfolioSnapshotAssembler.assemble("Lunch", ZonedDateTime.now(ZoneId.of("America/New_York")),
                "Live", null, List.of(), id -> METRICS, id -> ACCOUNTING, inputs);

        assertEquals(new BigDecimal("1614.80"), snapshot.risk().totalCapital());
        assertEquals(1, snapshot.positionRisks().size());
        assertEquals(RiskAnalytics.RiskVerdict.ON_TRACK_WINNER, snapshot.positionRisks().getFirst().verdict());
    }

    @Test
    void fundsDropTheStatusBarCaption() {
        assertEquals("$1,234.50", PortfolioSnapshotAssembler.funds("Funds Available: $1,234.50"));
        assertEquals("-", PortfolioSnapshotAssembler.funds("Funds Available: "));
        assertEquals("-", PortfolioSnapshotAssembler.funds(null));
    }

    private static StrategyWorkspace workspace(String id, String name, String code) {
        return new StrategyWorkspace(id, name, code, StrategyMode.PAPER, false, Instant.now(), Instant.now());
    }
}
