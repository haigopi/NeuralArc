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
            default -> Optional.empty();
        };
    }
}
