package com.neuralarc.ui;

import com.neuralarc.model.Strategy;

final class StrategyRecommendationMarkers {
    private StrategyRecommendationMarkers() {
    }

    static boolean isScannerRecommendationRow(Strategy strategy) {
        if (strategy == null || strategy.latestOrderStatus() == null) {
            return false;
        }
        String status = strategy.latestOrderStatus().trim().toUpperCase(java.util.Locale.ROOT);
        return status.startsWith("GAP_ROCKET_")
                || status.startsWith("ORB_")
                || status.startsWith("DIP_HUNTER_")
                || status.startsWith("VWAP_")
                || status.startsWith("SWING_")
                || status.startsWith("RANGE_RIDER_")
                || status.startsWith("EARNINGS_HUNTER_")
                || status.startsWith("PROFIT_SHIELD_");
    }

    static String sourceLabel(Strategy strategy) {
        if (strategy == null || strategy.latestOrderStatus() == null) {
            return "";
        }
        String status = strategy.latestOrderStatus().trim().toUpperCase(java.util.Locale.ROOT);
        if (status.startsWith("GAP_ROCKET_")) {
            return "Gap and go strategy";
        }
        if (status.startsWith("ORB_")) {
            return "ORB Engine strategy";
        }
        if (status.startsWith("DIP_HUNTER_")) {
            return "Dip Hunter strategy";
        }
        if (status.startsWith("VWAP_")) {
            return "VWAP Desk strategy";
        }
        if (status.startsWith("SWING_")) {
            return "Swing Vault strategy";
        }
        if (status.startsWith("RANGE_RIDER_")) {
            return "Range Rider strategy";
        }
        if (status.startsWith("EARNINGS_HUNTER_")) {
            return "Earnings Hunter strategy";
        }
        if (status.startsWith("PROFIT_SHIELD_")) {
            return "Profit Shield strategy";
        }
        return "";
    }
}
