package com.neuralarc.service;

import com.neuralarc.api.AlpacaPositionData;
import com.neuralarc.model.StopLossType;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyOrderStatus;
import com.neuralarc.model.StrategyStage;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.util.List;

/**
 * Pure decision logic for the managed stop-loss rule. Side-effect free so the conditions that can
 * liquidate a position are unit-testable without a broker or repository.
 */
final class ManagedStopLossEvaluator {
    private ManagedStopLossEvaluator() {
    }

    enum Action {
        /** No action; {@code detail} explains why in the rule log. */
        SKIP("SKIPPED"),
        NOT_SATISFIED("NOT_SATISFIED"),
        /** Stop could never have been valid downside protection — repair instead of selling. */
        AUTO_CORRECT("AUTO_CORRECTED"),
        SELL("SATISFIED");

        private final String logStatus;

        Action(String logStatus) {
            this.logStatus = logStatus;
        }

        String logStatus() {
            return logStatus;
        }
    }

    record Decision(Action action, BigDecimal threshold, String detail) {
    }

    static Decision decide(
            Strategy strategy,
            AlpacaPositionData position,
            BigDecimal latestPrice,
            List<StrategyOrder> orders
    ) {
        if (!strategy.automatedStopLossEnabled()) {
            return new Decision(Action.SKIP, BigDecimal.ZERO, "Disabled");
        }
        if (hasPendingOrFilledStopLoss(orders)) {
            return new Decision(Action.SKIP, BigDecimal.ZERO,
                    "Existing pending or filled stop loss order already present");
        }
        BigDecimal threshold = strategy.stopLossType() == StopLossType.PERCENT_BELOW_AVERAGE_COST
                ? Monetary.round(position.avgEntryPrice()
                        .multiply(BigDecimal.ONE.subtract(strategy.stopLossPercent().divide(new BigDecimal("100")))))
                : strategy.stopLossPrice();
        if (threshold.compareTo(BigDecimal.ZERO) <= 0) {
            return new Decision(Action.SKIP, threshold, "Computed threshold is not positive");
        }
        if (StopLossSanityGuard.isMisconfigured(threshold, latestPrice, position.avgEntryPrice())) {
            return new Decision(Action.AUTO_CORRECT, threshold, "");
        }
        if (latestPrice.compareTo(threshold) > 0) {
            return new Decision(Action.NOT_SATISFIED, threshold,
                    "latestPrice=" + latestPrice.toPlainString() + " > threshold=" + threshold.toPlainString());
        }
        return new Decision(Action.SELL, threshold,
                "latestPrice=" + latestPrice.toPlainString()
                        + " <= threshold=" + threshold.toPlainString()
                        + ", quantity=" + position.quantity().toPlainString());
    }

    private static boolean hasPendingOrFilledStopLoss(List<StrategyOrder> orders) {
        return orders.stream().anyMatch(order -> order.stage() == StrategyStage.STOP_LOSS
                && (order.isPending() || order.status() == StrategyOrderStatus.FILLED));
    }
}
