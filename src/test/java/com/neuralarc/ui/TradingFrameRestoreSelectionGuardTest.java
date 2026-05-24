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
import java.util.UUID;

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

    @Test
    void keepsExpiredFailedStrategyVisibleInCurrentGrid() {
        Strategy strategy = failedStrategy();
        strategy.setLatestOrderStatus("expired");
        strategy.setResubmitOnExpiryEnabled(true);

        assertTrue(TradingFrame.includeFailedStrategyInCurrentTab(strategy));
    }

    @Test
    void keepsInvalidFailedStrategyVisibleInCurrentGrid() {
        Strategy strategy = failedStrategy();
        strategy.setLatestOrderStatus("invalid");

        assertTrue(TradingFrame.includeFailedStrategyInCurrentTab(strategy));
    }

    @Test
    void hidesOrdinaryFailedStrategyWithoutCurrentGridReason() {
        Strategy strategy = failedStrategy();
        strategy.setLatestOrderStatus("api_error");

        assertFalse(TradingFrame.includeFailedStrategyInCurrentTab(strategy));
    }

    private Strategy failedStrategy() {
        return new Strategy(
                UUID.randomUUID().toString(),
                "AAPL Strategy",
                "AAPL",
                StrategyMode.PAPER,
                StrategyStatus.FAILED,
                StrategyLifecycleState.FAILED,
                new BigDecimal("100.00"),
                1,
                new BigDecimal("95.00"),
                1,
                new BigDecimal("90.00"),
                1,
                true,
                StopLossType.FIXED_PRICE,
                new BigDecimal("85.00"),
                BigDecimal.ZERO,
                false,
                BigDecimal.ZERO,
                true,
                new BigDecimal("120.00"),
                new BigDecimal("100.00"),
                true,
                false,
                ProfitHoldType.PERCENT_TRAILING,
                new BigDecimal("5.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                30,
                new BigDecimal("10000.00"),
                5,
                Instant.now(),
                Instant.now()
        );
    }
}
