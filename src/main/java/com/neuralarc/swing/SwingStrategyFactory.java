package com.neuralarc.swing;

import com.neuralarc.model.ProfitControlMode;
import com.neuralarc.model.ProfitHoldType;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyConfig;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyStatus;
import com.neuralarc.model.ThresholdType;
import com.neuralarc.model.TrailingType;

import java.math.BigDecimal;
import java.util.UUID;

public final class SwingStrategyFactory {
    public Strategy toStrategy(SwingRecommendation recommendation, String workspaceId, boolean executeRequested, int pollingSeconds) {
        StrategyConfig config = new StrategyConfig(
                recommendation.symbol(),
                recommendation.plannedEntryPrice(),
                1,
                true,
                recommendation.stopLossPrice(),
                true,
                recommendation.targetPrice(),
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                0,
                false,
                false,
                BigDecimal.ZERO,
                Math.max(1, pollingSeconds),
                true,
                false,
                false,
                ProfitHoldType.PERCENT_TRAILING,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                ProfitControlMode.SELL_TRIGGER,
                ThresholdType.FIXED_AMOUNT,
                BigDecimal.ZERO,
                TrailingType.PERCENTAGE,
                BigDecimal.ZERO,
                false
        );
        Strategy strategy = Strategy.fromConfig(
                UUID.randomUUID().toString(),
                "SWING_VAULT: " + recommendation.symbol() + " " + recommendation.mode().name(),
                config,
                recommendation.mode()
        );
        strategy.setWorkspaceId(workspaceId);
        strategy.setStatus(StrategyStatus.CREATED);
        strategy.setCurrentState(StrategyLifecycleState.CREATED);
        strategy.setLatestOrderStatus(executeRequested ? "SWING_MONITORING" : "SWING_RECOMMENDED");
        strategy.setLatestAlpacaOrderId("");
        strategy.setLastEvent("Swing Vault " + (executeRequested ? "monitoring" : "recommendation")
                + ": score=" + recommendation.strategyScore()
                + ", pullback=" + recommendation.pullbackPercent().toPlainString() + "% from recent high"
                + ", plannedEntry=$" + recommendation.plannedEntryPrice().toPlainString()
                + ", target=$" + recommendation.targetPrice().toPlainString()
                + ", R/R=" + recommendation.rewardRiskRatio().toPlainString()
                + ". No broker order was submitted.");
        return strategy;
    }
}
