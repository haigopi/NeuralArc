package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PositionValidationHotspotLayoutTest {
    @Test
    void neverHitsWhenAffordanceIsNotShown() {
        assertFalse(PositionValidationHotspotLayout.isRefreshNowHotspot(200, 150, false));
        assertFalse(PositionValidationHotspotLayout.isRefreshNowHotspot(200, 199, false));
    }

    @Test
    void hitsWithinTheRightmostRefreshNowRegionWhenShown() {
        int cellWidth = 200;
        int start = cellWidth - PositionValidationHotspotLayout.REFRESH_NOW_WIDTH;

        assertTrue(PositionValidationHotspotLayout.isRefreshNowHotspot(cellWidth, start, true));
        assertTrue(PositionValidationHotspotLayout.isRefreshNowHotspot(cellWidth, cellWidth - 1, true));
    }

    @Test
    void missesOutsideTheRefreshNowRegionWhenShown() {
        int cellWidth = 200;
        int start = cellWidth - PositionValidationHotspotLayout.REFRESH_NOW_WIDTH;

        assertFalse(PositionValidationHotspotLayout.isRefreshNowHotspot(cellWidth, start - 1, true));
        assertFalse(PositionValidationHotspotLayout.isRefreshNowHotspot(cellWidth, 0, true));
        assertFalse(PositionValidationHotspotLayout.isRefreshNowHotspot(cellWidth, cellWidth, true));
    }

    @Test
    void narrowCellClampsStartToZero() {
        int cellWidth = 40; // narrower than REFRESH_NOW_WIDTH
        assertTrue(PositionValidationHotspotLayout.isRefreshNowHotspot(cellWidth, 0, true));
        assertTrue(PositionValidationHotspotLayout.isRefreshNowHotspot(cellWidth, cellWidth - 1, true));
    }
}
