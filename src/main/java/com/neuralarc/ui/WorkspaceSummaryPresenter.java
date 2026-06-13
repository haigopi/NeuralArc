package com.neuralarc.ui;

import com.neuralarc.analytics.WorkspaceAccounting;

import java.math.BigDecimal;

/**
 * Formats a {@link WorkspaceAccounting.Snapshot} into the per-tab P&amp;L summary line shown for a
 * strategy workspace (or the All Stocks aggregate). Kept separate from any Swing component so the
 * formatting is unit-testable.
 */
final class WorkspaceSummaryPresenter {
    String summaryLine(String label, WorkspaceAccounting.Snapshot snapshot) {
        return label
                + "  •  Realized " + money(snapshot.realized())
                + "  •  Unrealized " + money(snapshot.unrealized())
                + "  •  Total " + money(snapshot.total())
                + "  •  Daily " + money(snapshot.dailyRealized())
                + "  •  Win " + percent(snapshot.winRatePercent())
                + "  •  Open " + snapshot.openPositions()
                + "  •  Closed " + snapshot.closedTrades()
                + "  •  Capital " + money(snapshot.capitalAllocated());
    }

    private String money(BigDecimal value) {
        if (value == null) {
            return "$0.00";
        }
        return value.signum() < 0
                ? "-$" + value.abs().toPlainString()
                : "$" + value.toPlainString();
    }

    private String percent(double value) {
        return String.format("%.0f%%", value);
    }
}
