package com.neuralarc.analytics;

import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.util.List;

/**
 * Pure aggregation of strategy-level P&amp;L into a workspace-level {@link Snapshot}.
 *
 * <p>NeuralArc keeps strategy accounting separate from Alpaca's blended broker position: a
 * workspace's figures are the sum of its member strategies (this class), never the broker total.
 * The math is intentionally side-effect free so it can feed the per-tab summary, the top status
 * bar, and the (future) risk dashboard, and be unit-tested in isolation.
 *
 * <p>Win rate is computed from realized <em>sell trades</em> (profitable sells / total sells), not
 * from closed strategies — a strategy can cycle through many trades, so per-trade outcomes are the
 * meaningful unit.
 */
public final class WorkspaceAccounting {
    private WorkspaceAccounting() {
    }

    /** One member strategy's current standing (open position + realized total). */
    public record StrategyAccount(
            String workspaceId,   // null = unassigned (All Stocks only)
            int openShares,
            BigDecimal unrealizedPnl,
            BigDecimal realizedPnl,
            BigDecimal marketValue,
            BigDecimal capitalAllocated
    ) {
    }

    /** One realized sell trade, used for win rate and daily realized P&L. */
    public record RealizedSell(
            String workspaceId,   // null = unassigned
            BigDecimal realizedPnl,
            boolean today
    ) {
    }

    public record Snapshot(
            BigDecimal realized,
            BigDecimal unrealized,
            BigDecimal total,
            BigDecimal dailyRealized,
            BigDecimal capitalAllocated,
            int openPositions,
            int closedTrades,
            double winRatePercent,
            BigDecimal gainingTotal,
            BigDecimal losingTotal
    ) {
    }

    /**
     * Aggregates the figures for {@code targetWorkspaceId}. A {@code null} target means the
     * All Stocks view, which aggregates every account/sell in the (already mode-scoped) inputs.
     */
    public static Snapshot forWorkspace(
            String targetWorkspaceId,
            List<StrategyAccount> accounts,
            List<RealizedSell> sells
    ) {
        BigDecimal realized = BigDecimal.ZERO;
        BigDecimal unrealized = BigDecimal.ZERO;
        BigDecimal capital = BigDecimal.ZERO;
        BigDecimal gainingTotal = BigDecimal.ZERO;
        BigDecimal losingTotal = BigDecimal.ZERO;
        int openPositions = 0;
        for (StrategyAccount account : accounts) {
            if (!matches(targetWorkspaceId, account.workspaceId())) {
                continue;
            }
            realized = realized.add(nonNull(account.realizedPnl()));
            unrealized = unrealized.add(nonNull(account.unrealizedPnl()));
            capital = capital.add(nonNull(account.capitalAllocated()));
            if (account.openShares() > 0) {
                openPositions++;
                BigDecimal accountUnrealized = nonNull(account.unrealizedPnl());
                if (accountUnrealized.compareTo(BigDecimal.ZERO) > 0) {
                    gainingTotal = gainingTotal.add(accountUnrealized);
                } else if (accountUnrealized.compareTo(BigDecimal.ZERO) < 0) {
                    losingTotal = losingTotal.add(accountUnrealized);
                }
            }
        }

        BigDecimal dailyRealized = BigDecimal.ZERO;
        int totalSells = 0;
        int profitableSells = 0;
        for (RealizedSell sell : sells) {
            if (!matches(targetWorkspaceId, sell.workspaceId())) {
                continue;
            }
            totalSells++;
            BigDecimal pnl = nonNull(sell.realizedPnl());
            if (pnl.compareTo(BigDecimal.ZERO) > 0) {
                profitableSells++;
            }
            if (sell.today()) {
                dailyRealized = dailyRealized.add(pnl);
            }
        }

        double winRate = totalSells == 0 ? 0.0 : (profitableSells * 100.0) / totalSells;
        return new Snapshot(
                Monetary.round(realized),
                Monetary.round(unrealized),
                Monetary.round(realized.add(unrealized)),
                Monetary.round(dailyRealized),
                Monetary.round(capital),
                openPositions,
                totalSells,
                winRate,
                Monetary.round(gainingTotal),
                Monetary.round(losingTotal)
        );
    }

    private static boolean matches(String targetWorkspaceId, String accountWorkspaceId) {
        // All Stocks (null target) includes everything; a workspace target matches by id.
        return targetWorkspaceId == null || targetWorkspaceId.equals(accountWorkspaceId);
    }

    private static BigDecimal nonNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
