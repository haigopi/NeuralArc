package com.neuralarc.ui;

import com.neuralarc.analytics.WorkspaceAccounting;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Turns a {@link WorkspaceAccounting.Snapshot} into the P&amp;L figures that open the footer under a
 * strategy workspace grid (or the All Stocks aggregate), each with a plain-language explanation.
 * To keep the footer on one row, figures the row already implies (unrealized P&amp;L is Gaining plus
 * Losing) or that are reference only (open positions, planned budget) go in the tab name's tooltip.
 * Kept separate from any Swing component so the wording is unit-testable.
 */
final class WorkspaceSummaryPresenter {
    List<PortfolioScopePresenter.Figure> figures(String label, WorkspaceAccounting.Snapshot snapshot) {
        String scope = PortfolioScopePresenter.scopeHtml(label) + ": ";
        return List.of(
                signed("Realized", snapshot.realized(),
                        scope + "profit or loss already locked in by sells."),
                signed("Total", snapshot.total(),
                        scope + "realized and unrealized together (unrealized is Gaining plus Losing)."),
                signed("Today", snapshot.dailyRealized(),
                        scope + "profit or loss locked in by sells during today's trading day."),
                new PortfolioScopePresenter.Figure("Win", winText(snapshot),
                        scope + "the share of sells that made a profit.", PortfolioScopePresenter.Tone.NEUTRAL));
    }

    /** The tab name's tooltip: the reference figures that are not repeated in the row. */
    String titleTooltip(String label, WorkspaceAccounting.Snapshot snapshot) {
        return PortfolioScopePresenter.scopeHtml(label)
                + "<br><b>Unrealized " + PortfolioScopePresenter.money(snapshot.unrealized()) + "</b>:"
                + " profit or loss on the shares held now (Gaining and Losing together)."
                + " It is locked in only when they are sold."
                + "<br><b>Open positions " + snapshot.openPositions() + "</b>: positions holding shares now."
                + "<br><b>Planned budget " + PortfolioScopePresenter.money(snapshot.capitalAllocated()) + "</b>:"
                + " the total these strategies are set to use.";
    }

    private static PortfolioScopePresenter.Figure signed(String caption, BigDecimal value, String tooltipHtml) {
        return new PortfolioScopePresenter.Figure(caption, PortfolioScopePresenter.money(value), tooltipHtml,
                PortfolioScopePresenter.toneOf(value));
    }

    private static String winText(WorkspaceAccounting.Snapshot snapshot) {
        int sells = snapshot.closedTrades();
        if (sells == 0) {
            return "no sells yet";
        }
        return String.format(Locale.US, "%.0f%% of %d %s", snapshot.winRatePercent(), sells, sells == 1 ? "sell" : "sells");
    }
}
