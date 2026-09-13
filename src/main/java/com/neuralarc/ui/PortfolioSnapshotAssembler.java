package com.neuralarc.ui;

import com.neuralarc.analytics.PortfolioSnapshot;
import com.neuralarc.analytics.RiskAnalytics;
import com.neuralarc.analytics.WorkspaceAccounting;
import com.neuralarc.model.StrategyWorkspace;
import com.neuralarc.service.ReconciliationService;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Builds the {@link PortfolioSnapshot} for the snapshot email from the same sources the screen uses:
 * the portfolio metrics behind the bottom bar and grid footers, the workspace accounting behind the
 * P&amp;L figures, and the Risk Dashboard's inputs, so the email and the app never disagree.
 */
final class PortfolioSnapshotAssembler {
    private static final String FUNDS_PREFIX = "Funds Available:";

    /** The Risk Dashboard's inputs for the viewed mode, shared with the snapshot email. */
    record RiskInputs(
            List<RiskAnalytics.Holding> holdings,
            List<RiskAnalytics.PositionInput> positions,
            List<ReconciliationService.SymbolPosition> localPositions
    ) {
        RiskInputs {
            holdings = holdings == null ? List.of() : holdings;
            positions = positions == null ? List.of() : positions;
            localPositions = localPositions == null ? List.of() : localPositions;
        }
    }

    private PortfolioSnapshotAssembler() {
    }

    /**
     * @param metricsFor    portfolio metrics for a workspace id, or for every workspace when given null
     * @param accountingFor the P&amp;L accounting for a workspace id, or for every workspace when given null
     */
    static PortfolioSnapshot assemble(
            String occasion,
            ZonedDateTime takenAt,
            String modeLabel,
            String availableFundsText,
            List<StrategyWorkspace> workspaces,
            Function<String, SystemMetricsPresenter.PortfolioScopeMetrics> metricsFor,
            Function<String, WorkspaceAccounting.Snapshot> accountingFor,
            RiskInputs riskInputs
    ) {
        List<PortfolioSnapshot.WorkspaceRow> rows = new ArrayList<>();
        for (StrategyWorkspace workspace : workspaces == null ? List.<StrategyWorkspace>of() : workspaces) {
            PortfolioSnapshot.Figures figures = figures(metricsFor.apply(workspace.id()), accountingFor.apply(workspace.id()));
            if (!figures.isEmpty()) {
                rows.add(new PortfolioSnapshot.WorkspaceRow(workspace.name(), figures));
            }
        }
        RiskInputs risk = riskInputs == null ? new RiskInputs(null, null, null) : riskInputs;
        return new PortfolioSnapshot(
                occasion,
                takenAt,
                modeLabel,
                funds(availableFundsText),
                figures(metricsFor.apply(null), accountingFor.apply(null)),
                rows,
                RiskAnalytics.analyze(risk.holdings()),
                RiskAnalytics.classify(risk.positions()));
    }

    static PortfolioSnapshot.Figures figures(SystemMetricsPresenter.PortfolioScopeMetrics metrics, WorkspaceAccounting.Snapshot accounting) {
        return new PortfolioSnapshot.Figures(
                metrics.marketValue(),
                metrics.investedValue(),
                metrics.upcomingBuyTotal(),
                accounting.realized(),
                accounting.unrealized(),
                accounting.total(),
                accounting.dailyRealized(),
                accounting.closedTrades(),
                accounting.winRatePercent(),
                metrics.gainingCount(),
                metrics.gainingPnl(),
                metrics.losingCount(),
                metrics.losingPnl(),
                metrics.pendingBuyPositions(),
                metrics.pendingSellPositions(),
                accounting.openPositions());
    }

    /** The funds figure without the status bar's "Funds Available:" caption. */
    static String funds(String availableFundsText) {
        String text = availableFundsText == null ? "" : availableFundsText.trim();
        if (text.regionMatches(true, 0, FUNDS_PREFIX, 0, FUNDS_PREFIX.length())) {
            text = text.substring(FUNDS_PREFIX.length()).trim();
        }
        return text.isBlank() ? "-" : text;
    }
}
