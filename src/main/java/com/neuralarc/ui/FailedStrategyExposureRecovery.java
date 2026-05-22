package com.neuralarc.ui;

import com.neuralarc.model.PauseReason;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyStatus;

final class FailedStrategyExposureRecovery {
    private FailedStrategyExposureRecovery() {
    }

    static boolean recover(
            Strategy strategy,
            boolean hasPosition,
            boolean hasOpenOrder,
            String brokerOrderStatus
    ) {
        if (strategy == null || (!hasPosition && !hasOpenOrder)) {
            return false;
        }
        boolean staleFailedStatus = strategy.status() == StrategyStatus.FAILED;
        boolean staleFailedState = strategy.currentState() == StrategyLifecycleState.FAILED;
        if (!staleFailedStatus && !staleFailedState) {
            return false;
        }
        strategy.setStatus(StrategyStatus.ACTIVE);
        strategy.setPauseReason(PauseReason.NONE);
        if (staleFailedState) {
            strategy.setCurrentState(hasPosition ? StrategyLifecycleState.BASE_BUY_FILLED : StrategyLifecycleState.BASE_BUY_PLACED);
        }
        if (brokerOrderStatus != null && !brokerOrderStatus.isBlank()) {
            strategy.setLatestOrderStatus(brokerOrderStatus);
        } else if (hasPosition) {
            strategy.setLatestOrderStatus("filled");
        }
        strategy.clearLastError();
        strategy.setLastEvent("Recovered active exposure from broker snapshot");
        return true;
    }
}

