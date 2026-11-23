package com.neuralarc.ui;

import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyOrderSide;
import com.neuralarc.model.StrategyOrderStatus;
import com.neuralarc.model.StrategyStatus;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Closes out a row that still believes it holds shares the broker no longer has.
 *
 * <p>The engine only completes a strategy when it sees one of its own exit orders fill. When the
 * shares leave another way — sold by hand, by another row, or a sell that failed after the position
 * was already gone — no exit fill is ever recorded, so the row stays ACTIVE and keeps reading
 * "Sell trigger active (no shares held)". Refresh is the moment the operator asks the app and the
 * broker to agree, so it checks the broker and completes such rows.
 *
 * <p>Deliberately conservative: it acts only when the broker holds nothing in the symbol, has no open
 * order in it, the row has no working order of its own, and the row's last order activity is older
 * than {@link #SETTLE_GRACE} (a fresh fill can reach the orders list before the positions list).
 */
final class ExternallyClosedPositionReconciler {
    static final Duration SETTLE_GRACE = Duration.ofMinutes(5);

    private ExternallyClosedPositionReconciler() {
    }

    /**
     * @param brokerHeldSymbols upper-case symbols the broker holds in this strategy's mode
     * @param brokerOpenOrderSymbols upper-case symbols with an open broker order in this mode
     * @return true when the strategy was changed and needs saving
     */
    static boolean reconcile(
            Strategy strategy,
            List<StrategyOrder> orders,
            Set<String> brokerHeldSymbols,
            Set<String> brokerOpenOrderSymbols,
            Instant now
    ) {
        if (!needsBrokerCheck(strategy, orders, now)
                || brokerHeldSymbols == null || brokerOpenOrderSymbols == null) {
            return false;
        }
        String symbol = strategy.symbol().toUpperCase(java.util.Locale.ROOT);
        if (brokerHeldSymbols.contains(symbol) || brokerOpenOrderSymbols.contains(symbol)) {
            return false;
        }
        strategy.setStatus(StrategyStatus.COMPLETED);
        strategy.setCurrentState(StrategyLifecycleState.COMPLETED);
        strategy.clearLastError();
        strategy.setLastEvent("Closed on refresh: the broker holds no " + symbol
                + " shares, so they were sold outside this strategy");
        return true;
    }

    /**
     * Whether the row's own state and orders say it holds shares with nothing working, long enough
     * that the broker's answer can be trusted. Changes nothing; the broker is asked only when true.
     */
    static boolean needsBrokerCheck(Strategy strategy, List<StrategyOrder> orders, Instant now) {
        if (!isCandidate(strategy) || orders == null || orders.isEmpty()
                || strategy.symbol() == null || strategy.symbol().isBlank()) {
            return false;
        }
        return orders.stream().noneMatch(StrategyOrder::isPending)
                && netFilledShares(orders).signum() > 0
                && !settledRecently(orders, now);
    }

    private static boolean isCandidate(Strategy strategy) {
        if (strategy == null || strategy.status() != StrategyStatus.ACTIVE) {
            return false;
        }
        return switch (strategy.currentState()) {
            case BASE_BUY_FILLED, BUY_LIMIT_1_FILLED, BUY_LIMIT_2_FILLED,
                 STOP_LOSS_ACTIVE, PROFIT_HOLD_ACTIVE -> true;
            default -> false;
        };
    }

    /** Shares this strategy's own filled orders say it still holds: buys in, sells out. */
    private static BigDecimal netFilledShares(List<StrategyOrder> orders) {
        BigDecimal held = BigDecimal.ZERO;
        for (StrategyOrder order : orders) {
            if (order.status() != StrategyOrderStatus.FILLED && order.status() != StrategyOrderStatus.PARTIALLY_FILLED
                    && order.status() != StrategyOrderStatus.CANCELED) {
                continue;
            }
            BigDecimal filled = order.filledQuantity() == null ? BigDecimal.ZERO : order.filledQuantity();
            held = order.side() == StrategyOrderSide.BUY ? held.add(filled) : held.subtract(filled);
        }
        return held;
    }

    private static boolean settledRecently(List<StrategyOrder> orders, Instant now) {
        Instant cutoff = (now == null ? Instant.now() : now).minus(SETTLE_GRACE);
        for (StrategyOrder order : orders) {
            Instant last = latest(order.updatedAt(), order.filledAt(), order.submittedAt());
            if (last != null && last.isAfter(cutoff)) {
                return true;
            }
        }
        return false;
    }

    private static Instant latest(Instant... instants) {
        Instant best = null;
        for (Instant instant : instants) {
            if (instant != null && (best == null || instant.isAfter(best))) {
                best = instant;
            }
        }
        return best;
    }
}
