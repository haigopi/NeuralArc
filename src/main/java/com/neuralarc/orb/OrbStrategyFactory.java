package com.neuralarc.orb;

import com.neuralarc.model.ProfitControlMode;
import com.neuralarc.model.ProfitHoldType;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyConfig;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyStatus;
import com.neuralarc.model.ThresholdType;
import com.neuralarc.model.TimeInForce;
import com.neuralarc.model.TrailingType;

import java.math.BigDecimal;
import java.util.UUID;

public final class OrbStrategyFactory {
    public Strategy toStrategy(OrbRecommendation recommendation, String workspaceId, boolean armRequested, int pollingSeconds) {
        StrategyConfig config = new StrategyConfig(
                recommendation.symbol(),
                recommendation.plannedEntry(),
                1,
                true,
                recommendation.stop(),
                true,
                recommendation.target(),
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                0,
                false,
                false,
                BigDecimal.ZERO,
                Math.max(1, pollingSeconds),
                recommendation.mode() == com.neuralarc.model.StrategyMode.PAPER,
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
                false,
                TimeInForce.DAY
        );
        Strategy strategy = Strategy.fromConfig(UUID.randomUUID().toString(),
                "ORB_ENGINE: " + recommendation.symbol() + " " + recommendation.mode().name(), config, recommendation.mode());
        strategy.setWorkspaceId(workspaceId);
        strategy.setStatus(StrategyStatus.CREATED);
        strategy.setCurrentState(StrategyLifecycleState.CREATED);
        strategy.setLatestOrderStatus(armRequested ? "ORB_ARMED" : "ORB_RECOMMENDED");
        strategy.setLatestAlpacaOrderId("");
        strategy.setLastEvent("ORB " + (armRequested ? "armed" : "recommendation")
                + ": score=" + recommendation.score()
                + ", rangeHigh=$" + recommendation.rangeHigh().toPlainString()
                + ", rangeLow=$" + recommendation.rangeLow().toPlainString()
                + ", entry=$" + recommendation.plannedEntry().toPlainString()
                + ", stop=$" + recommendation.stop().toPlainString()
                + ", target=$" + recommendation.target().toPlainString()
                + ". No broker order was submitted.");
        return strategy;
    }
}
