package com.neuralarc.ui;

import com.neuralarc.model.Position;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Every open position the threshold sell could act on, before any rule is applied.
 *
 * <p>A position qualifies on the same rule the plain sell actions use: it holds shares and has no
 * sell already working or finished. The dialog filters this list by the operator's rule as they
 * type, so the grid always shows exactly what the rule would sell.
 */
final class ThresholdSellCandidates {
    private ThresholdSellCandidates() {
    }

    record Candidate(ManagedStrategy entry, String workspaceLabel) {
        Candidate {
            workspaceLabel = workspaceLabel == null ? "" : workspaceLabel;
        }

        String strategyId() {
            return entry.strategy.id();
        }

        String symbol() {
            return entry.strategy.symbol();
        }

        Position position() {
            return entry.cachedPosition();
        }

        int shares() {
            return position().getTotalShares();
        }

        BigDecimal averageCost() {
            return Monetary.round(position().getAverageCost());
        }

        BigDecimal lastPrice() {
            return Monetary.round(position().getLastPrice());
        }

        BigDecimal unrealizedPnl() {
            return Monetary.round(position().unrealizedPnl());
        }

        BigDecimal pnlPercent() {
            BigDecimal invested = position().totalInvested();
            if (invested == null || invested.signum() <= 0) {
                return Monetary.zero();
            }
            return unrealizedPnl().multiply(BigDecimal.valueOf(100)).divide(invested, 2, RoundingMode.HALF_UP);
        }

        /** What the shares are worth at the last price, i.e. what a fill near the market would raise. */
        BigDecimal estimatedProceeds() {
            return Monetary.round(position().getLastPrice().multiply(BigDecimal.valueOf(shares())));
        }
    }

    /** @return sellable open positions, biggest winner first, so the grid reads top-down by P&amp;L. */
    static List<Candidate> collect(List<ManagedStrategy> scope, Function<String, String> workspaceLabel) {
        if (scope == null) {
            return List.of();
        }
        return scope.stream()
                .filter(ThresholdSellCandidates::isSellable)
                .map(entry -> new Candidate(
                        entry,
                        workspaceLabel == null ? "" : workspaceLabel.apply(entry.strategy.workspaceId())))
                .sorted(Comparator.comparing(Candidate::unrealizedPnl).reversed().thenComparing(Candidate::symbol))
                .toList();
    }

    static boolean isSellable(ManagedStrategy entry) {
        return PortfolioActionMatchers.isEligibleForManualSell(entry)
                && entry.cachedPosition().getTotalShares() > 0;
    }
}
