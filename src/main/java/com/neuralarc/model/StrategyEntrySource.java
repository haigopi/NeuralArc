package com.neuralarc.model;

import java.util.List;
import java.util.Locale;

/**
 * Where a strategy's entry came from, as far as the app can tell from what it stores.
 *
 * <p>The grid's Entry Source column and the base-buy price guard must agree on this: the guard only
 * overrides prices the app planned itself, never one the operator typed. A strategy is "manually
 * added" when nothing marks it as a scanner recommendation, a named engine strategy, a re-entry, a
 * Smart Pick, a broker sync or a live promotion.
 */
public final class StrategyEntrySource {
    private static final List<String> SCANNER_STATUS_PREFIXES = List.of(
            "GAP_ROCKET_", "ORB_", "DIP_HUNTER_", "VWAP_", "SWING_", "RANGE_RIDER_",
            "EARNINGS_HUNTER_", "PROFIT_SHIELD_");
    private static final List<String> NAME_MARKERS = List.of(
            "gap_rocket", "orb_engine", "swing_vault", "vwap_desk", "range_rider", "dip_hunter",
            "profit_shield", "earnings_hunter", "history_reentry");

    private StrategyEntrySource() {
    }

    /** A scanner-planned row, or a pasted manual-import row still waiting to be placed. */
    public static boolean isScannerRecommendation(Strategy strategy) {
        if (strategy == null || strategy.latestOrderStatus() == null) {
            return false;
        }
        String status = normalizedStatus(strategy);
        return SCANNER_STATUS_PREFIXES.stream().anyMatch(status::startsWith)
                || isManualImportedPendingRow(strategy);
    }

    public static boolean isManualImportedPendingRow(Strategy strategy) {
        if (strategy == null || strategy.latestOrderStatus() == null) {
            return false;
        }
        String status = normalizedStatus(strategy);
        if (!"PAPER_PENDING".equals(status) && !"LIVE_PENDING".equals(status)) {
            return false;
        }
        String name = strategy.name();
        return name != null && name.startsWith("MANUAL_ADDITION:");
    }

    public static boolean hasEngineNameMarker(String normalizedName) {
        return normalizedName != null && NAME_MARKERS.stream().anyMatch(normalizedName::contains);
    }

    public static boolean isSmartPicks(String combined) {
        return combined.contains("smart_picks")
                || combined.contains("smart picks")
                // Legacy strategies persisted before the Smart Picks rename.
                || combined.contains("i_am_feeling_lucky")
                || combined.contains("i am feeling lucky");
    }

    public static boolean isBrokerSynced(String combined) {
        return combined.contains("remote strategy")
                || combined.contains("synced from alpaca")
                || combined.contains("remote alpaca")
                || combined.contains("remote state")
                || combined.contains("broker direct");
    }

    public static boolean isPromotedLive(String combined) {
        return combined.contains("promoted live")
                || combined.contains("promotion to live")
                || combined.contains("after live promotion");
    }

    /** True when the operator chose this strategy's prices: nothing else in the app planned them. */
    public static boolean isManuallyAdded(Strategy strategy) {
        if (strategy == null || isScannerRecommendation(strategy)) {
            return false;
        }
        String name = lower(strategy.name());
        String combined = name + " " + lower(strategy.lastEvent());
        return !hasEngineNameMarker(name)
                && !isSmartPicks(combined)
                && !isBrokerSynced(combined)
                && !isPromotedLive(combined);
    }

    private static String normalizedStatus(Strategy strategy) {
        return strategy.latestOrderStatus().trim().toUpperCase(Locale.ROOT);
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase();
    }
}
