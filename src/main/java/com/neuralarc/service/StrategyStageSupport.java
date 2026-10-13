package com.neuralarc.service;

import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyOrderStatus;
import com.neuralarc.model.StrategyStage;

import java.util.List;
import java.util.Optional;

final class StrategyStageSupport {
    private StrategyStageSupport() {
    }

    static boolean isExitStage(StrategyStage stage) {
        return stage == StrategyStage.TARGET_SELL
                || stage == StrategyStage.PROFIT_EXIT
                || stage == StrategyStage.STOP_LOSS
                || stage == StrategyStage.LOSS_EXIT
                || stage == StrategyStage.MANUAL_EXIT
                || stage == StrategyStage.CLOSE_POSITION;
    }

    static boolean isEntryStage(StrategyStage stage) {
        return stage == StrategyStage.BASE_BUY
                || stage == StrategyStage.BUY_LIMIT_1
                || stage == StrategyStage.BUY_LIMIT_2
                || stage == StrategyStage.MANUAL_BUY;
    }

    /** Whether any of this strategy's own entry orders has bought shares, fully or in part. */
    static boolean hasEntryFill(List<StrategyOrder> orders) {
        return orders.stream().anyMatch(order -> isEntryStage(order.stage())
                && (order.status() == StrategyOrderStatus.FILLED
                || (order.status() == StrategyOrderStatus.PARTIALLY_FILLED
                && order.filledQuantity() != null && order.filledQuantity().signum() > 0)));
    }

    /**
     * True while this strategy has an entry order working and none of its own entry orders has filled.
     *
     * <p>The broker nets everything it holds in a symbol into one position, so "the symbol has a
     * position" says nothing about <em>this</em> entry. Whether the entry holds shares is answered by its
     * own orders, which are tracked by their own broker order ids: until one fills, any broker position
     * in the symbol belongs to some other entry or to a manual buy.
     */
    static boolean awaitingFirstEntryFill(List<StrategyOrder> orders) {
        return !hasEntryFill(orders)
                && orders.stream().anyMatch(order -> isEntryStage(order.stage()) && order.isPending());
    }

    static boolean isProfitableExitStage(StrategyStage stage) {
        return stage == StrategyStage.TARGET_SELL
                || stage == StrategyStage.PROFIT_EXIT
                || stage == StrategyStage.MANUAL_EXIT;
    }

    static boolean isStageFilled(List<StrategyOrder> orders, StrategyStage stage) {
        return orders.stream().anyMatch(order -> order.stage() == stage && order.status() == StrategyOrderStatus.FILLED);
    }

    static String ruleNameForStage(StrategyStage stage) {
        return switch (stage) {
            case BASE_BUY -> "BUY_RULE";
            case BUY_LIMIT_1 -> "LOSS_BUY_RULE";
            case BUY_LIMIT_2 -> "LOSS_INVESTMENT_BUY_RULE";
            case TARGET_SELL -> "SELL_RULE";
            case STOP_LOSS -> "STOP_LOSS_RULE";
            case LOSS_EXIT, PROFIT_EXIT, MANUAL_BUY, MANUAL_EXIT, CLOSE_POSITION -> stage.name();
        };
    }

    static Optional<StrategyStage> stageForRuleType(String ruleType) {
        if (ruleType == null || ruleType.isBlank()) {
            return Optional.empty();
        }
        return switch (ruleType.trim().toUpperCase()) {
            case "BUY_RULE", "BASE_BUY" -> Optional.of(StrategyStage.BASE_BUY);
            case "LOSS_BUY_RULE", "BUY_LIMIT_1" -> Optional.of(StrategyStage.BUY_LIMIT_1);
            case "LOSS_INVESTMENT_BUY_RULE", "BUY_LIMIT_2" -> Optional.of(StrategyStage.BUY_LIMIT_2);
            case "MANUAL_BUY" -> Optional.of(StrategyStage.MANUAL_BUY);
            default -> Optional.empty();
        };
    }
}
