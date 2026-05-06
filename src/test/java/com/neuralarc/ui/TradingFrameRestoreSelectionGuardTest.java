package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradingFrameRestoreSelectionGuardTest {

    @Test
    void allowsSelectingFirstRowWhenStrategiesAndVisibleRowsExist() {
        assertTrue(TradingFrame.canSelectFirstRestoredRow(1, 1));
        assertTrue(TradingFrame.canSelectFirstRestoredRow(5, 2));
    }

    @Test
    void blocksSelectingFirstRowWhenNoVisibleRows() {
        assertFalse(TradingFrame.canSelectFirstRestoredRow(3, 0));
    }

    @Test
    void blocksSelectingFirstRowWhenNoStrategies() {
        assertFalse(TradingFrame.canSelectFirstRestoredRow(0, 4));
        assertFalse(TradingFrame.canSelectFirstRestoredRow(0, 0));
    }
}

