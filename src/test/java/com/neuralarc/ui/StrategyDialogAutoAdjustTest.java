package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Auto Adjust Risk &amp; Stop Loss sub-section is shown/enabled only when both Stop Loss and Loss
 * Buy Levels are enabled. The dialog itself is a top-level window (not constructible headless), so the
 * gating rule is verified through its static seam.
 */
class StrategyDialogAutoAdjustTest {
    @Test
    void sectionAvailableOnlyWhenBothRiskControlsEnabled() {
        assertTrue(StrategyDialog.autoAdjustAvailable(true, true));
        assertFalse(StrategyDialog.autoAdjustAvailable(false, true));
        assertFalse(StrategyDialog.autoAdjustAvailable(true, false));
        assertFalse(StrategyDialog.autoAdjustAvailable(false, false));
    }
}
