package com.neuralarc.ui;

import com.neuralarc.model.ProfitHoldType;
import com.neuralarc.model.StopLossType;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyRowVisibilityTest {
    @Test
    void archivedAndStoppedRowsAreNeverDrawn() {
        assertTrue(StrategyRowVisibility.hiddenFromCurrentTab(strategy(StrategyStatus.ARCHIVED, false)));
        assertTrue(StrategyRowVisibility.hiddenFromCurrentTab(strategy(StrategyStatus.STOPPED, false)));
    }

    @Test
    void aCompletedRowThatRestartsAfterExitIsHiddenUntilItCyclesAgain() {
        // The NVDA case: this row held the whole broker position while being invisible.
        assertTrue(StrategyRowVisibility.hiddenFromCurrentTab(strategy(StrategyStatus.COMPLETED, true)));
    }

    @Test
    void aCompletedRowThatDoesNotRestartStaysOnScreen() {
        assertFalse(StrategyRowVisibility.hiddenFromCurrentTab(strategy(StrategyStatus.COMPLETED, false)));
    }

    @Test
    void liveStatesAreNotReportedAsHidden() {
        assertFalse(StrategyRowVisibility.hiddenFromCurrentTab(strategy(StrategyStatus.ACTIVE, true)));
        assertFalse(StrategyRowVisibility.hiddenFromCurrentTab(strategy(StrategyStatus.CREATED, false)));
        assertFalse(StrategyRowVisibility.hiddenFromCurrentTab(strategy(StrategyStatus.PAUSED, false)));
        assertFalse(StrategyRowVisibility.hiddenFromCurrentTab(strategy(StrategyStatus.FAILED, false)));
    }

    @Test
    void anAbsentStrategyCannotBeDrawn() {
        assertTrue(StrategyRowVisibility.hiddenFromCurrentTab(null));
    }

    private static Strategy strategy(StrategyStatus status, boolean restartAfterExit) {
        return new Strategy(
                "id-" + status, status + " row", "NVDA", StrategyMode.LIVE, status,
                StrategyLifecycleState.CREATED, new BigDecimal("200.00"), 1,
                BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, false, StopLossType.FIXED_PRICE,
                BigDecimal.ZERO, BigDecimal.ZERO, false, BigDecimal.ZERO, false, BigDecimal.ZERO,
                BigDecimal.valueOf(100), true, false, false, ProfitHoldType.PERCENT_TRAILING,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, restartAfterExit,
                10, new BigDecimal("2000.00"), 60, Instant.now(), Instant.now());
    }
}
