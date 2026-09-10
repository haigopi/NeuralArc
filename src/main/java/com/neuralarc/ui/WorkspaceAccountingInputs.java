package com.neuralarc.ui;

import com.neuralarc.analytics.WorkspaceAccounting;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Assembles the canonical per-strategy accounting inputs shared by the top status bar, every tab
 * summary, and the portfolio views.
 *
 * <p>The rule this class exists to hold: a strategy that has left the Current Strategies grid — sold
 * out, completed, stopped or archived — still contributes the profit it banked, because those sells
 * are real and are still listed in Trade History. It contributes <em>only</em> realized P&amp;L; it
 * holds no shares, no market value and no allocated capital, so open-position figures are untouched.
 * Dropping such strategies entirely is what made realized P&amp;L fall to zero as soon as a sold
 * position was cleaned off the grid.
 */
final class WorkspaceAccountingInputs {
    private WorkspaceAccountingInputs() {
    }

    /**
     * One strategy's contribution. {@code openInCurrentTab} says whether the row is still part of the
     * Current Strategies view; when it is not, only {@code sells} is read.
     */
    record StrategyInput(
            String workspaceId,
            boolean openInCurrentTab,
            List<WorkspaceAccounting.RealizedSell> sells,
            int openShares,
            BigDecimal unrealizedPnl,
            BigDecimal marketValue,
            BigDecimal capitalAllocated
    ) {
    }

    /** Bundle of per-strategy accounts + realized sells for a mode — the single P&L input set. */
    record Result(
            List<WorkspaceAccounting.StrategyAccount> accounts,
            List<WorkspaceAccounting.RealizedSell> sells
    ) {
    }

    static Result build(List<StrategyInput> inputs) {
        List<WorkspaceAccounting.StrategyAccount> accounts = new ArrayList<>();
        List<WorkspaceAccounting.RealizedSell> sells = new ArrayList<>();
        if (inputs == null) {
            return new Result(accounts, sells);
        }
        for (StrategyInput input : inputs) {
            if (input == null) {
                continue;
            }
            List<WorkspaceAccounting.RealizedSell> strategySells =
                    input.sells() == null ? List.of() : input.sells();
            // A closed-out strategy with nothing banked has nothing to say about the portfolio.
            if (!input.openInCurrentTab() && strategySells.isEmpty()) {
                continue;
            }
            sells.addAll(strategySells);
            BigDecimal realized = BigDecimal.ZERO;
            for (WorkspaceAccounting.RealizedSell sell : strategySells) {
                realized = realized.add(sell.realizedPnl() == null ? BigDecimal.ZERO : sell.realizedPnl());
            }
            accounts.add(input.openInCurrentTab()
                    ? new WorkspaceAccounting.StrategyAccount(
                            input.workspaceId(),
                            input.openShares(),
                            input.unrealizedPnl(),
                            realized,
                            input.marketValue(),
                            input.capitalAllocated())
                    : new WorkspaceAccounting.StrategyAccount(
                            input.workspaceId(),
                            0,
                            BigDecimal.ZERO,
                            realized,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO));
        }
        return new Result(accounts, sells);
    }
}
