package com.neuralarc.ui;

import com.neuralarc.model.Position;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyStatus;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Every losing open position in the current scope, each marked as selectable or locked.
 *
 * <p>The average-down action used to count only the positions it could buy into, so rows with a
 * working limit sell vanished from the count without explanation and the dialog disagreed with the
 * red rows on the grid. Now every losing position is listed; the ones that cannot take a buy stay
 * visible with the reason, and selectability is decided by the same eligibility rule the bulk
 * action applies when it submits.
 */
final class AverageDownCandidates {
    private AverageDownCandidates() {
    }

    record Candidate(ManagedStrategy entry, String workspaceLabel, String lockReason) {
        Candidate {
            workspaceLabel = workspaceLabel == null ? "" : workspaceLabel;
            lockReason = lockReason == null ? "" : lockReason;
        }

        boolean selectable() {
            return lockReason.isEmpty();
        }

        /** Why a row is locked, or what will happen to a selectable row's working target sell. */
        String note() {
            if (!selectable()) {
                return lockReason;
            }
            return PortfolioActionMatchers.isAverageDownIntoTargetSell(entry)
                    ? "Target sell resizes and reprices after the buy fills"
                    : "";
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
    }

    /**
     * @param scope          the rows visible in the current tab.
     * @param eligible       whether a row can take a manual buy right now — the bulk action's own rule.
     * @param workspaceLabel resolves a workspace id (possibly null) to a display name.
     * @return losing positions, selectable first, then the deepest loss first.
     */
    static List<Candidate> collect(
            List<ManagedStrategy> scope,
            Predicate<ManagedStrategy> eligible,
            Function<String, String> workspaceLabel
    ) {
        if (scope == null) {
            return List.of();
        }
        return scope.stream()
                .filter(AverageDownCandidates::isLosingOpenPosition)
                .map(entry -> new Candidate(
                        entry,
                        workspaceLabel == null ? "" : workspaceLabel.apply(entry.strategy.workspaceId()),
                        eligible != null && eligible.test(entry) ? "" : lockReason(entry)))
                .sorted(Comparator.comparing((Candidate candidate) -> !candidate.selectable())
                        .thenComparing(Candidate::unrealizedPnl)
                        .thenComparing(Candidate::symbol))
                .toList();
    }

    static boolean anyLosingOpenPosition(List<ManagedStrategy> scope) {
        return scope != null && scope.stream().anyMatch(AverageDownCandidates::isLosingOpenPosition);
    }

    /** A row that holds shares and is currently below its entry — what the grid shows in red. */
    static boolean isLosingOpenPosition(ManagedStrategy entry) {
        if (entry == null || entry.strategy == null) {
            return false;
        }
        Position position = entry.cachedPosition();
        return position.getTotalShares() > 0 && position.unrealizedPnl().compareTo(BigDecimal.ZERO) < 0;
    }

    /** Why a losing position cannot take a buy, phrased as what the operator would need to change. */
    static String lockReason(ManagedStrategy entry) {
        StrategyLifecycleState state = entry.strategy.currentState();
        if (state == StrategyLifecycleState.SELL_PLACED) {
            return "Exit sell working – cancel it to average down";
        }
        if (state == StrategyLifecycleState.SELL_PARTIALLY_FILLED) {
            return "Exit in progress – sell partially filled";
        }
        StrategyStatus status = entry.strategy.status();
        if (status == StrategyStatus.FAILED) {
            return "Strategy failed – resolve it before buying";
        }
        if (status != StrategyStatus.ACTIVE && status != StrategyStatus.PAUSED) {
            return "Only active or paused strategies can buy";
        }
        if (state == StrategyLifecycleState.COMPLETED
                || state == StrategyLifecycleState.FAILED
                || state == StrategyLifecycleState.STOPPED) {
            return "Strategy is closing";
        }
        return "Not eligible for a manual buy";
    }
}
