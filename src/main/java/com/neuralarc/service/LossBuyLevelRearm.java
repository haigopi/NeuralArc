package com.neuralarc.service;

import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyStage;

import java.util.List;
import java.util.Optional;

/**
 * Decides the lifecycle state after a loss-buy level is edited on a strategy that already bought it.
 *
 * <p>The state says how far down the ladder the strategy has bought. An edit that moves a filled level
 * makes that level a fresh target, so the state steps back to the first level that is waiting again and
 * the row reads "Loss buy limit N active" instead of the stale "Buy Limit 2 Filled".
 */
final class LossBuyLevelRearm {
    private LossBuyLevelRearm() {
    }

    /** The state to move to, or empty when every configured level still matches what was bought. */
    static Optional<StrategyLifecycleState> stateAfterEdit(Strategy strategy, List<StrategyOrder> orders) {
        StrategyLifecycleState state = strategy.currentState();
        if (state != StrategyLifecycleState.BASE_BUY_FILLED
                && state != StrategyLifecycleState.BUY_LIMIT_1_FILLED
                && state != StrategyLifecycleState.BUY_LIMIT_2_FILLED) {
            return Optional.empty();
        }
        if (!strategy.lossBuyLevelsEnabled() || !StrategyStageSupport.isStageFilled(orders, StrategyStage.BASE_BUY)) {
            return Optional.empty();
        }
        StrategyLifecycleState target = state;
        if (levelConfigured(strategy.buyLimit1Quantity(), strategy.buyLimit1Price())
                && !StrategyStageSupport.isLossLevelFilled(strategy, orders, StrategyStage.BUY_LIMIT_1)) {
            target = StrategyLifecycleState.BASE_BUY_FILLED;
        } else if (levelConfigured(strategy.buyLimit2Quantity(), strategy.buyLimit2Price())
                && !StrategyStageSupport.isLossLevelFilled(strategy, orders, StrategyStage.BUY_LIMIT_2)
                && state == StrategyLifecycleState.BUY_LIMIT_2_FILLED) {
            target = StrategyLifecycleState.BUY_LIMIT_1_FILLED;
        }
        return target == state ? Optional.empty() : Optional.of(target);
    }

    private static boolean levelConfigured(int quantity, java.math.BigDecimal price) {
        return quantity > 0 && price != null && price.signum() > 0;
    }
}
