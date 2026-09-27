package com.neuralarc.ui;

import com.neuralarc.model.PauseReason;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyStatus;

/**
 * Puts a strategy back to work when the broker proves it still has something to manage.
 *
 * <p>Two states strand a live position: a stale {@code FAILED}, and a {@code PAUSED} carrying
 * {@link PauseReason#SYSTEM_ERROR} — the app's own poll error, not a decision anyone made. Either way
 * the shares are still at the broker and nothing is watching their stop loss, which is the worst
 * state for a position to sit in. An operator's own pause is never touched.
 */
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
        // A system-error pause is the app's own failure, not the operator's choice, so a position that
        // survived it should go back to being managed rather than sit unwatched behind "System Error".
        boolean systemErrorPause = strategy.status() == StrategyStatus.PAUSED
                && strategy.pauseReason() == PauseReason.SYSTEM_ERROR;
        if (!staleFailedStatus && !staleFailedState && !systemErrorPause) {
            return false;
        }
        strategy.setStatus(StrategyStatus.ACTIVE);
        strategy.setPauseReason(PauseReason.NONE);
        if (staleFailedState || systemErrorPause) {
            strategy.setCurrentState(hasPosition ? StrategyLifecycleState.BASE_BUY_FILLED : StrategyLifecycleState.BASE_BUY_PLACED);
        }
        if (brokerOrderStatus != null && !brokerOrderStatus.isBlank()) {
            strategy.setLatestOrderStatus(brokerOrderStatus);
        } else if (hasPosition) {
            strategy.setLatestOrderStatus("filled");
        }
        strategy.clearLastError();
        strategy.setLastEvent(systemErrorPause
                ? "Resumed after a poll error: the broker still holds this position"
                : "Recovered active exposure from broker snapshot");
        return true;
    }
}

