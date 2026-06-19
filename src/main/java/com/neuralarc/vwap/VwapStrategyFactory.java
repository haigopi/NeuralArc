package com.neuralarc.vwap;

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

public final class VwapStrategyFactory {
    public Strategy toStrategy(VwapRecommendation recommendation, String workspaceId, boolean executeRequested, int pollingSeconds) {
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
                "VWAP_DESK: " + recommendation.symbol() + " " + recommendation.mode().name(),
                config,
                recommendation.mode()
        );
        strategy.setWorkspaceId(workspaceId);
        strategy.setStatus(StrategyStatus.CREATED);
        strategy.setCurrentState(StrategyLifecycleState.CREATED);
        strategy.setLatestOrderStatus(executeRequested ? "VWAP_MONITORING" : "VWAP_RECOMMENDED");
        strategy.setLatestAlpacaOrderId("");
        strategy.setLastEvent("VWAP Desk " + (executeRequested ? "monitoring" : "recommendation")
                + ": score=" + recommendation.strategyScore()
                + ", discount=" + recommendation.discountPercent().toPlainString() + "% below VWAP"
                + ", plannedEntry=$" + recommendation.plannedEntryPrice().toPlainString()
                + ", target(VWAP)=$" + recommendation.targetPrice().toPlainString()
                + ". No broker order was submitted.");
        return strategy;
    }
}
