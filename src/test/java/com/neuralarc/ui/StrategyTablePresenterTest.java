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

        assertEquals("Base Buy Filled - Monitoring next configured rule", label);
    }

    @Test
    void profitablePositionPrioritizesSellTriggerActiveRule() {
        Strategy strategy = strategy();
        strategy.setStatus(StrategyStatus.ACTIVE);
        strategy.setCurrentState(StrategyLifecycleState.STOP_LOSS_ACTIVE);

        Position position = new Position("AAPL");
        position.applyBuy(10, new BigDecimal("100.00"));
        position.setLastPrice(new BigDecimal("120.00"));

        String label = presenter.displayStatusLabel(strategy, position, false, false, false);

        assertEquals("Sell trigger active @ $120.00 | Current $120.00 - monitoring for sell trigger", label);
    }

    @Test
    void losingPositionShowsStopLossActiveRuleWhenEnabled() {
        Strategy strategy = strategy();
        strategy.setStatus(StrategyStatus.ACTIVE);
        strategy.setCurrentState(StrategyLifecycleState.STOP_LOSS_ACTIVE);

        Position position = new Position("AAPL");
        position.applyBuy(10, new BigDecimal("100.00"));
        position.setLastPrice(new BigDecimal("92.50"));

        String label = presenter.displayStatusLabel(strategy, position, false, false, false);

        assertEquals("Stop loss active @ $85.00 | Current $92.50 - monitoring downside protection", label);
    }

    @Test
    void profitHoldStatusShowsTrailingConfiguration() {
        Strategy strategy = strategy();
        strategy.setStatus(StrategyStatus.ACTIVE);
        strategy.setCurrentState(StrategyLifecycleState.PROFIT_HOLD_ACTIVE);
        strategy.setProfitHoldEnabled(true);
        strategy.setProfitHoldType(ProfitHoldType.PERCENT_TRAILING);
        strategy.setProfitHoldPercent(new BigDecimal("5.00"));

        Position position = new Position("AAPL");
        position.applyBuy(10, new BigDecimal("100.00"));
        position.setLastPrice(new BigDecimal("125.00"));

        String label = presenter.displayStatusLabel(strategy, position, false, false, false);

        assertEquals("Profit Hold active by 5.00% | Current $125.00 - monitoring trailing protection", label);
    }

    @Test
    void brokerUnavailableStatusShowsRetryingMessage() {
        Strategy strategy = strategy();
        strategy.setStatus(StrategyStatus.ACTIVE);
        strategy.setCurrentState(StrategyLifecycleState.SELL_PLACED);
        strategy.setLatestOrderStatus("failed_transport");

        String label = presenter.displayStatusLabel(strategy, false, false, false);

        assertEquals("Limit Sell Placed (Retrying: Unable to reach broker)", label);
    }

    @Test
    void brokerUnavailableStatusIsIgnoredWhenBrokerIsCurrentlyReachable() {
        Strategy strategy = strategy();
        strategy.setStatus(StrategyStatus.ACTIVE);
        strategy.setCurrentState(StrategyLifecycleState.SELL_PLACED);
        strategy.setLatestOrderStatus("failed_transport");

        String label = presenter.displayStatusLabel(strategy, new Position("AAPL"), false, true, false, false);

        assertEquals("Limit Sell Placed", label);
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

    @Test
    void valueAtSourceColumnsExposeEntryAndExitIndependently() {
        Strategy strategy = strategy();
        strategy.setName("I_AM_FEELING_LUCKY: AAPL Paper");
        strategy.setLastEvent("Alpaca Paper mode from I Am Feeling Lucky. Source top mover gainer.");
        strategy.setStatus(StrategyStatus.COMPLETED);
        strategy.setCurrentState(StrategyLifecycleState.COMPLETED);
        strategy.setLastTriggeredRuleType("MANUAL_EXIT");

        Object entryValue = presenter.valueAt(
                strategy,
                new Position("AAPL"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                9,
                "Active",
                "Paper"
        );
        Object exitValue = presenter.valueAt(
                strategy,
                new Position("AAPL"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                10,
                "Active",
                "Paper"
        );

        assertEquals("Lucky [Gainers]", entryValue);
        assertEquals("Manual Exit", exitValue);
    }

    @Test
    void entrySourceUsesLuckySourceFromNameWhenOrderSubmissionOverwritesLastEvent() {
        Strategy strategy = strategy();
        strategy.setName("I_AM_FEELING_LUCKY_GAINERS: AAPL Paper");
        strategy.setLastEvent("Stop loss sell submitted");

        Object entryValue = presenter.valueAt(
                strategy,
                new Position("AAPL"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                9,
                "Active",
                "Paper"
        );

        assertEquals("Lucky [Gainers]", entryValue);
    }

    @Test
    void entrySourceShowsLuckyLosersAndExitSourceStaysBlankForBuyRule() {
        Strategy strategy = strategy();
        strategy.setName("I_AM_FEELING_LUCKY_LOSERS: TSLA Paper");
        strategy.setLastEvent("Order BASE_BUY is ACCEPTED");
        strategy.setLastTriggeredRuleType("BASE_BUY");

        Object entryValue = presenter.valueAt(
                strategy,
                new Position("TSLA"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                9,
                "Limit Base Buy Placed",
                "Paper"
        );
        Object exitValue = presenter.valueAt(
                strategy,
                new Position("TSLA"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                10,
                "Limit Base Buy Placed",
                "Paper"
        );

        assertEquals("Lucky [Losers]", entryValue);
        assertEquals("", exitValue);
    }

    @Test
    void exitSourceStaysBlankUntilSellCompletes() {
        Strategy strategy = strategy();
        strategy.setStatus(StrategyStatus.ACTIVE);
        strategy.setCurrentState(StrategyLifecycleState.SELL_PLACED);
        strategy.setLastTriggeredRuleType("TARGET_SELL");

        Object exitValue = presenter.valueAt(
                strategy,
                new Position("AAPL"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                10,
                "Limit Sell Placed",
                "Paper"
        );

        assertEquals("", exitValue);
    }

    @Test
    void exitSourceStaysBlankForExpiredOrCancelledBuyOrder() {
        Strategy expired = strategy();
        expired.setStatus(StrategyStatus.FAILED);
        expired.setCurrentState(StrategyLifecycleState.FAILED);
        expired.setLatestOrderStatus("expired");

        Strategy cancelled = strategy();
        cancelled.setStatus(StrategyStatus.PAUSED);
        cancelled.setCurrentState(StrategyLifecycleState.PAUSED);
        cancelled.setPauseReason(PauseReason.MANUAL_LIMIT_BUY_CANCELED);

        assertEquals("", presenter.valueAt(expired, new Position("AAPL"), BigDecimal.ZERO, BigDecimal.ZERO, 10, "Expired", "Paper"));
        assertEquals("", presenter.valueAt(cancelled, new Position("AAPL"), BigDecimal.ZERO, BigDecimal.ZERO, 10, "Canceled", "Paper"));
    }

    @Test
    void sharesColumnShowsPendingBaseBuyQuantityBeforeFill() {
        Strategy strategy = strategy();
        strategy.setStatus(StrategyStatus.ACTIVE);
        strategy.setCurrentState(StrategyLifecycleState.BASE_BUY_PLACED);
        strategy.setBaseBuyQuantity(25);

        Object quantityValue = presenter.valueAt(
                strategy,
                new Position("AAPL"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                2,
                "Limit Base Buy Placed",
                "Paper"
        );

        assertEquals(25, quantityValue);
    }

    @Test
    void sharesColumnPrefersFilledPositionQuantity() {
        Strategy strategy = strategy();
        strategy.setStatus(StrategyStatus.ACTIVE);
        strategy.setCurrentState(StrategyLifecycleState.BASE_BUY_PLACED);
        strategy.setBaseBuyQuantity(25);
        Position position = new Position("AAPL");
        position.applyBuy(7, new BigDecimal("100.00"));

        Object quantityValue = presenter.valueAt(
                strategy,
                position,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                2,
                "Limit Base Buy Partially Filled",
                "Paper"
        );

        assertEquals(7, quantityValue);
    }

    @Test
    void statusShowsPendingBaseBuyPriceQuantityAndBrokerStatus() {
        Strategy strategy = strategy();
        strategy.setStatus(StrategyStatus.ACTIVE);
        strategy.setCurrentState(StrategyLifecycleState.BASE_BUY_PLACED);
        strategy.setBaseBuyQuantity(25);
        strategy.setLatestOrderStatus("new");

        String label = presenter.displayStatusLabel(strategy, new Position("AAPL"), false, true, false);

        assertEquals("Limit Base Buy Placed - @ $100.00/25 (New)", label);
    }

    @Test
    void statusShowsLimitSellPriceQuantityAndBrokerStatus() {
        Strategy strategy = strategy();
        strategy.setStatus(StrategyStatus.ACTIVE);
        strategy.setCurrentState(StrategyLifecycleState.SELL_PLACED);
        strategy.setLatestOrderStatus("new");
        Position position = new Position("AAPL");
        position.applyBuy(8, new BigDecimal("100.00"));

        String label = presenter.displayStatusLabel(strategy, position, false, true, false);

        assertEquals("Limit Sell Placed - @ $120.00/8 (New)", label);
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
