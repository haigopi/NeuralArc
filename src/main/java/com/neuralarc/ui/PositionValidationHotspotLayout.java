package com.neuralarc.ui;

/**
 * Pure hit-test for the "Refresh Now" affordance shown inside the polling cell when validation is
 * Warning-Paused. Mirrors {@link StrategyGridActionLayout}'s click-region pattern for the Actions
 * column.
 */
final class PositionValidationHotspotLayout {
    static final int REFRESH_NOW_WIDTH = 90;

    private PositionValidationHotspotLayout() {
    }

    static boolean isRefreshNowHotspot(int cellWidth, int xInCell, boolean showRefreshNowAffordance) {
        if (!showRefreshNowAffordance) {
            return false;
        }
        int start = Math.max(0, cellWidth - REFRESH_NOW_WIDTH);
        return xInCell >= start && xInCell < cellWidth;
    }
}
