package com.neuralarc.ui;

import com.neuralarc.model.PauseReason;
import com.neuralarc.model.Position;
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

class StrategyTablePresenterTest {
    private final StrategyTablePresenter presenter = new StrategyTablePresenter();

    @Test
    void manuallyCanceledPauseShowsManualRestartStatus() {
        Strategy strategy = strategy();
        strategy.setStatus(StrategyStatus.PAUSED);
        strategy.setCurrentState(StrategyLifecycleState.PAUSED);
        strategy.setPauseReason(PauseReason.MANUAL_LIMIT_BUY_CANCELED);

        String label = presenter.displayStatusLabel(strategy, false, false, false);

        assertEquals("Cancelled by user. Waiting for manual restart.", label);
    }

    @Test
    void activeFilledBaseBuyShowsWaitingForNextRule() {
        Strategy strategy = strategy();
        strategy.setStatus(StrategyStatus.ACTIVE);
        strategy.setCurrentState(StrategyLifecycleState.BASE_BUY_FILLED);

        String label = presenter.displayStatusLabel(strategy, false, false, false);

        assertEquals("Base Buy Filled - Waiting on next rule", label);
    }

    @Test
    void failedExpiredShowsExpiredStatus() {
        Strategy strategy = strategy();
        strategy.setStatus(StrategyStatus.FAILED);
        strategy.setCurrentState(StrategyLifecycleState.FAILED);
        strategy.setLatestOrderStatus("expired");

        String label = presenter.displayStatusLabel(strategy, false, false, false);

        assertEquals("Expired", label);
    }

    @Test
    void valueAtUsesLastSellPriceAndRealizedPnlForClosedPosition() {
        Position position = new Position("AAPL");

        assertEquals("120.00", presenter.valueAt(
                strategy(),
                position,
                new BigDecimal("120.00"),
                new BigDecimal("200.00"),
                4,
                "Completed",
                "Paper"
        ));
        assertEquals("200.00", presenter.valueAt(
                strategy(),
                position,
                new BigDecimal("120.00"),
                new BigDecimal("200.00"),
                6,
                "Completed",
                "Paper"
        ));
    }

    @Test
    void valueAtShowsNegativeRealizedPnlForClosedPosition() {
        Position position = new Position("AAPL");

        assertEquals("-45.67", presenter.valueAt(
                strategy(),
                position,
                new BigDecimal("95.00"),
                new BigDecimal("-45.67"),
                6,
                "Completed",
                "Paper"
        ));
    }

    private Strategy strategy() {
        return new Strategy(
                UUID.randomUUID().toString(),
                "AAPL Strategy",
                "AAPL",
                StrategyMode.PAPER,
                StrategyStatus.ACTIVE,
                StrategyLifecycleState.BASE_BUY_PLACED,
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
