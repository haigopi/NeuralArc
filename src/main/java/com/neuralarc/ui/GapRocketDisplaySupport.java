package com.neuralarc.ui;

import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyLifecycleState;

final class GapRocketDisplaySupport {
    private GapRocketDisplaySupport() {}

    static boolean suppressBrokerPosition(Strategy strategy) {
        if (strategy == null) {
            return false;
        }
        String latest = strategy.latestOrderStatus() == null ? "" : strategy.latestOrderStatus();
        if (latest.startsWith("GAP_ROCKET_") || latest.startsWith("DIP_HUNTER_") || latest.startsWith("VWAP_")
                || latest.startsWith("SWING_") || latest.startsWith("EARNINGS_HUNTER_")
                || latest.startsWith("RANGE_RIDER_") || latest.startsWith("PROFIT_SHIELD_")) {
            return true;
        }
        String name = strategy.name() == null ? "" : strategy.name().toUpperCase();
        if (!name.contains("GAP_ROCKET") && !name.contains("DIP_HUNTER") && !name.contains("VWAP_DESK")
                && !name.contains("SWING_VAULT") && !name.contains("EARNINGS_HUNTER")
                && !name.contains("RANGE_RIDER") && !name.contains("PROFIT_SHIELD")) {
            return false;
        }
        StrategyLifecycleState state = strategy.currentState();
        return state == StrategyLifecycleState.CREATED
                || state == StrategyLifecycleState.VALIDATED
                || state == StrategyLifecycleState.BASE_BUY_PLACED
                || state == StrategyLifecycleState.BASE_BUY_PARTIALLY_FILLED
                || state == StrategyLifecycleState.QUEUED_FOR_OPEN;
    }
}
