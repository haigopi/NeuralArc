package com.neuralarc.ui;

import com.neuralarc.model.Strategy;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;

final class PendingBaseBuyPlacementSupport {
    private static final BigDecimal TODAY_LOW_DISCOUNT_FACTOR = new BigDecimal("0.90");

    private PendingBaseBuyPlacementSupport() {
    }

    static boolean isPendingBaseBuyPlacement(Strategy strategy) {
        return GapAndGoCoordinator.isPendingOrderPlacement(strategy)
                || OrbCoordinator.isPendingOrderPlacement(strategy)
                || DipHunterCoordinator.isPendingOrderPlacement(strategy)
                || VwapCoordinator.isPendingOrderPlacement(strategy)
                || SwingCoordinator.isPendingOrderPlacement(strategy)
                || RangeRiderCoordinator.isPendingOrderPlacement(strategy)
                || EarningsHunterCoordinator.isPendingOrderPlacement(strategy)
                || ProfitShieldCoordinator.isPendingOrderPlacement(strategy);
    }

    static BigDecimal adjustedBaseBuyLimit(BigDecimal baseBuyLimitPrice, BigDecimal todayLow) {
        if (baseBuyLimitPrice == null || baseBuyLimitPrice.signum() <= 0
                || todayLow == null || todayLow.signum() <= 0
                || baseBuyLimitPrice.compareTo(todayLow) <= 0) {
            return Monetary.round(baseBuyLimitPrice);
        }
        return Monetary.round(todayLow.multiply(TODAY_LOW_DISCOUNT_FACTOR));
    }
}
