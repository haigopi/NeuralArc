package com.neuralarc.ui;

import com.neuralarc.model.PauseReason;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyStatus;
import com.neuralarc.util.BrokerOrderStatusUtil;

/**
 * Row predicates behind the portfolio bulk actions: which strategies a given action is allowed to
 * touch. Split out of {@link PortfolioActionsSupport} to keep that file within the class-size limit.
 */
final class PortfolioActionMatchers {
    private PortfolioActionMatchers() {
    }

    static boolean hasCancelablePendingLimitBuy(ManagedStrategy entry) {
        if (entry == null || entry.strategy == null || entry.strategy.status() != StrategyStatus.ACTIVE) {
            return false;
        }
        StrategyLifecycleState state = entry.strategy.currentState();
        return state == StrategyLifecycleState.BASE_BUY_PLACED
                || state == StrategyLifecycleState.BASE_BUY_PARTIALLY_FILLED
                || state == StrategyLifecycleState.BUY_LIMIT_1_PLACED
                || state == StrategyLifecycleState.BUY_LIMIT_1_PARTIALLY_FILLED
                || state == StrategyLifecycleState.BUY_LIMIT_2_PLACED
                || state == StrategyLifecycleState.BUY_LIMIT_2_PARTIALLY_FILLED;
    }

    static boolean hasCancelablePendingLimitSell(ManagedStrategy entry) {
        if (entry == null || entry.strategy == null || entry.strategy.status() != StrategyStatus.ACTIVE) {
            return false;
        }
        StrategyLifecycleState state = entry.strategy.currentState();
        return state == StrategyLifecycleState.SELL_PLACED
                || state == StrategyLifecycleState.SELL_PARTIALLY_FILLED;
    }

    static boolean isRemovableInactive(ManagedStrategy entry) {
        if (entry == null || entry.strategy == null) {
            return false;
        }
        StrategyStatus status = entry.strategy.status();
        if (status == StrategyStatus.COMPLETED) {
            return true;
        }
        if (status == StrategyStatus.PAUSED) {
            PauseReason pauseReason = entry.strategy.pauseReason();
            return pauseReason == PauseReason.MANUAL_LIMIT_BUY_CANCELED
                    || pauseReason == PauseReason.USER_PAUSED;
        }
        return false;
    }

    /**
     * A trade that has finished: nothing is held any more and no broker order is still working.
     * These rows only clutter the active grids - their fills stay in trade history after archiving.
     */
    static boolean isClosedPosition(ManagedStrategy entry) {
        if (entry == null || entry.strategy == null) {
            return false;
        }
        StrategyStatus status = entry.strategy.status();
        if (status == StrategyStatus.ARCHIVED) {
            return false;
        }
        if (entry.cachedPosition().getTotalShares() > 0) {
            return false;
        }
        if (isPendingOrderState(entry.strategy.currentState())) {
            return false;
        }
        return status == StrategyStatus.COMPLETED
                || status == StrategyStatus.STOPPED
                || entry.strategy.currentState() == StrategyLifecycleState.COMPLETED;
    }

    static boolean isResumeEligible(ManagedStrategy entry) {
        if (entry == null || entry.strategy == null) {
            return false;
        }
        return entry.strategy.status() == StrategyStatus.PAUSED;
    }

    static boolean isEligibleForManualSell(ManagedStrategy entry) {
        if (entry == null || entry.strategy == null) {
            return false;
        }
        StrategyStatus status = entry.strategy.status();
        if (status == StrategyStatus.COMPLETED
                || status == StrategyStatus.FAILED
                || status == StrategyStatus.STOPPED
                || status == StrategyStatus.ARCHIVED) {
            return false;
        }
        StrategyLifecycleState state = entry.strategy.currentState();
        if (isCanceledSellState(entry)) {
            return true;
        }
        return state != StrategyLifecycleState.COMPLETED
                && state != StrategyLifecycleState.FAILED
                && state != StrategyLifecycleState.STOPPED
                && state != StrategyLifecycleState.SELL_PLACED
                && state != StrategyLifecycleState.SELL_PARTIALLY_FILLED;
    }

    static boolean isCanceledSellState(ManagedStrategy entry) {
        if (entry == null || entry.strategy == null) {
            return false;
        }
        StrategyLifecycleState state = entry.strategy.currentState();
        if (state != StrategyLifecycleState.SELL_PLACED && state != StrategyLifecycleState.SELL_PARTIALLY_FILLED) {
            return false;
        }
        String normalized = BrokerOrderStatusUtil.normalize(entry.strategy.latestOrderStatus());
        return "canceled".equals(normalized) || "cancelled".equals(normalized);
    }

    static boolean isExpired(ManagedStrategy entry) {
        if (entry == null || entry.strategy == null) {
            return false;
        }
        if (!"expired".equals(BrokerOrderStatusUtil.normalize(entry.strategy.latestOrderStatus()))) {
            return false;
        }
        if (entry.strategy.status() == StrategyStatus.FAILED) {
            return true;
        }
        return entry.strategy.status() == StrategyStatus.ACTIVE && isPendingOrderState(entry.strategy.currentState());
    }

    static boolean isInvalidLocalRecord(ManagedStrategy entry) {
        if (entry == null || entry.strategy == null) {
            return false;
        }
        String normalized = BrokerOrderStatusUtil.normalize(entry.strategy.latestOrderStatus());
        return entry.strategy.status() == StrategyStatus.FAILED
                && ("invalid".equals(normalized) || "invalid_local".equals(normalized));
    }

    static boolean isPendingOrderState(StrategyLifecycleState state) {
        return state == StrategyLifecycleState.BASE_BUY_PLACED
                || state == StrategyLifecycleState.BASE_BUY_PARTIALLY_FILLED
                || state == StrategyLifecycleState.BUY_LIMIT_1_PLACED
                || state == StrategyLifecycleState.BUY_LIMIT_1_PARTIALLY_FILLED
                || state == StrategyLifecycleState.BUY_LIMIT_2_PLACED
                || state == StrategyLifecycleState.BUY_LIMIT_2_PARTIALLY_FILLED
                || state == StrategyLifecycleState.SELL_PLACED
                || state == StrategyLifecycleState.SELL_PARTIALLY_FILLED;
    }

    static boolean isTradeHistoryRecord(ManagedStrategy entry) {
        if (entry == null || entry.strategy == null) {
            return false;
        }
        StrategyStatus status = entry.strategy.status();
        return status == StrategyStatus.ARCHIVED
                || status == StrategyStatus.COMPLETED
                || status == StrategyStatus.FAILED
                || status == StrategyStatus.STOPPED;
    }
}
