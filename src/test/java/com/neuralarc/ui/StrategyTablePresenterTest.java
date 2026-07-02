package com.neuralarc.ui;

import com.neuralarc.model.PauseReason;
import com.neuralarc.model.Position;
import com.neuralarc.model.ProfitControlMode;
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
    void activeFilledBaseBuyShowsConfiguredLossBuyRule() {
        Strategy strategy = strategy();
        strategy.setStatus(StrategyStatus.ACTIVE);
        strategy.setCurrentState(StrategyLifecycleState.BASE_BUY_FILLED);

        String label = presenter.displayStatusLabel(strategy, false, false, false);

        assertEquals("Loss buy limit 1 active @ $95.00 - monitoring loss buy", label);
    }

    @Test
    void activeFilledBaseBuyWithoutLossPreventionShowsSellTriggerRule() {
        Strategy strategy = strategy();
        strategy.setStatus(StrategyStatus.ACTIVE);
        strategy.setCurrentState(StrategyLifecycleState.BASE_BUY_FILLED);
        strategy.setLossBuyLevelsEnabled(false);
        strategy.setAutomatedStopLossEnabled(false);

        String label = presenter.displayStatusLabel(strategy, false, false, false);

        assertEquals("Sell trigger active @ $120.00 - monitoring for sell trigger", label);
    }

    @Test
    void activeFilledBaseBuyWithAutomaticStopSellShowsAutomaticStopSellRule() {
        Strategy strategy = strategy();
        strategy.setStatus(StrategyStatus.ACTIVE);
        strategy.setCurrentState(StrategyLifecycleState.BASE_BUY_FILLED);
        strategy.setLossBuyLevelsEnabled(false);
        strategy.setAutomatedStopLossEnabled(false);
        strategy.setProfitControlMode(ProfitControlMode.AUTOMATIC_STOP_SELL);
        strategy.setAutomaticStopSellThreshold(new BigDecimal("10.00"));

        String label = presenter.displayStatusLabel(strategy, false, false, false);

        assertEquals("Automatic stop sell active after $10.00 profit - monitoring profit threshold", label);
    }

    @Test
    void manualLimitBuyPendingStatusIsVisibleInGridStatus() {
        Strategy strategy = strategy();
        strategy.setStatus(StrategyStatus.ACTIVE);
        strategy.setCurrentState(StrategyLifecycleState.BASE_BUY_FILLED);
        strategy.setLastTriggeredRuleType("MANUAL_BUY");
        strategy.setLastEvent("Manual limit buy order submitted");
        strategy.setLatestOrderStatus("pending_new");

        String label = presenter.displayStatusLabel(strategy, false, true, false);

        assertEquals("Manual Limit Buy Pending Fill (Pending New)", label);
    }

    @Test
    void manualLimitBuyPendingStatusShowsPriceAndQuantityWhenAvailable() {
        Strategy strategy = strategy();
        strategy.setStatus(StrategyStatus.ACTIVE);
        strategy.setCurrentState(StrategyLifecycleState.BASE_BUY_FILLED);
        strategy.setLastTriggeredRuleType("MANUAL_BUY");
        strategy.setLastEvent("Manual limit buy order submitted");
        strategy.setLatestOrderStatus("pending_new");

        String label = presenter.displayStatusLabel(
                strategy,
                new Position("AAPL"),
                false,
                true,
                false,
                false,
                BigDecimal.ZERO,
                new StrategyTablePresenter.PendingOrderSummary(new BigDecimal("98.75"), new BigDecimal("3"))
        );

        assertEquals("Manual Limit Buy Pending Fill - @ $98.75/3 (Pending New)", label);
    }

    @Test
    void heldPositionIsShownAlongsidePendingLimitBuy() {
        Strategy strategy = strategy();
        strategy.setStatus(StrategyStatus.ACTIVE);
        strategy.setCurrentState(StrategyLifecycleState.BASE_BUY_FILLED);
        strategy.setLastEvent("Manual limit buy order submitted");
        strategy.setLatestOrderStatus("accepted");
        // Note: lastTriggeredRuleType intentionally NOT MANUAL_BUY — the pending order summary alone
        // must drive the pending-buy presentation (robust across restart/sync).
        strategy.setLastTriggeredRuleType("BASE_BUY");
        Position position = new Position("META");
        position.applyBuy(1, new BigDecimal("100.00"));

        String label = presenter.displayStatusLabel(
                strategy,
                position,
                false,
                true,
                false,
                false,
                BigDecimal.ZERO,
                new StrategyTablePresenter.PendingOrderSummary(new BigDecimal("98.75"), new BigDecimal("2"))
        );

        assertEquals("Position: 1 filled — Manual Limit Buy Pending Fill - @ $98.75/2 (Accepted)", label);
    }

    @Test
    void manualMarketBuyPendingStatusIsVisibleInGridStatus() {
        Strategy strategy = strategy();
        strategy.setStatus(StrategyStatus.ACTIVE);
        strategy.setCurrentState(StrategyLifecycleState.BASE_BUY_FILLED);
        strategy.setLastTriggeredRuleType("MANUAL_BUY");
        strategy.setLastEvent("Manual market buy order submitted");
        strategy.setLatestOrderStatus("new");

        String label = presenter.displayStatusLabel(strategy, false, true, false);

        assertEquals("Manual Market Buy Pending Fill (New)", label);
    }

    @Test
    void manualMarketBuyPendingStatusShowsQuantityWhenAvailable() {
        Strategy strategy = strategy();
        strategy.setStatus(StrategyStatus.ACTIVE);
        strategy.setCurrentState(StrategyLifecycleState.BASE_BUY_FILLED);
        strategy.setLastTriggeredRuleType("MANUAL_BUY");
        strategy.setLastEvent("Manual market buy order submitted");
        strategy.setLatestOrderStatus("new");

        String label = presenter.displayStatusLabel(
                strategy,
                new Position("AAPL"),
                false,
                true,
                false,
                false,
                BigDecimal.ZERO,
                new StrategyTablePresenter.PendingOrderSummary(BigDecimal.ZERO, new BigDecimal("4"))
        );

        assertEquals("Manual Market Buy Pending Fill - Qty 4 (New)", label);
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

        assertEquals("Sell trigger active @ $120.00 - monitoring for sell trigger", label);
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

        assertEquals("Stop loss active @ $85.00 - monitoring downside protection", label);
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

        assertEquals("Expired - no auto extension set; manual reposition required", label);
    }

    @Test
    void failedExpiredShowsExpiredStatusWithWaitingBuyRule() {
        Strategy strategy = strategy();
        strategy.setStatus(StrategyStatus.FAILED);
        strategy.setCurrentState(StrategyLifecycleState.FAILED);
        strategy.setLatestOrderStatus("expired");
        strategy.setLastTriggeredRuleType("BUY_RULE");
        strategy.setResubmitOnExpiryEnabled(true);

        String label = presenter.displayStatusLabel(strategy, false, false, false);

        assertEquals(
                "Expired (Limit Base Buy Placed - waiting to fill) - auto extension enabled; "
                        + "after polling or closed-market refresh detects expiry, a guarded base limit buy can be reposted. "
                        + "Example: base $100.00 is kept when quote/previous close/yesterday low are above it; "
                        + "weak indicators reduce it by 2.00% and it is never increased automatically",
                label
        );
    }

    @Test
    void failedExpiredShowsExpiredStatusWithWaitingLifecycleWhenStillPresent() {
        Strategy strategy = strategy();
        strategy.setStatus(StrategyStatus.FAILED);
        strategy.setCurrentState(StrategyLifecycleState.BUY_LIMIT_1_PLACED);
        strategy.setLatestOrderStatus("expired");

        String label = presenter.displayStatusLabel(strategy, false, false, false);

        assertEquals(
                "Expired (Limit Buy 1 Placed - waiting to fill) - no auto extension set; manual reposition required",
                label
        );
    }

    @Test
    void failedInvalidShowsInvalidStatus() {
        Strategy strategy = strategy();
        strategy.setStatus(StrategyStatus.FAILED);
        strategy.setCurrentState(StrategyLifecycleState.FAILED);
        strategy.setLatestOrderStatus("invalid");

        String label = presenter.displayStatusLabel(strategy, false, false, false);

        assertEquals("Invalid - not found at broker", label);
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
    void valueAtShowsConfiguredTimeInForce() {
        Strategy strategy = strategy();
        strategy.setTimeInForce(com.neuralarc.model.TimeInForce.GTC);

        Object value = presenter.valueAt(
                strategy,
                new Position("AAPL"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                8,
                "Limit Base Buy Placed"
        );

        assertEquals("GTC", value);
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
    void completedStatusShowsProfitBookedAmount() {
        Strategy strategy = strategy();
        strategy.setStatus(StrategyStatus.COMPLETED);
        strategy.setCurrentState(StrategyLifecycleState.COMPLETED);

        String label = presenter.displayStatusLabel(
                strategy,
                new Position("AAPL"),
                false,
                false,
                false,
                false,
                new BigDecimal("123.456")
        );

        assertEquals("Completed - Profit Booked $123.46", label);
    }

    @Test
    void completedStatusShowsLossBookedAmount() {
        Strategy strategy = strategy();
        strategy.setStatus(StrategyStatus.COMPLETED);
        strategy.setCurrentState(StrategyLifecycleState.COMPLETED);

        String label = presenter.displayStatusLabel(
                strategy,
                new Position("AAPL"),
                false,
                false,
                false,
                false,
                new BigDecimal("-45.67")
        );

        assertEquals("Completed - Loss Booked $45.67", label);
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

        assertEquals("Picks [Gainers]", entryValue);
        assertEquals("Manual - User Exit", exitValue);
    }

    @Test
    void completedLossShowsLossExitWhenRuleWasNotPersisted() {
        Strategy strategy = strategy();
        strategy.setStatus(StrategyStatus.COMPLETED);
        strategy.setCurrentState(StrategyLifecycleState.COMPLETED);
        strategy.setLastTriggeredRuleType("");

        Object exitValue = presenter.valueAt(
                strategy,
                new Position("JOBY"),
                BigDecimal.ZERO,
                new BigDecimal("-4.90"),
                10,
                "Completed - Loss Booked $4.90",
                "Paper"
        );

        assertEquals("Autonomous - Loss Exit", exitValue);
    }

    @Test
    void autonomousExitSourcesIncludeSystemOwnership() {
        Strategy stopLoss = completedStrategyWithRule("STOP_LOSS_RULE");
        Strategy sellTrigger = completedStrategyWithRule("SELL_RULE");
        Strategy profitExit = completedStrategyWithRule("PROFIT_EXIT");

        assertEquals("Autonomous - Stop Loss", presenter.valueAt(stopLoss, new Position("AAPL"), BigDecimal.ZERO, BigDecimal.ZERO, 10, "Completed", "Paper"));
        assertEquals("Autonomous - Sell Trigger", presenter.valueAt(sellTrigger, new Position("AAPL"), BigDecimal.ZERO, BigDecimal.ZERO, 10, "Completed", "Paper"));
        assertEquals("Autonomous - Profit Exit", presenter.valueAt(profitExit, new Position("AAPL"), BigDecimal.ZERO, BigDecimal.ZERO, 10, "Completed", "Paper"));
    }

    @Test
    void entrySourceUsesLegacyLuckySourceFromNameWhenOrderSubmissionOverwritesLastEvent() {
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

        assertEquals("Picks [Gainers]", entryValue);
    }

    @Test
    void entrySourceRecognizesSmartPicksNameToken() {
        Strategy strategy = strategy();
        strategy.setName("SMART_PICKS_GAINERS: AAPL Paper");
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

        assertEquals("Picks [Gainers]", entryValue);
    }

    @Test
    void entrySourceRecognizesSmartPicksLastEvent() {
        Strategy strategy = strategy();
        strategy.setName("AAPL Paper");
        strategy.setLastEvent("Alpaca Paper mode from Smart Picks. Source top mover gainer.");

        Object entryValue = presenter.valueAt(
                strategy,
                new Position("AAPL"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                9,
                "Active",
                "Paper"
        );

        assertEquals("Picks [Gainers]", entryValue);
    }

    @Test
    void entrySourceRecognizesWeekendReboundSmartPicks() {
        Strategy strategy = strategy();
        strategy.setName("SMART_PICKS_WEEKEND_REBOUND: AAPL Paper");
        strategy.setLastEvent("Order BASE_BUY is ACCEPTED");

        Object entryValue = presenter.valueAt(
                strategy,
                new Position("AAPL"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                9,
                "Limit Base Buy Placed",
                "Paper"
        );

        assertEquals("Picks [Weekend Rebound]", entryValue);
    }

    @Test
    void entrySourceUsesBrokerSyncedNameWhenPollingOverwritesRemoteSyncEvent() {
        Strategy strategy = strategy();
        strategy.setName("MGRT Remote Strategy");
        strategy.setLastEvent("Order BASE_BUY is ACCEPTED");

        Object entryValue = presenter.valueAt(
                strategy,
                new Position("MGRT"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                9,
                "Limit Base Buy Placed",
                "Paper"
        );

        assertEquals("Broker Synced", entryValue);
    }

    @Test
    void entrySourceRecognizesLegacyReviewedLuckySource() {
        Strategy strategy = strategy();
        strategy.setName("I_AM_FEELING_LUCKY_REVIEWED: INFQ.WS Paper");
        strategy.setLastEvent("Order BASE_BUY is ACCEPTED");

        Object entryValue = presenter.valueAt(
                strategy,
                new Position("INFQ.WS"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                9,
                "Limit Base Buy Placed",
                "Paper"
        );

        assertEquals("Picks [Reviewed]", entryValue);
    }

    @Test
    void entrySourceShowsLegacyLuckyLosersAndExitSourceStaysBlankForBuyRule() {
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

        assertEquals("Picks [Losers]", entryValue);
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


    @Test
    void gapRocketRecommendationShowsPendingBaseBuyStatus() {
        Strategy strategy = strategy();
        strategy.setStatus(StrategyStatus.CREATED);
        strategy.setCurrentState(StrategyLifecycleState.CREATED);
        strategy.setLatestOrderStatus("GAP_ROCKET_RECOMMENDED");
        strategy.setBaseBuyLimitPrice(new BigDecimal("203.60"));

        String label = presenter.displayStatusLabel(strategy, false, false, false);

        assertEquals("Base buy pending to place order @ $203.60", label);
    }

    @Test
    void gapRocketRecommendationShowsGapAndGoEntrySource() {
        Strategy strategy = strategy();
        strategy.setName("GAP_ROCKET: NVDA PAPER");
        strategy.setLatestOrderStatus("GAP_ROCKET_RECOMMENDED");

        Object value = presenter.valueAt(strategy, new Position("NVDA"), BigDecimal.ZERO, BigDecimal.ZERO, 9, "");

        assertEquals("Gap and go strategy", value);
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

    private Strategy completedStrategyWithRule(String rule) {
        Strategy strategy = strategy();
        strategy.setStatus(StrategyStatus.COMPLETED);
        strategy.setCurrentState(StrategyLifecycleState.COMPLETED);
        strategy.setLastTriggeredRuleType(rule);
        return strategy;
    }
}
