package com.neuralarc.service;

import com.neuralarc.api.AlpacaClient;
import com.neuralarc.api.AlpacaOrderData;
import com.neuralarc.api.AlpacaPositionData;
import com.neuralarc.api.AlpacaTradeUpdateEvent;
import com.neuralarc.model.*;
import com.neuralarc.util.Monetary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class StrategyPollingServiceTest {
    @Test
    void buyLimit1TriggersAfterBaseBuyFilled() {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        f.addOrder(f.filledOrder(strategy.id(), StrategyStage.BASE_BUY, 10, new BigDecimal("8.00")));
        f.alpaca.latestPrice = new BigDecimal("6.00");

        f.service.pollStrategy(strategy.id());

        assertTrue(f.orders.findLatestByStrategyStage(strategy.id(), StrategyStage.BUY_LIMIT_1).isPresent());
    }

    @Test
    void buyLimit2BlockedUntilBuyLimit1FullyFilled() {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        f.addOrder(f.filledOrder(strategy.id(), StrategyStage.BASE_BUY, 10, new BigDecimal("8.00")));
        StrategyOrder partialL1 = f.filledOrder(strategy.id(), StrategyStage.BUY_LIMIT_1, 5, new BigDecimal("6.00"));
        partialL1.setStatus(StrategyOrderStatus.PARTIALLY_FILLED);
        f.addOrder(partialL1);
        f.alpaca.latestPrice = new BigDecimal("5.00");

        f.service.pollStrategy(strategy.id());

        assertTrue(f.orders.findLatestByStrategyStage(strategy.id(), StrategyStage.BUY_LIMIT_2).isEmpty());
    }

    @Test
    void buyLimit2TriggersAfterBuyLimit1Filled() {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        f.addOrder(f.filledOrder(strategy.id(), StrategyStage.BASE_BUY, 10, new BigDecimal("8.00")));
        f.addOrder(f.filledOrder(strategy.id(), StrategyStage.BUY_LIMIT_1, 5, new BigDecimal("6.00")));
        f.alpaca.latestPrice = new BigDecimal("5.00");

        f.service.pollStrategy(strategy.id());

        assertTrue(f.orders.findLatestByStrategyStage(strategy.id(), StrategyStage.BUY_LIMIT_2).isPresent());
    }

    @Test
    void onTradeUpdateWithDuplicateSymbolsAppliesOnlyToMatchedStrategyOrder() {
        Fixture f = new Fixture();
        Strategy first = f.activeStrategy(false);
        Strategy second = new Strategy(
                UUID.randomUUID().toString(), "s2", "AAPL", StrategyMode.PAPER, StrategyStatus.ACTIVE,
                StrategyLifecycleState.CREATED,
                new BigDecimal("8.00"), 10,
                new BigDecimal("6.00"), 5,
                new BigDecimal("5.00"), 5,
                true, StopLossType.FIXED_PRICE, new BigDecimal("7.00"), BigDecimal.ZERO,
                false, BigDecimal.ZERO,
                true, new BigDecimal("10.00"), new BigDecimal("100.00"), true,
                false, ProfitHoldType.PERCENT_TRAILING, new BigDecimal("10.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                false, 25, new BigDecimal("300.00"), 2, Instant.now(), Instant.now()
        );
        f.strategies.save(second);

        StrategyOrder firstOrder = new StrategyOrder(
                UUID.randomUUID().toString(), first.id(), StrategyStage.BASE_BUY,
                "ord-first", "client-first", "AAPL",
                StrategyOrderSide.BUY, StrategyOrderType.LIMIT,
                new BigDecimal("8.00"), BigDecimal.ZERO,
                new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO,
                StrategyOrderStatus.SUBMITTED,
                Instant.now(), Instant.now(), null, "{}"
        );
        StrategyOrder secondOrder = new StrategyOrder(
                UUID.randomUUID().toString(), second.id(), StrategyStage.BASE_BUY,
                "ord-second", "client-second", "AAPL",
                StrategyOrderSide.BUY, StrategyOrderType.LIMIT,
                new BigDecimal("8.00"), BigDecimal.ZERO,
                new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO,
                StrategyOrderStatus.SUBMITTED,
                Instant.now(), Instant.now(), null, "{}"
        );
        f.orders.save(firstOrder);
        f.orders.save(secondOrder);

        Optional<String> appliedStrategyId = f.service.onTradeUpdate(new AlpacaTradeUpdateEvent(
                "fill",
                new AlpacaOrderData("ord-first", "client-first", "AAPL", "buy", "limit",
                        new BigDecimal("8.00"), new BigDecimal("10"), new BigDecimal("8.00"), "filled", "{}")
        ));

        assertEquals(Optional.of(first.id()), appliedStrategyId);
        Strategy firstUpdated = f.strategies.findById(first.id()).orElseThrow();
        Strategy secondUpdated = f.strategies.findById(second.id()).orElseThrow();
        assertEquals(StrategyOrderStatus.FILLED, f.orders.findByAlpacaOrderId("ord-first").orElseThrow().status());
        assertEquals(StrategyOrderStatus.SUBMITTED, f.orders.findByAlpacaOrderId("ord-second").orElseThrow().status());
        assertEquals(StrategyLifecycleState.CREATED, secondUpdated.currentState());
        assertTrue(secondUpdated.latestOrderStatus() == null || secondUpdated.latestOrderStatus().isBlank());
    }

    @Test
    void expiredBrokerOrderMarksStrategyExpiredForUi() {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        StrategyOrder order = new StrategyOrder(
                UUID.randomUUID().toString(), strategy.id(), StrategyStage.BASE_BUY,
                "ord-expired", "client-expired", "AAPL",
                StrategyOrderSide.BUY, StrategyOrderType.LIMIT,
                new BigDecimal("8.00"), BigDecimal.ZERO,
                new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO,
                StrategyOrderStatus.SUBMITTED,
                Instant.now(), Instant.now(), null, "{}"
        );
        f.orders.save(order);

        Optional<String> appliedStrategyId = f.service.onTradeUpdate(new AlpacaTradeUpdateEvent(
                "expired",
                new AlpacaOrderData("ord-expired", "client-expired", "AAPL", "buy", "limit",
                        new BigDecimal("8.00"), BigDecimal.ZERO, BigDecimal.ZERO, "expired", "{}")
        ));

        assertEquals(Optional.of(strategy.id()), appliedStrategyId);
        Strategy updated = f.strategies.findById(strategy.id()).orElseThrow();
        assertEquals(StrategyStatus.FAILED, updated.status());
        assertEquals(StrategyLifecycleState.FAILED, updated.currentState());
        assertEquals("expired", updated.latestOrderStatus());
        assertEquals(StrategyOrderStatus.CANCELED, f.orders.findByAlpacaOrderId("ord-expired").orElseThrow().status());
    }

    @Test
    void lossBuyLevelsDoNotTriggerWhenDisabled() {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        strategy.setLossBuyLevelsEnabled(false);
        f.strategies.save(strategy);
        f.addOrder(f.filledOrder(strategy.id(), StrategyStage.BASE_BUY, 10, new BigDecimal("8.00")));
        f.alpaca.latestPrice = new BigDecimal("5.00");

        f.service.pollStrategy(strategy.id());

        assertTrue(f.orders.findLatestByStrategyStage(strategy.id(), StrategyStage.BUY_LIMIT_1).isEmpty());
        assertTrue(f.orders.findLatestByStrategyStage(strategy.id(), StrategyStage.BUY_LIMIT_2).isEmpty());
    }

    @Test
    void stopLossTriggersAfterFillWhenPriceDropsBelowThreshold() {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        f.addOrder(f.filledOrder(strategy.id(), StrategyStage.BASE_BUY, 10, new BigDecimal("8.00")));
        f.alpaca.latestPrice = new BigDecimal("6.90");
        f.alpaca.position = Optional.of(new AlpacaPositionData("AAPL", new BigDecimal("10"), new BigDecimal("8.00"), new BigDecimal("6.90"), "{}"));

        f.service.pollStrategy(strategy.id());

        assertTrue(f.orders.findLatestByStrategyStage(strategy.id(), StrategyStage.STOP_LOSS).isPresent());
    }

    @Test
    void stopLossDoesNotTriggerWhenDisabled() {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        strategy.setAutomatedStopLossEnabled(false);
        f.strategies.save(strategy);
        f.addOrder(f.filledOrder(strategy.id(), StrategyStage.BASE_BUY, 10, new BigDecimal("8.00")));
        f.alpaca.latestPrice = new BigDecimal("6.90");
        f.alpaca.position = Optional.of(new AlpacaPositionData("AAPL", new BigDecimal("10"), new BigDecimal("8.00"), new BigDecimal("6.90"), "{}"));

        f.service.pollStrategy(strategy.id());

        assertTrue(f.orders.findLatestByStrategyStage(strategy.id(), StrategyStage.STOP_LOSS).isEmpty());
    }

    @Test
    void targetSellPlacesLimitSellWhenProfitHoldDisabled() {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        f.alpaca.latestPrice = new BigDecimal("10.00");
        f.alpaca.position = Optional.of(new AlpacaPositionData("AAPL", new BigDecimal("10"), new BigDecimal("8.00"), new BigDecimal("10.00"), "{}"));

        f.service.pollStrategy(strategy.id());

        assertTrue(f.orders.findLatestByStrategyStage(strategy.id(), StrategyStage.TARGET_SELL).isPresent());
    }

    @Test
    void targetSellImmediateFillSendsSellExecutedEmail() throws Exception {
        Fixture f = new Fixture();
        RecordingTradeEmailNotificationService emailService = new RecordingTradeEmailNotificationService(f.settingsService);
        StrategyStateMachine stateMachine = new StrategyStateMachine(f.events, new StrategyEventBus());
        StrategyEngine engine = new StrategyEngine(
                f.strategies,
                f.orders,
                stateMachine,
                f.alpaca,
                f.settingsService,
                f.marketHoursService,
                emailService
        );
        Strategy strategy = f.activeStrategy(false);
        f.alpaca.latestPrice = new BigDecimal("10.00");
        f.alpaca.nextLimitSellStatus = "filled";
        f.alpaca.nextLimitSellFilledQuantity = new BigDecimal("10");
        f.alpaca.nextLimitSellFilledAveragePrice = new BigDecimal("10.00");
        f.alpaca.position = Optional.of(new AlpacaPositionData("AAPL", new BigDecimal("10"), new BigDecimal("8.00"), new BigDecimal("10.00"), "{}"));

        engine.reconcile(strategy);

        StrategyOrder sellOrder = f.orders.findLatestByStrategyStage(strategy.id(), StrategyStage.TARGET_SELL).orElseThrow();
        assertEquals(StrategyOrderStatus.FILLED, sellOrder.status());
        assertEquals(StrategyLifecycleState.COMPLETED, f.strategies.findById(strategy.id()).orElseThrow().currentState());
        assertEquals(List.of("AAPL:TARGET_SELL:FILLED"), emailService.sellExecuted);
    }

    @Test
    void targetSellPlacesBrokerTrailingStopWhenEnabled() {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        strategy.setAlpacaTrailingStopEnabled(true);
        strategy.setProfitHoldType(ProfitHoldType.FIXED_AMOUNT_TRAILING);
        strategy.setProfitHoldAmount(new BigDecimal("0.75"));
        f.strategies.save(strategy);
        f.alpaca.latestPrice = new BigDecimal("10.00");
        f.alpaca.position = Optional.of(new AlpacaPositionData("AAPL", new BigDecimal("10"), new BigDecimal("8.00"), new BigDecimal("10.00"), "{}"));

        f.service.pollStrategy(strategy.id());

        StrategyOrder exit = f.orders.findLatestByStrategyStage(strategy.id(), StrategyStage.PROFIT_EXIT).orElseThrow();
        assertEquals(StrategyOrderType.TRAILING_STOP, exit.orderType());
        assertEquals("ALPACA_TRAILING_STOP", f.strategies.findById(strategy.id()).orElseThrow().lastTriggeredRuleType());
        assertTrue(f.orders.findLatestByStrategyStage(strategy.id(), StrategyStage.TARGET_SELL).isEmpty());
    }

    @Test
    void targetSellDisabledBlocksProfitExitEvaluation() {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(true);
        strategy.setTargetSellEnabled(false);
        f.strategies.save(strategy);
        f.alpaca.latestPrice = new BigDecimal("11.00");
        f.alpaca.position = Optional.of(new AlpacaPositionData("AAPL", new BigDecimal("10"), new BigDecimal("8.00"), new BigDecimal("11.00"), "{}"));

        f.service.pollStrategy(strategy.id());

        assertTrue(f.orders.findLatestByStrategyStage(strategy.id(), StrategyStage.TARGET_SELL).isEmpty());
        assertTrue(f.orders.findLatestByStrategyStage(strategy.id(), StrategyStage.PROFIT_EXIT).isEmpty());
    }

    @Test
    void targetSellStateIsNotOverwrittenByStopLossMonitoringOnLaterPoll() {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        f.alpaca.position = Optional.of(new AlpacaPositionData("AAPL", new BigDecimal("10"), new BigDecimal("8.00"), new BigDecimal("10.00"), "{}"));

        f.service.pollStrategy(strategy.id());
        Strategy afterSellTrigger = f.strategies.findById(strategy.id()).orElseThrow();
        assertEquals(StrategyLifecycleState.SELL_PLACED, afterSellTrigger.currentState());

        f.alpaca.position = Optional.of(new AlpacaPositionData("AAPL", new BigDecimal("10"), new BigDecimal("8.00"), new BigDecimal("10.10"), "{}"));
        f.service.pollStrategy(strategy.id());
        Strategy afterSecondPoll = f.strategies.findById(strategy.id()).orElseThrow();

        assertEquals(StrategyLifecycleState.SELL_PLACED, afterSecondPoll.currentState());
        assertEquals("SELL_RULE", afterSecondPoll.lastTriggeredRuleType());
    }

    @Test
    void profitHoldSellsAfterPullbackFromHigh() {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(true);
        f.alpaca.position = Optional.of(new AlpacaPositionData("AAPL", new BigDecimal("10"), new BigDecimal("8.00"), new BigDecimal("10.00"), "{}"));

        f.alpaca.position = Optional.of(new AlpacaPositionData("AAPL", new BigDecimal("10"), new BigDecimal("8.00"), new BigDecimal("10.50"), "{}"));
        f.service.pollStrategy(strategy.id());
        assertTrue(f.orders.findLatestByStrategyStage(strategy.id(), StrategyStage.PROFIT_EXIT).isEmpty());

        f.alpaca.position = Optional.of(new AlpacaPositionData("AAPL", new BigDecimal("10"), new BigDecimal("8.00"), new BigDecimal("11.00"), "{}"));
        f.service.pollStrategy(strategy.id());
        assertTrue(f.orders.findLatestByStrategyStage(strategy.id(), StrategyStage.PROFIT_EXIT).isEmpty());

        f.alpaca.position = Optional.of(new AlpacaPositionData("AAPL", new BigDecimal("10"), new BigDecimal("8.00"), new BigDecimal("9.85"), "{}"));
        f.service.pollStrategy(strategy.id());
        assertTrue(f.orders.findLatestByStrategyStage(strategy.id(), StrategyStage.PROFIT_EXIT).isPresent());
    }

    @Test
    void profitHoldModeUsesProfitActivationThresholdWhenSellTriggerDisabled() {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(true);
        strategy.setTargetSellEnabled(false);
        strategy.setProfitControlMode(ProfitControlMode.PROFIT_HOLD);
        strategy.setAutomaticStopSellThresholdType(ThresholdType.PERCENTAGE);
        strategy.setAutomaticStopSellThreshold(new BigDecimal("25.00"));
        strategy.setProfitHoldType(ProfitHoldType.PERCENT_TRAILING);
        strategy.setProfitHoldPercent(new BigDecimal("10.00"));
        f.strategies.save(strategy);

        f.alpaca.position = Optional.of(new AlpacaPositionData("AAPL", new BigDecimal("10"), new BigDecimal("8.00"), new BigDecimal("9.90"), "{}"));
        f.service.pollStrategy(strategy.id());
        assertEquals(0, f.strategies.findById(strategy.id()).orElseThrow().highestObservedPriceAfterTarget().compareTo(BigDecimal.ZERO));
        assertTrue(f.orders.findLatestByStrategyStage(strategy.id(), StrategyStage.PROFIT_EXIT).isEmpty());
        assertTrue(f.orders.findLatestByStrategyStage(strategy.id(), StrategyStage.TARGET_SELL).isEmpty());

        f.alpaca.position = Optional.of(new AlpacaPositionData("AAPL", new BigDecimal("10"), new BigDecimal("8.00"), new BigDecimal("10.50"), "{}"));
        f.service.pollStrategy(strategy.id());
        assertEquals(new BigDecimal("10.50"), f.strategies.findById(strategy.id()).orElseThrow().highestObservedPriceAfterTarget());
        assertTrue(f.orders.findLatestByStrategyStage(strategy.id(), StrategyStage.PROFIT_EXIT).isEmpty());

        f.alpaca.position = Optional.of(new AlpacaPositionData("AAPL", new BigDecimal("10"), new BigDecimal("8.00"), new BigDecimal("9.40"), "{}"));
        f.service.pollStrategy(strategy.id());
        assertTrue(f.orders.findLatestByStrategyStage(strategy.id(), StrategyStage.PROFIT_EXIT).isPresent());
    }

    @Test
    void automaticStopSellUsesProfitActivationThresholdAndConfiguredTrailingValue() {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        strategy.setTargetSellEnabled(false);
        strategy.setProfitControlMode(ProfitControlMode.AUTOMATIC_STOP_SELL);
        strategy.setAutomaticStopSellThresholdType(ThresholdType.FIXED_AMOUNT);
        strategy.setAutomaticStopSellThreshold(new BigDecimal("2.00"));
        strategy.setAutomaticStopSellTrailingType(TrailingType.FIXED_AMOUNT);
        strategy.setAutomaticStopSellTrailingValue(new BigDecimal("0.75"));
        f.strategies.save(strategy);

        f.alpaca.position = Optional.of(new AlpacaPositionData("AAPL", new BigDecimal("10"), new BigDecimal("8.00"), new BigDecimal("9.99"), "{}"));
        f.service.pollStrategy(strategy.id());
        assertTrue(f.orders.findLatestByStrategyStage(strategy.id(), StrategyStage.PROFIT_EXIT).isEmpty());

        f.alpaca.position = Optional.of(new AlpacaPositionData("AAPL", new BigDecimal("10"), new BigDecimal("8.00"), new BigDecimal("10.00"), "{}"));
        f.service.pollStrategy(strategy.id());

        StrategyOrder exit = f.orders.findLatestByStrategyStage(strategy.id(), StrategyStage.PROFIT_EXIT).orElseThrow();
        assertEquals(StrategyOrderType.TRAILING_STOP, exit.orderType());
        assertEquals(BigDecimal.ZERO, f.alpaca.lastTrailPercent);
        assertEquals(new BigDecimal("0.75"), f.alpaca.lastTrailPrice);
    }

    @Test
    void maxQuantityAndCapitalRiskControlsBlockBuy() {
        Fixture f = new Fixture();
        Strategy strategy = new Strategy(
                UUID.randomUUID().toString(), "risk", "AAPL", StrategyMode.PAPER, StrategyStatus.ACTIVE,
                StrategyLifecycleState.CREATED,
                new BigDecimal("8.00"), 10,
                new BigDecimal("6.00"), 5,
                new BigDecimal("5.00"), 5,
                true, StopLossType.FIXED_PRICE, new BigDecimal("7.00"), BigDecimal.ZERO,
                false, BigDecimal.ZERO,
                true, new BigDecimal("10.00"), new BigDecimal("100.00"), true,
                false, ProfitHoldType.PERCENT_TRAILING, new BigDecimal("10.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                false, 10, new BigDecimal("80.00"), 2, Instant.now(), Instant.now()
        );
        f.strategies.save(strategy);
        f.addOrder(f.filledOrder(strategy.id(), StrategyStage.BASE_BUY, 10, new BigDecimal("8.00")));
        f.alpaca.latestPrice = new BigDecimal("6.00");

        f.service.pollStrategy(strategy.id());

        assertTrue(f.orders.findLatestByStrategyStage(strategy.id(), StrategyStage.BUY_LIMIT_1).isEmpty());
        assertEquals(StrategyStatus.FAILED, f.strategies.findById(strategy.id()).orElseThrow().status());
    }

    @Test
    void pollingRecreatesBaseBuyWhenRemoteOrderIsMissing() {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        StrategyOrder pendingBase = new StrategyOrder(
                UUID.randomUUID().toString(), strategy.id(), StrategyStage.BASE_BUY,
                "ord-missing", "client-missing", "AAPL",
                StrategyOrderSide.BUY, StrategyOrderType.LIMIT,
                new BigDecimal("8.00"), BigDecimal.ZERO,
                new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO,
                StrategyOrderStatus.SUBMITTED,
                Instant.now(), Instant.now(), null, "{}"
        );
        f.orders.save(pendingBase);
        f.alpaca.latestPrice = new BigDecimal("8.00");
        f.alpaca.position = Optional.empty();
        f.alpaca.orderById.clear();

        f.service.pollStrategy(strategy.id());

        List<StrategyOrder> strategyOrders = f.orders.findByStrategyId(strategy.id());
        long submittedBaseOrders = strategyOrders.stream()
                .filter(order -> order.stage() == StrategyStage.BASE_BUY)
                .filter(order -> order.status() == StrategyOrderStatus.SUBMITTED || order.status() == StrategyOrderStatus.PENDING)
                .count();
        assertTrue(submittedBaseOrders >= 1);
        assertTrue(strategyOrders.stream()
                .filter(order -> "ord-missing".equals(order.alpacaOrderId()))
                .allMatch(order -> order.status() == StrategyOrderStatus.CANCELED));
    }

    @Test
    void manualCancelDuringInFlightPollDoesNotRecreateBaseBuy() throws Exception {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        StrategyOrder pendingBase = new StrategyOrder(
                UUID.randomUUID().toString(), strategy.id(), StrategyStage.BASE_BUY,
                "ord-missing", "client-missing", "AAPL",
                StrategyOrderSide.BUY, StrategyOrderType.LIMIT,
                new BigDecimal("8.00"), BigDecimal.ZERO,
                new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO,
                StrategyOrderStatus.SUBMITTED,
                Instant.now(), Instant.now(), null, "{}"
        );
        f.orders.save(pendingBase);
        f.alpaca.position = Optional.empty();
        f.alpaca.orderById.clear();
        f.alpaca.blockNextOpenOrdersCall();

        Thread pollingThread = new Thread(() -> f.service.pollStrategy(strategy.id()), "test-poll-in-flight");
        pollingThread.start();
        assertTrue(f.alpaca.awaitOpenOrdersBlock(), "Poll must reach deterministic open-orders gate");

        Strategy paused = f.strategies.findById(strategy.id()).orElseThrow();
        paused.setStatus(StrategyStatus.PAUSED);
        paused.setCurrentState(StrategyLifecycleState.PAUSED);
        paused.setPauseReason(PauseReason.MANUAL_LIMIT_BUY_CANCELED);
        f.strategies.save(paused);

        f.alpaca.releaseBlockedOpenOrders();
        pollingThread.join(3000L);

        Strategy after = f.strategies.findById(strategy.id()).orElseThrow();
        assertEquals(StrategyStatus.PAUSED, after.status());
        assertEquals(PauseReason.MANUAL_LIMIT_BUY_CANCELED, after.pauseReason());

        List<StrategyOrder> ordersAfter = f.orders.findByStrategyId(strategy.id());
        long baseBuyOrders = ordersAfter.stream()
                .filter(order -> order.stage() == StrategyStage.BASE_BUY)
                .count();
        assertEquals(1L, baseBuyOrders, "No additional base-buy order should be created after manual cancel during in-flight poll");
    }

    @Test
    void pollDueStrategiesReturnsImmediatelyAndOtherStrategiesContinueWhileOnePollIsBlocked() throws Exception {
        Fixture f = new Fixture();
        Strategy blocked = f.activeStrategy("AAPL", false);
        Strategy fast = f.activeStrategy("MSFT", false);
        Instant blockedBaseline = Instant.now().minusSeconds(60);
        Instant fastBaseline = Instant.now().minusSeconds(60);
        blocked.setLastPolledAt(blockedBaseline);
        fast.setLastPolledAt(fastBaseline);
        f.strategies.save(blocked);
        f.strategies.save(fast);
        f.alpaca.blockOpenOrdersForSymbol(blocked.symbol());
        // The combined per-cycle snapshot batch would otherwise short-circuit the per-symbol
        // getOpenOrders(symbol) gate below; fail it once so this cycle falls back to per-strategy
        // resolution, which is what this test exercises (dispatch stays non-blocking per strategy).
        f.alpaca.failNextOpenOrdersBatch();

        long firstStartedAt = System.nanoTime();
        int firstDue = f.service.pollDueStrategies();
        long firstElapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - firstStartedAt);

        assertEquals(2, firstDue);
        assertTrue(firstElapsedMillis < 500, "pollDueStrategies should return without waiting for blocked strategy completion");
        assertTrue(f.alpaca.awaitOpenOrdersBlock(blocked.symbol()), "Blocked strategy should reach the deterministic open-orders gate");

        Instant fastFirstPoll = f.awaitLastPolledAfter(
                fast.id(),
                fastBaseline,
                "Unblocked strategy should complete its first poll while another strategy is blocked"
        );

        Strategy fastDueAgain = f.strategies.findById(fast.id()).orElseThrow();
        fastDueAgain.setLastPolledAt(Instant.now().minusSeconds(60));
        f.strategies.save(fastDueAgain);

        long secondStartedAt = System.nanoTime();
        int secondDue = f.service.pollDueStrategies();
        long secondElapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - secondStartedAt);

        assertEquals(1, secondDue);
        assertTrue(secondElapsedMillis < 500, "Subsequent poll cycles should stay independent of the blocked strategy");

        Instant fastSecondPoll = f.awaitLastPolledAfter(
                fast.id(),
                fastFirstPoll,
                "Ready strategy should be polled again even while another strategy remains blocked"
        );
        assertTrue(fastSecondPoll.isAfter(fastFirstPoll));
        assertEquals(1, f.alpaca.openOrderCallsForSymbol(blocked.symbol()),
                "Blocked strategy must not be dispatched a second time while already in flight");

        f.alpaca.releaseBlockedOpenOrders(blocked.symbol());
        f.awaitLastPolledAfter(
                blocked.id(),
                blockedBaseline,
                "Blocked strategy should finish once its broker call is released"
        );
    }

    @Test
    void pollDueStrategiesUsesOneCombinedBatchInsteadOfPerStrategyCalls() throws Exception {
        Fixture f = new Fixture();
        Strategy a = f.activeStrategy("AAPL", false);
        Strategy b = f.activeStrategy("MSFT", false);
        Instant baselineA = Instant.now().minusSeconds(60);
        Instant baselineB = Instant.now().minusSeconds(60);
        a.setLastPolledAt(baselineA);
        b.setLastPolledAt(baselineB);
        f.strategies.save(a);
        f.strategies.save(b);
        f.alpaca.positionsBySymbol.put("AAPL",
                new AlpacaPositionData("AAPL", new BigDecimal("1"), new BigDecimal("8.00"), new BigDecimal("8.00"), "{}"));

        int due = f.service.pollDueStrategies();

        assertEquals(2, due);
        f.awaitLastPolledAfter(a.id(), baselineA, "Strategy A should complete its poll");
        f.awaitLastPolledAfter(b.id(), baselineB, "Strategy B should complete its poll");
        assertEquals(1, f.alpaca.positionsBatchCalls, "Positions should be fetched once for the whole cycle, not once per strategy");
        assertEquals(1, f.alpaca.openOrdersBatchCalls, "Open orders should be fetched once for the whole cycle, not once per strategy");
        assertEquals(0, f.alpaca.positionCalls, "Per-strategy getPosition must not run when the combined batch already succeeded");
    }

    @Test
    void pullsForwardNearbyStrategyIntoTheSameBatchCycle() throws Exception {
        Fixture f = new Fixture();
        Strategy due = f.activeStrategy("AAPL", false);
        due.setPollingIntervalSeconds(15);
        due.setLastPolledAt(Instant.now().minusSeconds(20));
        f.strategies.save(due);

        Strategy nearby = f.activeStrategy("MSFT", false);
        nearby.setPollingIntervalSeconds(15);
        Instant nearbyBaseline = Instant.now().minusSeconds(12); // natural due time ~3s from now, within the 5s catch-up window
        nearby.setLastPolledAt(nearbyBaseline);
        f.strategies.save(nearby);

        int dueCount = f.service.pollDueStrategies();

        assertEquals(2, dueCount, "The nearby same-bucket strategy should be pulled forward into this cycle");
        f.awaitLastPolledAfter(nearby.id(), nearbyBaseline, "Pulled-forward strategy should complete its poll this cycle, not a later one");
    }

    @Test
    void fallsBackToPerStrategyCallsWhenTheCombinedBatchFetchFails() throws Exception {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy("AAPL", false);
        Instant baseline = Instant.now().minusSeconds(60);
        strategy.setLastPolledAt(baseline);
        f.strategies.save(strategy);
        f.alpaca.position = Optional.of(new AlpacaPositionData("AAPL", new BigDecimal("10"), new BigDecimal("8.00"), new BigDecimal("8.00"), "{}"));
        f.alpaca.failNextOpenOrdersBatch();

        f.service.pollDueStrategies();

        f.awaitLastPolledAfter(strategy.id(), baseline, "Strategy should still complete its poll via per-strategy fallback");
        assertTrue(f.alpaca.positionCalls > 0, "Per-strategy getPosition should be used as fallback when the combined batch fetch fails");
    }

    @Test
    void openOrderChangeDetectionSkipsGetOrderForStillOpenOrdersAndCallsOnceWhenFilled() throws Exception {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy("AAPL", false);
        Instant baseline = Instant.now().minusSeconds(60);
        strategy.setLastPolledAt(baseline);
        f.strategies.save(strategy);
        f.alpaca.position = Optional.empty();
        StrategyOrder pendingBase = new StrategyOrder(
                UUID.randomUUID().toString(), strategy.id(), StrategyStage.BASE_BUY,
                "ord-open-test", "client-open-test", "AAPL",
                StrategyOrderSide.BUY, StrategyOrderType.LIMIT,
                new BigDecimal("8.00"), BigDecimal.ZERO,
                new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO,
                StrategyOrderStatus.SUBMITTED,
                Instant.now(), Instant.now(), null, "{}"
        );
        f.addOrder(pendingBase);

        f.service.pollDueStrategies();
        f.awaitLastPolledAfter(strategy.id(), baseline, "First poll should complete");

        assertEquals(0, f.alpaca.orderCalls,
                "An order still present in the shared open-orders snapshot should not trigger an individual getOrder call");

        Instant secondBaseline = f.strategies.findById(strategy.id()).orElseThrow().lastPolledAt();
        f.alpaca.orderById.put("ord-open-test", new AlpacaOrderData(
                "ord-open-test", "client-open-test", "AAPL", "buy", "limit",
                new BigDecimal("8.00"), new BigDecimal("8.00"), new BigDecimal("10"), "filled", "{}"
        ));
        Strategy again = f.strategies.findById(strategy.id()).orElseThrow();
        again.setLastPolledAt(Instant.now().minusSeconds(60));
        f.strategies.save(again);

        f.service.pollDueStrategies();
        f.awaitLastPolledAfter(strategy.id(), secondBaseline, "Second poll should complete");

        assertEquals(1, f.alpaca.orderCalls,
                "An order that dropped out of the shared open-orders snapshot should get exactly one individual getOrder call");
    }

    @Test
    void maxAttemptsDefaultsToUnlimitedAndKeepsPollingThroughRepeatedBatchFailures() throws Exception {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy("AAPL", false);
        Instant baseline = Instant.now().minusSeconds(60);
        strategy.setLastPolledAt(baseline);
        f.strategies.save(strategy);
        f.alpaca.failNextOpenOrdersBatch();

        f.service.pollDueStrategies();

        f.awaitLastPolledAfter(strategy.id(), baseline,
                "Strategy should still be polled after a batch failure since max attempts defaults to unlimited/disabled");
        assertTrue(f.service.isValidationWarningPaused(),
                "The tracker should still surface the failure for the UI even though polling itself was unaffected");
    }

    @Test
    void pollStrategyNowClearsWarningPausedState() throws Exception {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy("AAPL", false);
        Instant baseline = Instant.now().minusSeconds(60);
        strategy.setLastPolledAt(baseline);
        f.strategies.save(strategy);
        f.alpaca.failNextOpenOrdersBatch();
        f.service.pollDueStrategies();
        f.awaitLastPolledAfter(strategy.id(), baseline, "Initial poll should complete via fallback");
        assertTrue(f.service.isValidationWarningPaused());

        f.service.pollStrategyNow(strategy.id());

        assertFalse(f.service.isValidationWarningPaused(), "Manual refresh should clear the warning-paused state immediately");
    }

    @Test
    void explicitBatchPollDispatchesStrategiesInParallel() throws Exception {
        Fixture f = new Fixture();
        Strategy blocked = f.activeStrategy("AAPL", false);
        Strategy fast = f.activeStrategy("MSFT", false);
        Instant blockedBaseline = Instant.now().minusSeconds(60);
        Instant fastBaseline = Instant.now().minusSeconds(60);
        blocked.setLastPolledAt(blockedBaseline);
        fast.setLastPolledAt(fastBaseline);
        f.strategies.save(blocked);
        f.strategies.save(fast);
        f.alpaca.blockOpenOrdersForSymbol(blocked.symbol());

        long startedAt = System.nanoTime();
        int submitted = f.service.pollStrategiesAsync(List.of(blocked.id(), fast.id()));
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        assertEquals(2, submitted);
        assertTrue(elapsedMillis < 500, "Batch poll dispatch should not wait for blocked strategy completion");
        assertTrue(f.alpaca.awaitOpenOrdersBlock(blocked.symbol()), "Blocked strategy should reach the deterministic open-orders gate");
        f.awaitLastPolledAfter(
                fast.id(),
                fastBaseline,
                "Unblocked strategy should complete while another explicit batch poll is blocked"
        );

        f.alpaca.releaseBlockedOpenOrders(blocked.symbol());
        f.awaitLastPolledAfter(
                blocked.id(),
                blockedBaseline,
                "Blocked strategy should finish once its broker call is released"
        );
    }

    @Test
    void pollCycleSkipsFailedHistoryStrategies() {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        strategy.setStatus(StrategyStatus.FAILED);
        strategy.setCurrentState(StrategyLifecycleState.FAILED);
        f.strategies.save(strategy);
        f.alpaca.position = Optional.empty();
        f.alpaca.orderById.clear();

        f.service.pollDueStrategies();

        Strategy updated = f.strategies.findById(strategy.id()).orElseThrow();
        assertEquals(StrategyStatus.FAILED, updated.status());
        assertTrue(f.orders.findLatestByStrategyStage(strategy.id(), StrategyStage.BASE_BUY).isEmpty());
    }

    @Test
    void pollCycleResubmitsExpiredFailedStrategyWhenEnabled() {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        strategy.setStatus(StrategyStatus.FAILED);
        strategy.setCurrentState(StrategyLifecycleState.FAILED);
        strategy.setLatestOrderStatus("expired");
        strategy.setResubmitOnExpiryEnabled(true);
        f.strategies.save(strategy);
        f.alpaca.position = Optional.empty();
        f.alpaca.orderById.clear();

        f.service.pollDueStrategies();

        assertDoesNotThrow(() -> f.awaitTrue(() -> {
            Strategy updated = f.strategies.findById(strategy.id()).orElseThrow();
            return updated.status() == StrategyStatus.ACTIVE
                    && "new".equals(updated.latestOrderStatus())
                    && f.orders.findLatestByStrategyStage(strategy.id(), StrategyStage.BASE_BUY).isPresent();
        }, "Expired failed strategy should be resubmitted asynchronously"));

        Strategy updated = f.strategies.findById(strategy.id()).orElseThrow();
        assertEquals(StrategyStatus.ACTIVE, updated.status());
        assertEquals("new", updated.latestOrderStatus());
        assertTrue(f.orders.findLatestByStrategyStage(strategy.id(), StrategyStage.BASE_BUY).isPresent());
    }

    @Test
    void marketOpenTransitionResubmitsExpiredAutoExtensionWithoutWaitingForPollingInterval() {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        strategy.setStatus(StrategyStatus.FAILED);
        strategy.setCurrentState(StrategyLifecycleState.FAILED);
        strategy.setLatestOrderStatus("expired");
        strategy.setResubmitOnExpiryEnabled(true);
        strategy.setPollingIntervalSeconds(60);
        strategy.setLastPolledAt(Instant.now());
        f.strategies.save(strategy);
        f.alpaca.position = Optional.empty();
        f.alpaca.orderById.clear();
        f.marketHoursService.open = true;

        int due = f.service.pollDueStrategies();

        assertEquals(0, due);
        assertDoesNotThrow(() -> f.awaitTrue(() -> {
            Strategy updated = f.strategies.findById(strategy.id()).orElseThrow();
            return updated.status() == StrategyStatus.ACTIVE
                    && "new".equals(updated.latestOrderStatus())
                    && f.orders.findLatestByStrategyStage(strategy.id(), StrategyStage.BASE_BUY).isPresent();
        }, "Expired auto-extension strategy should restart immediately when the market opens"));
    }

    @Test
    void pollCycleCompletesListenerWhenExpiredAutoResubmitCannotRun() throws Exception {
        Fixture f = new Fixture();
        f.service.pollDueStrategies();
        Strategy strategy = f.activeStrategy(false);
        strategy.setStatus(StrategyStatus.FAILED);
        strategy.setCurrentState(StrategyLifecycleState.FAILED);
        strategy.setLatestOrderStatus("expired");
        strategy.setLatestAlpacaOrderId("ord-expired");
        strategy.setLastTriggeredRuleType("LOSS_BUY_RULE");
        strategy.setResubmitOnExpiryEnabled(true);
        f.strategies.save(strategy);
        f.orders.save(new StrategyOrder(
                UUID.randomUUID().toString(),
                strategy.id(),
                StrategyStage.BUY_LIMIT_1,
                "ord-expired",
                "client-expired",
                "AAPL",
                StrategyOrderSide.BUY,
                StrategyOrderType.LIMIT,
                new BigDecimal("6.00"),
                BigDecimal.ZERO,
                new BigDecimal("5"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                StrategyOrderStatus.SUBMITTED,
                Instant.now(),
                Instant.now(),
                null,
                "{}"
        ));
        f.alpaca.position = Optional.empty();
        f.alpaca.orderById.clear();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);
        f.service.setPollListener(new StrategyPollingService.PollListener() {
            @Override public void onPollStarted(String strategyId) {
                if (strategy.id().equals(strategyId)) {
                    started.countDown();
                }
            }

            @Override public void onPollCompleted(String strategyId) {
                if (strategy.id().equals(strategyId)) {
                    completed.countDown();
                }
            }
        });

        int due = f.service.pollDueStrategies();

        assertEquals(1, due);
        assertTrue(started.await(3, TimeUnit.SECONDS));
        assertTrue(completed.await(3, TimeUnit.SECONDS));
        assertEquals(StrategyStatus.FAILED, f.strategies.findById(strategy.id()).orElseThrow().status());
    }

    @Test
    void pollCycleResubmitsExpiredFailedBuyLimit1WhenPositionExists() {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        strategy.setStatus(StrategyStatus.FAILED);
        strategy.setCurrentState(StrategyLifecycleState.FAILED);
        strategy.setLatestOrderStatus("expired");
        strategy.setLatestAlpacaOrderId("ord-expired");
        strategy.setLastTriggeredRuleType("LOSS_BUY_RULE");
        strategy.setResubmitOnExpiryEnabled(true);
        f.strategies.save(strategy);
        f.orders.save(new StrategyOrder(
                UUID.randomUUID().toString(),
                strategy.id(),
                StrategyStage.BUY_LIMIT_1,
                "ord-expired",
                "client-expired",
                "AAPL",
                StrategyOrderSide.BUY,
                StrategyOrderType.LIMIT,
                new BigDecimal("6.00"),
                BigDecimal.ZERO,
                new BigDecimal("5"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                StrategyOrderStatus.SUBMITTED,
                Instant.now(),
                Instant.now(),
                null,
                "{}"
        ));
        f.alpaca.position = Optional.of(new AlpacaPositionData("AAPL", new BigDecimal("10"), new BigDecimal("8.00"), new BigDecimal("8.50"), "{}"));
        f.alpaca.orderById.clear();

        f.service.pollDueStrategies();

        assertDoesNotThrow(() -> f.awaitTrue(() -> {
            Strategy updated = f.strategies.findById(strategy.id()).orElseThrow();
            return updated.status() == StrategyStatus.ACTIVE
                    && f.orders.findByStrategyId(strategy.id()).stream()
                    .anyMatch(order -> order.stage() == StrategyStage.BUY_LIMIT_1 && order.isPending());
        }, "Expired buy limit 1 should be resubmitted while the base position remains open"));

        Strategy updated = f.strategies.findById(strategy.id()).orElseThrow();
        assertEquals(StrategyStatus.ACTIVE, updated.status());
        assertTrue(f.orders.findByStrategyId(strategy.id()).stream()
                .anyMatch(order -> order.stage() == StrategyStage.BUY_LIMIT_1 && order.isPending()));
        assertEquals(StrategyOrderStatus.CANCELED, f.orders.findByClientOrderId("client-expired").orElseThrow().status());
    }

    @Test
    void pollCycleDoesNotResubmitExpiredFailedStrategyWhenDisabled() {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        strategy.setStatus(StrategyStatus.FAILED);
        strategy.setCurrentState(StrategyLifecycleState.FAILED);
        strategy.setLatestOrderStatus("expired");
        f.strategies.save(strategy);
        f.alpaca.position = Optional.empty();
        f.alpaca.orderById.clear();

        f.service.pollDueStrategies();

        Strategy updated = f.strategies.findById(strategy.id()).orElseThrow();
        assertEquals(StrategyStatus.FAILED, updated.status());
        assertTrue(f.orders.findLatestByStrategyStage(strategy.id(), StrategyStage.BASE_BUY).isEmpty());
    }

    @Test
    void profitableExitRestartsCycleWhenRepeatAfterProfitEnabled() {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        strategy.setRestartAfterExitEnabled(true);
        f.strategies.save(strategy);
        f.addOrder(f.filledOrder(strategy.id(), StrategyStage.BASE_BUY, 10, new BigDecimal("8.00")));
        f.addOrder(f.filledOrder(strategy.id(), StrategyStage.TARGET_SELL, 10, new BigDecimal("10.00")));
        f.alpaca.position = Optional.empty();

        f.service.pollStrategy(strategy.id());

        List<StrategyOrder> baseOrders = f.orders.findByStrategyId(strategy.id()).stream()
                .filter(order -> order.stage() == StrategyStage.BASE_BUY)
                .toList();
        assertTrue(baseOrders.stream().anyMatch(StrategyOrder::isPending));
        assertEquals(StrategyStatus.ACTIVE, f.strategies.findById(strategy.id()).orElseThrow().status());
    }

    @Test
    void pendingSellPollsAtConfiguredIntervalEvenWhenStreamRecentlyHandledAnotherEvent() throws Exception {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        strategy.setCurrentState(StrategyLifecycleState.SELL_PLACED);
        strategy.setLastPolledAt(Instant.now().minusSeconds(61));
        strategy.setLatestOrderStatus("pending_new");
        strategy.setLatestAlpacaOrderId("ord-sell");
        f.strategies.save(strategy);
        f.addOrder(f.filledOrder(strategy.id(), StrategyStage.BASE_BUY, 1, new BigDecimal("220.00")));
        f.orders.save(new StrategyOrder(
                UUID.randomUUID().toString(), strategy.id(), StrategyStage.TARGET_SELL,
                "ord-sell", "client-sell", "NVDA",
                StrategyOrderSide.SELL, StrategyOrderType.LIMIT,
                new BigDecimal("221.10"), BigDecimal.ZERO,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO,
                StrategyOrderStatus.PENDING,
                Instant.now().minusSeconds(90), Instant.now().minusSeconds(90), null, "{}"
        ));
        f.alpaca.orderById.put("ord-sell", new AlpacaOrderData(
                "ord-sell", "client-sell", "NVDA", "sell", "limit",
                new BigDecimal("221.10"), BigDecimal.ONE, new BigDecimal("221.10"), "filled", "{}"
        ));
        f.alpaca.position = Optional.empty();
        Strategy other = f.activeStrategy("AAPL", false);
        f.orders.save(new StrategyOrder(
                UUID.randomUUID().toString(), other.id(), StrategyStage.BASE_BUY,
                "ord-other", "client-other", "AAPL",
                StrategyOrderSide.BUY, StrategyOrderType.LIMIT,
                new BigDecimal("8.00"), BigDecimal.ZERO,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO,
                StrategyOrderStatus.PENDING,
                Instant.now().minusSeconds(90), Instant.now().minusSeconds(90), null, "{}"
        ));
        f.service.onTradeUpdate(new AlpacaTradeUpdateEvent(
                "fill",
                new AlpacaOrderData("ord-other", "client-other", "AAPL", "buy", "limit",
                        new BigDecimal("8.00"), BigDecimal.ONE, new BigDecimal("8.00"), "filled", "{}")
        ));

        f.service.pollDueStrategies();

        f.awaitTrue(() -> f.orders.findByAlpacaOrderId("ord-sell")
                        .map(order -> order.status() == StrategyOrderStatus.FILLED)
                        .orElse(false),
                "Pending sell should reconcile from broker at configured polling interval");
        Strategy updated = f.strategies.findById(strategy.id()).orElseThrow();
        assertEquals(StrategyLifecycleState.COMPLETED, updated.currentState());
        assertEquals("filled", updated.latestOrderStatus());
        assertEquals(StrategyOrderStatus.FILLED, f.orders.findByAlpacaOrderId("ord-sell").orElseThrow().status());
    }

    @Test
    void profitableExitDoesNotRecreateStaleLossBuyBeforeRestart() {
        Fixture f = new Fixture();
        Strategy strategy = new Strategy(
                UUID.randomUUID().toString(), "s", "TSLA", StrategyMode.PAPER, StrategyStatus.ACTIVE,
                StrategyLifecycleState.BUY_LIMIT_1_PLACED,
                new BigDecimal("373.00"), 10,
                new BigDecimal("370.00"), 5,
                new BigDecimal("365.00"), 5,
                true, StopLossType.FIXED_PRICE, new BigDecimal("360.00"), BigDecimal.ZERO,
                false, BigDecimal.ZERO,
                true, new BigDecimal("380.00"), new BigDecimal("100.00"), true,
                false, ProfitHoldType.PERCENT_TRAILING, new BigDecimal("10.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                true, 20, new BigDecimal("7405.00"), 2, Instant.now(), Instant.now()
        );
        strategy.setMaxCapitalAllowed(new BigDecimal("10000.00"));
        f.strategies.save(strategy);
        f.addOrder(f.filledOrder(strategy.id(), StrategyStage.BASE_BUY, 10, new BigDecimal("373.00")));
        f.addOrder(f.filledOrder(strategy.id(), StrategyStage.TARGET_SELL, 10, new BigDecimal("392.17")));
        f.alpaca.position = Optional.empty();
        f.alpaca.latestPrice = new BigDecimal("392.17");

        f.service.pollStrategy(strategy.id());

        Strategy updated = f.strategies.findById(strategy.id()).orElseThrow();
        List<StrategyOrder> strategyOrders = f.orders.findByStrategyId(strategy.id());
        long submittedBuyLimit1Orders = strategyOrders.stream()
                .filter(order -> order.stage() == StrategyStage.BUY_LIMIT_1)
                .filter(StrategyOrder::isPending)
                .count();
        long submittedBaseOrders = strategyOrders.stream()
                .filter(order -> order.stage() == StrategyStage.BASE_BUY)
                .filter(StrategyOrder::isPending)
                .count();

        assertEquals(0, submittedBuyLimit1Orders);
        assertEquals(1, submittedBaseOrders);
        assertEquals(StrategyStatus.ACTIVE, updated.status());
        assertNotEquals(StrategyLifecycleState.FAILED, updated.currentState());
    }

    @Test
    void stopLossExitDoesNotRestartCycleWhenRepeatAfterProfitEnabled() {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        strategy.setRestartAfterExitEnabled(true);
        f.strategies.save(strategy);
        f.addOrder(f.filledOrder(strategy.id(), StrategyStage.BASE_BUY, 10, new BigDecimal("8.00")));
        f.addOrder(f.filledOrder(strategy.id(), StrategyStage.STOP_LOSS, 10, new BigDecimal("7.00")));
        f.alpaca.position = Optional.empty();

        f.service.pollStrategy(strategy.id());

        List<StrategyOrder> baseOrders = f.orders.findByStrategyId(strategy.id()).stream()
                .filter(order -> order.stage() == StrategyStage.BASE_BUY)
                .toList();
        assertEquals(1, baseOrders.size());
        assertEquals(StrategyStatus.COMPLETED, f.strategies.findById(strategy.id()).orElseThrow().status());
    }

    @Test
    void closePositionExitDoesNotRestartCycleWhenRepeatAfterProfitEnabled() {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        strategy.setRestartAfterExitEnabled(true);
        f.strategies.save(strategy);
        f.addOrder(f.filledOrder(strategy.id(), StrategyStage.BASE_BUY, 10, new BigDecimal("8.00")));
        f.addOrder(f.filledOrder(strategy.id(), StrategyStage.CLOSE_POSITION, 10, new BigDecimal("9.80")));
        f.alpaca.position = Optional.empty();

        f.service.pollStrategy(strategy.id());

        List<StrategyOrder> baseOrders = f.orders.findByStrategyId(strategy.id()).stream()
                .filter(order -> order.stage() == StrategyStage.BASE_BUY)
                .toList();
        assertEquals(1, baseOrders.size());
        assertEquals(StrategyStatus.COMPLETED, f.strategies.findById(strategy.id()).orElseThrow().status());
    }

    @Test
    void manualExitDoesNotRestartCycleWhenRepeatAfterExitDisabled() {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        strategy.setRestartAfterExitEnabled(false);
        f.strategies.save(strategy);
        f.addOrder(f.filledOrder(strategy.id(), StrategyStage.BASE_BUY, 10, new BigDecimal("8.00")));
        f.addOrder(f.filledOrder(strategy.id(), StrategyStage.MANUAL_EXIT, 10, new BigDecimal("9.80")));
        f.alpaca.position = Optional.empty();

        f.service.pollStrategy(strategy.id());

        List<StrategyOrder> baseOrders = f.orders.findByStrategyId(strategy.id()).stream()
                .filter(order -> order.stage() == StrategyStage.BASE_BUY)
                .toList();
        assertEquals(1, baseOrders.size());
        assertEquals(StrategyStatus.COMPLETED, f.strategies.findById(strategy.id()).orElseThrow().status());
    }

    @Test
    void pollingIntervalSkipsWhenStrategyIsNotYetDue() {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        strategy.setPollingIntervalSeconds(30);
        strategy.setLastPolledAt(Instant.now());
        f.strategies.save(strategy);

        int due = f.service.pollDueStrategies();
        StrategyPollingService.PollCycleSnapshot snapshot = f.service.lastPollCycleSnapshot();

        assertEquals(0, due);
        assertEquals(1, snapshot.eligible());
        assertEquals(0, snapshot.due());
        assertEquals(1, snapshot.skippedNotDue());
    }

    @Test
    void expiredAutoExtensionRetriesBeforeLongPollingIntervalAfterClosedMarketDetection() {
        Fixture f = new Fixture();
        f.service.pollDueStrategies();
        Strategy strategy = f.activeStrategy(false);
        strategy.setStatus(StrategyStatus.FAILED);
        strategy.setCurrentState(StrategyLifecycleState.FAILED);
        strategy.setLatestOrderStatus("expired");
        strategy.setResubmitOnExpiryEnabled(true);
        strategy.setPollingIntervalSeconds(3600);
        strategy.setLastPolledAt(Instant.now().minusSeconds(61));
        f.strategies.save(strategy);
        f.alpaca.position = Optional.empty();
        f.alpaca.orderById.clear();

        int due = f.service.pollDueStrategies();

        assertEquals(1, due);
        assertDoesNotThrow(() -> f.awaitTrue(() -> {
            Strategy updated = f.strategies.findById(strategy.id()).orElseThrow();
            return updated.status() == StrategyStatus.ACTIVE
                    && "new".equals(updated.latestOrderStatus())
                    && f.orders.findLatestByStrategyStage(strategy.id(), StrategyStage.BASE_BUY).isPresent();
        }, "Expired auto-extension strategy should retry without waiting for the long polling interval"));
    }

    @Test
    void streamHealthyBackoffDefersPollingUntilExtendedInterval() throws Exception {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        strategy.setPollingIntervalSeconds(2);
        strategy.setLastPolledAt(Instant.now().minusSeconds(3));
        f.strategies.save(strategy);

        Field streamField = StrategyPollingService.class.getDeclaredField("lastStreamingEventAt");
        streamField.setAccessible(true);
        streamField.set(f.service, Instant.now());

        int due = f.service.pollDueStrategies();
        StrategyPollingService.PollCycleSnapshot snapshot = f.service.lastPollCycleSnapshot();

        assertEquals(0, due);
        assertEquals(1, snapshot.eligible());
        assertEquals(0, snapshot.due());
        assertEquals(1, snapshot.skippedNotDue());
    }

    @Test
    void manualExitRestartsCycleWhenRepeatAfterExitEnabled() {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        strategy.setRestartAfterExitEnabled(true);
        f.strategies.save(strategy);
        f.addOrder(f.filledOrder(strategy.id(), StrategyStage.BASE_BUY, 10, new BigDecimal("8.00")));
        f.addOrder(f.filledOrder(strategy.id(), StrategyStage.MANUAL_EXIT, 10, new BigDecimal("9.80")));
        f.alpaca.position = Optional.empty();

        f.service.pollStrategy(strategy.id());

        List<StrategyOrder> baseOrders = f.orders.findByStrategyId(strategy.id()).stream()
                .filter(order -> order.stage() == StrategyStage.BASE_BUY)
                .toList();
        assertEquals(2, baseOrders.size());
        assertEquals(StrategyStatus.ACTIVE, f.strategies.findById(strategy.id()).orElseThrow().status());
    }

    @Test
    void profitableSellDoesNotRestartWhenPositionStillOpen() {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        strategy.setRestartAfterExitEnabled(true);
        f.strategies.save(strategy);
        f.addOrder(f.filledOrder(strategy.id(), StrategyStage.BASE_BUY, 10, new BigDecimal("8.00")));
        f.addOrder(f.filledOrder(strategy.id(), StrategyStage.TARGET_SELL, 9, new BigDecimal("10.00")));
        f.alpaca.position = Optional.of(new AlpacaPositionData("AAPL", new BigDecimal("1"), new BigDecimal("8.00"), new BigDecimal("10.00"), "{}"));
        f.alpaca.latestPrice = new BigDecimal("10.00");

        f.service.pollStrategy(strategy.id());

        List<StrategyOrder> baseOrders = f.orders.findByStrategyId(strategy.id()).stream()
                .filter(order -> order.stage() == StrategyStage.BASE_BUY)
                .toList();
        assertEquals(1, baseOrders.size());
        assertEquals(StrategyStatus.ACTIVE, f.strategies.findById(strategy.id()).orElseThrow().status());
    }

    @Test
    void marketCloseSuppressionDoesNotPersistentlyAutoPauseActiveStrategies() {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        f.marketHoursService.open = false;

        f.service.pollDueStrategies();

        Strategy updated = f.strategies.findById(strategy.id()).orElseThrow();
        assertEquals(StrategyStatus.ACTIVE, updated.status());
        assertEquals(PauseReason.NONE, updated.pauseReason());
        assertEquals(0, f.alpaca.positionCalls);
        assertEquals(0, f.alpaca.priceCalls);
        assertEquals(0, f.alpaca.openOrderCalls);
    }

    @Test
    void autoResumeWhenMarketOpens() {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        f.marketHoursService.open = true;
        strategy.setStatus(StrategyStatus.PAUSED);
        strategy.setCurrentState(StrategyLifecycleState.PAUSED);
        strategy.setPauseReason(PauseReason.AUTO_MARKET_CLOSED);
        f.strategies.save(strategy);

        f.service.pollDueStrategies();

        Strategy updated = f.strategies.findById(strategy.id()).orElseThrow();
        assertEquals(StrategyStatus.ACTIVE, updated.status());
        assertEquals(PauseReason.NONE, updated.pauseReason());

        StrategyPollingService.MarketClosedAutoRepairSummary summary = f.service.drainMarketClosedAutoRepairedStrategyIds();
        assertEquals(List.of(strategy.id()), summary.strategyIds());
        assertTrue(f.service.drainMarketClosedAutoRepairedStrategyIds().isEmpty());
    }

    @Test
    void manualPauseIsNotOverwrittenByAutoResume() {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        strategy.setStatus(StrategyStatus.PAUSED);
        strategy.setCurrentState(StrategyLifecycleState.PAUSED);
        strategy.setPauseReason(PauseReason.USER_PAUSED);
        f.strategies.save(strategy);

        f.service.pollDueStrategies();

        Strategy updated = f.strategies.findById(strategy.id()).orElseThrow();
        assertEquals(StrategyStatus.PAUSED, updated.status());
        assertEquals(PauseReason.USER_PAUSED, updated.pauseReason());
    }

    @Test
    void pollingDoesNotCallAlpacaRepeatedlyWhenAutoPaused() {
        Fixture f = new Fixture();
        f.activeStrategy(false);
        f.marketHoursService.open = false;

        f.service.pollDueStrategies();
        f.service.pollDueStrategies();

        assertEquals(0, f.alpaca.positionCalls);
        assertEquals(0, f.alpaca.priceCalls);
        assertEquals(0, f.alpaca.openOrderCalls);
    }

    @Test
    void marketClosedPollingRefreshesDueWaitingOrderStatusWithoutTradingCalls() throws Exception {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        strategy.setCurrentState(StrategyLifecycleState.BASE_BUY_PLACED);
        strategy.setLatestOrderStatus("new");
        strategy.setLatestAlpacaOrderId("ord-expired");
        strategy.setLastPolledAt(Instant.now().minusSeconds(60));
        f.strategies.save(strategy);
        f.orders.save(new StrategyOrder(
                UUID.randomUUID().toString(),
                strategy.id(),
                StrategyStage.BASE_BUY,
                "ord-expired",
                "client-expired",
                "AAPL",
                StrategyOrderSide.BUY,
                StrategyOrderType.LIMIT,
                new BigDecimal("8.00"),
                BigDecimal.ZERO,
                new BigDecimal("10"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                StrategyOrderStatus.SUBMITTED,
                Instant.now(),
                Instant.now(),
                null,
                "{}"
        ));
        f.alpaca.orderById.put("ord-expired", new AlpacaOrderData(
                "ord-expired",
                "client-expired",
                "AAPL",
                "buy",
                "limit",
                new BigDecimal("8.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "expired",
                "{}"
        ));
        f.marketHoursService.open = false;

        int due = f.service.pollDueStrategies();

        assertEquals(0, due);
        f.awaitTrue(() -> f.alpaca.orderCalls == 1, "Market-closed order status refresh should be dispatched");
        assertEquals(1, f.alpaca.orderCalls);
        assertEquals(0, f.alpaca.positionCalls);
        assertEquals(0, f.alpaca.priceCalls);
        assertEquals(0, f.alpaca.openOrderCalls);
        f.awaitTrue(() -> f.strategies.findById(strategy.id()).orElseThrow().status() == StrategyStatus.FAILED,
                "Market-closed order status refresh should reconcile expired status");
        Strategy updated = f.strategies.findById(strategy.id()).orElseThrow();
        assertEquals(StrategyStatus.FAILED, updated.status());
        assertEquals(StrategyLifecycleState.FAILED, updated.currentState());
        assertEquals("expired", updated.latestOrderStatus());
    }

    @Test
    void marketClosedExpiryRefreshImmediatelyRepositionsWhenResubmitEnabled() throws Exception {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        strategy.setCurrentState(StrategyLifecycleState.BASE_BUY_PLACED);
        strategy.setLatestOrderStatus("new");
        strategy.setLatestAlpacaOrderId("ord-expired");
        strategy.setLastPolledAt(Instant.now().minusSeconds(60));
        strategy.setResubmitOnExpiryEnabled(true);
        f.strategies.save(strategy);
        f.orders.save(new StrategyOrder(
                UUID.randomUUID().toString(),
                strategy.id(),
                StrategyStage.BASE_BUY,
                "ord-expired",
                "client-expired",
                "AAPL",
                StrategyOrderSide.BUY,
                StrategyOrderType.LIMIT,
                new BigDecimal("8.00"),
                BigDecimal.ZERO,
                new BigDecimal("10"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                StrategyOrderStatus.SUBMITTED,
                Instant.now(),
                Instant.now(),
                null,
                "{}"
        ));
        f.alpaca.orderById.put("ord-expired", new AlpacaOrderData(
                "ord-expired",
                "client-expired",
                "AAPL",
                "buy",
                "limit",
                new BigDecimal("8.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "expired",
                "{}"
        ));
        f.marketHoursService.open = false;

        int due = f.service.pollDueStrategies();

        assertEquals(0, due);
        f.awaitTrue(() -> {
            Strategy updated = f.strategies.findById(strategy.id()).orElseThrow();
            return updated.status() == StrategyStatus.ACTIVE
                    && updated.currentState() == StrategyLifecycleState.BASE_BUY_PLACED
                    && "new".equals(updated.latestOrderStatus());
        }, "Expired base buy should be repositioned immediately after market-closed status refresh");
        // The expired order is canceled in the same async flow; await it rather than asserting
        // synchronously, otherwise the check can race ahead of the cancellation.
        f.awaitTrue(() -> f.orders.findByClientOrderId("client-expired")
                        .map(order -> order.status() == StrategyOrderStatus.CANCELED)
                        .orElse(false),
                "Expired base buy order should be canceled after repositioning");
    }

    @Test
    void overnightEligibleSymbolCanPollWhenGlobalSessionIsClosed() throws Exception {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        strategy.setLastPolledAt(Instant.now().minusSeconds(60));
        f.strategies.save(strategy);
        f.settingsService.save(new AppSettingsService.AppSettings(
                "test@example.com",
                true,
                true,
                true,
                BrokerType.ALPACA,
                ApplicationMode.PAPER,
                false
        ));
        f.marketHoursService.open = false;
        f.alpaca.overnightEligibleBySymbol.put(strategy.symbol().toUpperCase(Locale.ROOT), true);

        int due = f.service.pollDueStrategies();

        assertEquals(1, due);
    }

    @Test
    void staleManualMarketClosedOverrideIsClearedWhenOvernightSessionIsOpen() throws Exception {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        strategy.setPauseReason(PauseReason.MANUAL_MARKET_CLOSED_OVERRIDE);
        strategy.setLastPolledAt(Instant.now().minusSeconds(60));
        f.strategies.save(strategy);
        f.settingsService.save(new AppSettingsService.AppSettings(
                "test@example.com",
                true,
                true,
                true,
                BrokerType.ALPACA,
                ApplicationMode.PAPER,
                false
        ));
        f.marketHoursService.open = false;
        f.alpaca.overnightEligibleBySymbol.put(strategy.symbol().toUpperCase(Locale.ROOT), true);

        f.service.pollDueStrategies();

        Strategy updated = f.strategies.findById(strategy.id()).orElseThrow();
        assertEquals(PauseReason.NONE, updated.pauseReason());

        StrategyPollingService.MarketClosedAutoRepairSummary summary = f.service.drainMarketClosedAutoRepairedStrategyIds();
        assertEquals(List.of(strategy.id()), summary.strategyIds());
    }

    private static final class Fixture {
        final InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        final InMemoryOrderRepository orders = new InMemoryOrderRepository();
        final InMemoryEventRepository events = new InMemoryEventRepository();
        final FakeAlpacaClient alpaca = new FakeAlpacaClient();
        final MutableMarketHoursService marketHoursService = new MutableMarketHoursService();
        final AppSettingsService settingsService;
        final StrategyPollingService service;

        Fixture() {
            try {
                Path settingsPath = Files.createTempDirectory("neuralarc-test").resolve("settings.properties");
                settingsService = new AppSettingsService(settingsPath);
                settingsService.save(new AppSettingsService.AppSettings(
                        "test@example.com",
                        true,
                        true,
                        false,
                        BrokerType.ALPACA,
                        ApplicationMode.PAPER,
                        false
                ));
                service = new StrategyPollingService(strategies, orders, events, alpaca, settingsService, marketHoursService);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }

        Strategy activeStrategy(boolean profitHold) {
            return activeStrategy("AAPL", profitHold);
        }

        Strategy activeStrategy(String symbol, boolean profitHold) {
            Strategy strategy = new Strategy(
                    UUID.randomUUID().toString(), "s", symbol, StrategyMode.PAPER, StrategyStatus.ACTIVE,
                    StrategyLifecycleState.CREATED,
                    new BigDecimal("8.00"), 10,
                    new BigDecimal("6.00"), 5,
                    new BigDecimal("5.00"), 5,
                    true, StopLossType.FIXED_PRICE, new BigDecimal("7.00"), BigDecimal.ZERO,
                    false, BigDecimal.ZERO,
                    true, new BigDecimal("10.00"), new BigDecimal("100.00"), true,
                    profitHold, ProfitHoldType.PERCENT_TRAILING, new BigDecimal("10.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                    false, 25, new BigDecimal("300.00"), 2, Instant.now(), Instant.now()
            );
            strategies.save(strategy);
            return strategy;
        }

        Instant awaitLastPolledAfter(String strategyId, Instant baseline, String message) throws Exception {
            long deadline = System.currentTimeMillis() + 10_000L;
            while (System.currentTimeMillis() < deadline) {
                Instant current = strategies.findById(strategyId).orElseThrow().lastPolledAt();
                if (current != null && current.isAfter(baseline)) {
                    return current;
                }
                Thread.sleep(10L);
            }
            fail(message);
            return baseline;
        }

        void awaitTrue(BooleanSupplier condition, String message) throws Exception {
            long deadline = System.currentTimeMillis() + 10_000L;
            while (System.currentTimeMillis() < deadline) {
                if (condition.getAsBoolean()) {
                    return;
                }
                Thread.sleep(10L);
            }
            fail(message);
        }

        StrategyOrder filledOrder(String strategyId, StrategyStage stage, int qty, BigDecimal price) {
            return new StrategyOrder(
                    UUID.randomUUID().toString(), strategyId, stage,
                    "ord-" + stage.name(), "client-" + stage.name(), "AAPL",
                    stage == StrategyStage.TARGET_SELL
                            || stage == StrategyStage.PROFIT_EXIT
                            || stage == StrategyStage.STOP_LOSS
                            || stage == StrategyStage.MANUAL_EXIT
                            || stage == StrategyStage.CLOSE_POSITION
                            ? StrategyOrderSide.SELL
                            : StrategyOrderSide.BUY,
                    StrategyOrderType.LIMIT,
                    price,
                    BigDecimal.ZERO,
                    new BigDecimal(String.valueOf(qty)),
                    new BigDecimal(String.valueOf(qty)),
                    price,
                    StrategyOrderStatus.FILLED,
                    Instant.now(),
                    Instant.now(),
                    Instant.now(),
                    "{}"
            );
        }

        void addOrder(StrategyOrder order) {
            orders.save(order);
            alpaca.orderById.put(order.alpacaOrderId(), new AlpacaOrderData(
                    order.alpacaOrderId(), order.clientOrderId(), order.symbol(), order.side().name().toLowerCase(),
                    "limit", order.limitPrice(), order.filledAveragePrice(), order.filledQuantity(), order.status().name().toLowerCase(), "{}"
            ));
        }
    }

    private static final class FakeAlpacaClient implements AlpacaClient {
        BigDecimal latestPrice = new BigDecimal("8.00");
        Optional<AlpacaPositionData> position = Optional.empty();
        final Map<String, AlpacaPositionData> positionsBySymbol = new ConcurrentHashMap<>();
        final Map<String, AlpacaOrderData> orderById = new HashMap<>();
        final Map<String, Boolean> overnightEligibleBySymbol = new HashMap<>();
        int positionsBatchCalls;
        int openOrdersBatchCalls;
        int orderCounter;
        int positionCalls;
        int priceCalls;
        int openOrderCalls;
        int orderCalls;
        BigDecimal lastTrailPercent = BigDecimal.ZERO;
        BigDecimal lastTrailPrice = BigDecimal.ZERO;
        String nextLimitSellStatus = "new";
        BigDecimal nextLimitSellFilledQuantity = Monetary.zero();
        BigDecimal nextLimitSellFilledAveragePrice = Monetary.zero();
        private volatile CountDownLatch openOrdersEnteredLatch;
        private volatile CountDownLatch openOrdersReleaseLatch;
        private volatile boolean failNextOpenOrdersBatch;
        private final Map<String, CountDownLatch> openOrdersEnteredBySymbol = new ConcurrentHashMap<>();
        private final Map<String, CountDownLatch> openOrdersReleaseBySymbol = new ConcurrentHashMap<>();
        private final Map<String, Integer> openOrderCallsBySymbol = new ConcurrentHashMap<>();

        @Override
        public AlpacaOrderData submitLimitBuyOrder(String symbol, int quantity, BigDecimal limitPrice, String clientOrderId) {
            return submit(symbol, "buy", quantity, limitPrice, clientOrderId);
        }

        @Override
        public AlpacaOrderData submitLimitSellOrder(String symbol, int quantity, BigDecimal limitPrice, String clientOrderId) {
            return submitSell(symbol, quantity, limitPrice, clientOrderId);
        }

        @Override
        public AlpacaOrderData submitMarketSellOrder(String symbol, int quantity, String clientOrderId) {
            orderCounter++;
            String orderId = "ord-" + orderCounter;
            AlpacaOrderData data = new AlpacaOrderData(orderId, clientOrderId, symbol, "sell", "market", Monetary.zero(), Monetary.zero(), Monetary.zero(), "new", "{}");
            orderById.put(orderId, data);
            return data;
        }

        @Override
        public AlpacaOrderData submitTrailingStopSellOrder(String symbol, int quantity, BigDecimal trailPercent, BigDecimal trailPrice, String clientOrderId) {
            orderCounter++;
            String orderId = "ord-" + orderCounter;
            lastTrailPercent = trailPercent == null ? BigDecimal.ZERO : trailPercent;
            lastTrailPrice = trailPrice == null ? BigDecimal.ZERO : trailPrice;
            BigDecimal effectiveLimit = trailPrice != null && trailPrice.compareTo(BigDecimal.ZERO) > 0
                    ? trailPrice
                    : Monetary.zero();
            AlpacaOrderData data = new AlpacaOrderData(orderId, clientOrderId, symbol, "sell", "trailing_stop", effectiveLimit, Monetary.zero(), Monetary.zero(), "new", "{}");
            orderById.put(orderId, data);
            return data;
        }

        private AlpacaOrderData submit(String symbol, String side, int quantity, BigDecimal limitPrice, String clientOrderId) {
            orderCounter++;
            String orderId = "ord-" + orderCounter;
            AlpacaOrderData data = new AlpacaOrderData(orderId, clientOrderId, symbol, side, "limit", limitPrice, Monetary.zero(), Monetary.zero(), "new", "{}");
            orderById.put(orderId, data);
            return data;
        }

        private AlpacaOrderData submitSell(String symbol, int quantity, BigDecimal limitPrice, String clientOrderId) {
            orderCounter++;
            String orderId = "ord-" + orderCounter;
            AlpacaOrderData data = new AlpacaOrderData(
                    orderId,
                    clientOrderId,
                    symbol,
                    "sell",
                    "limit",
                    limitPrice,
                    nextLimitSellFilledAveragePrice,
                    nextLimitSellFilledQuantity,
                    nextLimitSellStatus,
                    "{}"
            );
            orderById.put(orderId, data);
            nextLimitSellStatus = "new";
            nextLimitSellFilledQuantity = Monetary.zero();
            nextLimitSellFilledAveragePrice = Monetary.zero();
            return data;
        }

        @Override
        public Optional<AlpacaOrderData> getOrder(String orderId) {
            orderCalls++;
            return Optional.ofNullable(orderById.get(orderId));
        }

        @Override
        public List<AlpacaOrderData> getOpenOrders(String symbol) {
            openOrderCalls++;
            String normalizedSymbol = normalize(symbol);
            openOrderCallsBySymbol.merge(normalizedSymbol, 1, Integer::sum);
            awaitOpenOrdersGateIfConfigured();
            awaitOpenOrdersGateIfConfigured(normalizedSymbol);
            return orderById.values().stream()
                    .filter(o -> o.symbol().equalsIgnoreCase(symbol))
                    .filter(this::isOpenOrder)
                    .toList();
        }

        @Override
        public List<AlpacaOrderData> getOpenOrders() {
            if (failNextOpenOrdersBatch) {
                failNextOpenOrdersBatch = false;
                throw new RuntimeException("simulated combined snapshot batch failure");
            }
            openOrderCalls++;
            openOrdersBatchCalls++;
            awaitOpenOrdersGateIfConfigured();
            return orderById.values().stream().filter(this::isOpenOrder).toList();
        }

        /** Forces the next shared batch snapshot fetch to fail, so per-strategy resolution falls back
         *  to the individual getPosition/getOpenOrders(symbol) calls exercised by the blocking gates above. */
        void failNextOpenOrdersBatch() {
            failNextOpenOrdersBatch = true;
        }

        @Override
        public boolean cancelOrder(String orderId) {
            return orderById.remove(orderId) != null;
        }

        @Override
        public Optional<AlpacaPositionData> getPosition(String symbol) {
            positionCalls++;
            AlpacaPositionData bySymbol = positionsBySymbol.get(normalize(symbol));
            return bySymbol != null ? Optional.of(bySymbol) : position;
        }

        @Override
        public List<AlpacaPositionData> getPositions() {
            positionsBatchCalls++;
            if (!positionsBySymbol.isEmpty()) {
                return new ArrayList<>(positionsBySymbol.values());
            }
            return position.map(List::of).orElseGet(List::of);
        }

        @Override
        public BigDecimal getLatestPrice(String symbol) {
            priceCalls++;
            return latestPrice;
        }

        @Override
        public boolean supportsOvernightSession(String symbol) {
            if (symbol == null) {
                return false;
            }
            return overnightEligibleBySymbol.getOrDefault(symbol.trim().toUpperCase(Locale.ROOT), false);
        }

        private boolean isOpenOrder(AlpacaOrderData order) {
            StrategyOrderStatus status = StrategyService.mapOrderStatus(order.status());
            return status == StrategyOrderStatus.SUBMITTED
                    || status == StrategyOrderStatus.PENDING
                    || status == StrategyOrderStatus.PARTIALLY_FILLED;
        }

        void blockNextOpenOrdersCall() {
            openOrdersEnteredLatch = new CountDownLatch(1);
            openOrdersReleaseLatch = new CountDownLatch(1);
        }

        void blockOpenOrdersForSymbol(String symbol) {
            String normalizedSymbol = normalize(symbol);
            openOrdersEnteredBySymbol.put(normalizedSymbol, new CountDownLatch(1));
            openOrdersReleaseBySymbol.put(normalizedSymbol, new CountDownLatch(1));
        }

        boolean awaitOpenOrdersBlock() throws InterruptedException {
            CountDownLatch entered = openOrdersEnteredLatch;
            return entered != null && entered.await(2, TimeUnit.SECONDS);
        }

        boolean awaitOpenOrdersBlock(String symbol) throws InterruptedException {
            CountDownLatch entered = openOrdersEnteredBySymbol.get(normalize(symbol));
            return entered != null && entered.await(2, TimeUnit.SECONDS);
        }

        void releaseBlockedOpenOrders() {
            CountDownLatch release = openOrdersReleaseLatch;
            if (release != null) {
                release.countDown();
            }
        }

        void releaseBlockedOpenOrders(String symbol) {
            CountDownLatch release = openOrdersReleaseBySymbol.get(normalize(symbol));
            if (release != null) {
                release.countDown();
            }
        }

        int openOrderCallsForSymbol(String symbol) {
            return openOrderCallsBySymbol.getOrDefault(normalize(symbol), 0);
        }

        private void awaitOpenOrdersGateIfConfigured() {
            CountDownLatch entered = openOrdersEnteredLatch;
            CountDownLatch release = openOrdersReleaseLatch;
            if (entered == null || release == null) {
                return;
            }
            entered.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                openOrdersEnteredLatch = null;
                openOrdersReleaseLatch = null;
            }
        }

        private void awaitOpenOrdersGateIfConfigured(String normalizedSymbol) {
            CountDownLatch entered = openOrdersEnteredBySymbol.get(normalizedSymbol);
            CountDownLatch release = openOrdersReleaseBySymbol.get(normalizedSymbol);
            if (entered == null || release == null) {
                return;
            }
            entered.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                openOrdersEnteredBySymbol.remove(normalizedSymbol);
                openOrdersReleaseBySymbol.remove(normalizedSymbol);
            }
        }

        private String normalize(String symbol) {
            return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        }
    }

    private static final class MutableMarketHoursService extends MarketHoursService {
        private boolean open = true;

        @Override
        public boolean isTradingSessionOpen(boolean extendedHoursEnabled) {
            return open;
        }

        @Override
        public boolean isTradingSessionOpen(Instant instant, boolean extendedHoursEnabled) {
            return open;
        }

        @Override
        public boolean isTradingSessionOpen(Instant instant, boolean extendedHoursEnabled, boolean overnightHoursEnabled) {
            return open || (extendedHoursEnabled && overnightHoursEnabled);
        }

        @Override
        public Instant nextMarketOpen(boolean extendedHoursEnabled) {
            return ZonedDateTime.of(2026, 4, 30, 9, 30, 0, 0, ZoneId.of("America/New_York")).toInstant();
        }

        @Override
        public Instant nextMarketOpen(Instant instant, boolean extendedHoursEnabled) {
            return nextMarketOpen(extendedHoursEnabled);
        }
    }

    private static final class InMemoryStrategyRepository implements StrategyRepository {
        private final Map<String, Strategy> store = new HashMap<>();
        @Override public synchronized void save(Strategy strategy) { store.put(strategy.id(), strategy); }
        @Override public synchronized Optional<Strategy> findById(String id) { return Optional.ofNullable(store.get(id)); }
        @Override public synchronized List<Strategy> findAll() { return new ArrayList<>(store.values()); }
        @Override public synchronized List<Strategy> findActive() { return findAll().stream().filter(s -> s.status() == StrategyStatus.ACTIVE).toList(); }
        @Override public synchronized void deleteById(String id) { store.remove(id); }
    }

    private static final class InMemoryOrderRepository implements StrategyOrderRepository {
        private final List<StrategyOrder> orders = new ArrayList<>();
        @Override public synchronized void save(StrategyOrder order) {
            orders.removeIf(o -> o.id().equals(order.id()));
            orders.add(order);
        }
        @Override public synchronized List<StrategyOrder> findByStrategyId(String strategyId) { return orders.stream().filter(o -> o.strategyId().equals(strategyId)).toList(); }
        @Override public synchronized Optional<StrategyOrder> findLatestByStrategyStage(String strategyId, StrategyStage stage) {
            return findByStrategyId(strategyId).stream().filter(o -> o.stage() == stage)
                    .max(Comparator.comparing(StrategyOrder::submittedAt));
        }
        @Override public synchronized Optional<StrategyOrder> findByAlpacaOrderId(String alpacaOrderId) {
            return orders.stream()
                    .filter(order -> alpacaOrderId != null && alpacaOrderId.equals(order.alpacaOrderId()))
                    .max(Comparator.comparing(StrategyOrder::submittedAt));
        }
        @Override public synchronized Optional<StrategyOrder> findByClientOrderId(String clientOrderId) {
            return orders.stream()
                    .filter(order -> clientOrderId != null && clientOrderId.equals(order.clientOrderId()))
                    .max(Comparator.comparing(StrategyOrder::submittedAt));
        }
        @Override public synchronized void deleteByStrategyId(String strategyId) { orders.removeIf(order -> order.strategyId().equals(strategyId)); }
    }

    private static final class InMemoryEventRepository implements StrategyExecutionEventRepository {
        private final List<StrategyExecutionEvent> events = new ArrayList<>();
        @Override public synchronized void save(StrategyExecutionEvent event) { events.add(event); }
        @Override public synchronized List<StrategyExecutionEvent> findByStrategyId(String strategyId) { return events.stream().filter(e -> e.strategyId().equals(strategyId)).toList(); }
        @Override public synchronized void deleteByStrategyId(String strategyId) { events.removeIf(event -> event.strategyId().equals(strategyId)); }
    }

    private static final class RecordingTradeEmailNotificationService extends TradeEmailNotificationService {
        private final List<String> sellExecuted = new ArrayList<>();

        private RecordingTradeEmailNotificationService(AppSettingsService settingsService) {
            super(settingsService, (recipientEmail, subject, textBody, htmlBody) -> {}, Runnable::run, "ops@example.com");
        }

        @Override
        public void notifySellExecuted(Strategy strategy, StrategyOrder order) {
            sellExecuted.add(strategy.symbol() + ":" + order.stage() + ":" + order.status());
        }
    }
}
