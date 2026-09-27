package com.neuralarc.ui;

import com.neuralarc.model.PauseReason;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FailedStrategyExposureRecoveryTest {

    @Test
    void recoversFailedStrategyWhenBrokerPositionExists() {
        Strategy strategy = failedStrategy();
        strategy.setLastError("stale error");

        boolean changed = FailedStrategyExposureRecovery.recover(strategy, true, false, "");

        assertTrue(changed);
        assertEquals(StrategyStatus.ACTIVE, strategy.status());
        assertEquals(StrategyLifecycleState.BASE_BUY_FILLED, strategy.currentState());
        assertEquals("filled", strategy.latestOrderStatus());
        assertNull(strategy.lastError());
    }

    @Test
    void recoversFailedStrategyWhenOpenOrderExistsAndKeepsBrokerStatus() {
        Strategy strategy = failedStrategy();

        boolean changed = FailedStrategyExposureRecovery.recover(strategy, false, true, "new");

        assertTrue(changed);
        assertEquals(StrategyStatus.ACTIVE, strategy.status());
        assertEquals(StrategyLifecycleState.BASE_BUY_PLACED, strategy.currentState());
        assertEquals("new", strategy.latestOrderStatus());
    }

    @Test
    void doesNotChangeFailedStrategyWithoutBrokerExposure() {
        Strategy strategy = failedStrategy();

        boolean changed = FailedStrategyExposureRecovery.recover(strategy, false, false, "");

        assertFalse(changed);
        assertEquals(StrategyStatus.FAILED, strategy.status());
        assertEquals(StrategyLifecycleState.FAILED, strategy.currentState());
    }

    @Test
    void recoversWhenLifecycleStateIsFailedEvenIfStatusIsActive() {
        Strategy strategy = failedStrategy();
        strategy.setStatus(StrategyStatus.ACTIVE);
        strategy.setCurrentState(StrategyLifecycleState.FAILED);

        boolean changed = FailedStrategyExposureRecovery.recover(strategy, true, false, "");

        assertTrue(changed);
        assertEquals(StrategyStatus.ACTIVE, strategy.status());
        assertEquals(StrategyLifecycleState.BASE_BUY_FILLED, strategy.currentState());
        assertEquals("filled", strategy.latestOrderStatus());
    }

    @Test
    void aSystemErrorPauseIsLiftedWhenTheBrokerStillHoldsThePosition() {
        // The PN / CURI case: a poll blew up, the row read "System Error", and the shares sat unwatched.
        Strategy strategy = pausedStrategy(PauseReason.SYSTEM_ERROR);

        assertTrue(FailedStrategyExposureRecovery.recover(strategy, true, false, "filled"));

        assertEquals(StrategyStatus.ACTIVE, strategy.status());
        assertEquals(PauseReason.NONE, strategy.pauseReason());
        assertEquals(StrategyLifecycleState.BASE_BUY_FILLED, strategy.currentState());
        assertTrue(strategy.lastEvent().contains("still holds this position"));
        assertNull(strategy.lastError());
    }

    @Test
    void aSystemErrorPauseWithOnlyAWorkingOrderResumesToo() {
        Strategy strategy = pausedStrategy(PauseReason.SYSTEM_ERROR);

        assertTrue(FailedStrategyExposureRecovery.recover(strategy, false, true, "new"));

        assertEquals(StrategyLifecycleState.BASE_BUY_PLACED, strategy.currentState());
        assertEquals("new", strategy.latestOrderStatus());
    }

    @Test
    void anOperatorsOwnPauseIsNeverUndone() {
        assertFalse(FailedStrategyExposureRecovery.recover(pausedStrategy(PauseReason.USER_PAUSED), true, false, "filled"),
                "a strategy someone paused on purpose must stay paused");
        assertFalse(FailedStrategyExposureRecovery.recover(
                pausedStrategy(PauseReason.MANUAL_LIMIT_BUY_CANCELED), true, false, "filled"));
    }

    @Test
    void aSystemErrorPauseWithNothingAtTheBrokerStaysPaused() {
        Strategy strategy = pausedStrategy(PauseReason.SYSTEM_ERROR);

        assertFalse(FailedStrategyExposureRecovery.recover(strategy, false, false, ""));

        assertEquals(StrategyStatus.PAUSED, strategy.status());
    }

    private Strategy pausedStrategy(PauseReason reason) {
        Strategy strategy = failedStrategy();
        strategy.setStatus(StrategyStatus.PAUSED);
        strategy.setCurrentState(StrategyLifecycleState.PAUSED);
        strategy.setPauseReason(reason);
        strategy.setLastError("Read timed out");
        return strategy;
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
                10,
                new BigDecimal("95.00"),
                5,
                new BigDecimal("90.00"),
                5,
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

