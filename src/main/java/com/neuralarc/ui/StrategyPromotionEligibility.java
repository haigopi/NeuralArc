package com.neuralarc.ui;

import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyStatus;
import com.neuralarc.util.BrokerOrderStatusUtil;

final class StrategyPromotionEligibility {
    private StrategyPromotionEligibility() {
    }

    static boolean canPromoteToLive(Strategy strategy) {
        if (strategy == null || strategy.mode() != StrategyMode.PAPER) {
            return false;
        }
        return canPromotePaperStatus(strategy.status(), strategy.latestOrderStatus());
    }

    static boolean canPromoteToLive(StrategyStatus status, boolean paperMode, boolean busy, String latestOrderStatus) {
        return paperMode && !busy && canPromotePaperStatus(status, latestOrderStatus);
    }

    private static boolean canPromotePaperStatus(StrategyStatus status, String latestOrderStatus) {
        if (status == StrategyStatus.ACTIVE || status == StrategyStatus.PAUSED) {
            return true;
        }
        return status == StrategyStatus.FAILED
                && "expired".equals(BrokerOrderStatusUtil.normalize(latestOrderStatus));
    }
}
