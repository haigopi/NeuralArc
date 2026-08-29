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
                || ProfitShieldCoordinator.isPendingOrderPlacement(strategy)
                || isManualPendingImport(strategy);
    }

    private static boolean isManualPendingImport(Strategy strategy) {
        if (strategy == null) {
            return false;
        }
        String orderStatus = strategy.latestOrderStatus();
        if (!"PAPER_PENDING".equalsIgnoreCase(orderStatus) && !"LIVE_PENDING".equalsIgnoreCase(orderStatus)) {
            return false;
        }
        String name = strategy.name();
        return name != null && name.startsWith("MANUAL_ADDITION:");
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
