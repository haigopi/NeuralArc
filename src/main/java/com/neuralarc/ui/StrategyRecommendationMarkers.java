package com.neuralarc.ui;

import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyEntrySource;

final class StrategyRecommendationMarkers {
    private StrategyRecommendationMarkers() {
    }

    static boolean isScannerRecommendationRow(Strategy strategy) {
        return StrategyEntrySource.isScannerRecommendation(strategy);
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
        if (StrategyEntrySource.isManualImportedPendingRow(strategy)) {
            return "Manual addition";
        }
        return "";
    }
}
