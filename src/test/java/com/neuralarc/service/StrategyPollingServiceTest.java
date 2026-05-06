package com.neuralarc.service;

import com.neuralarc.api.AlpacaClient;
import com.neuralarc.api.AlpacaOrderData;
import com.neuralarc.api.AlpacaPositionData;
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
import java.util.concurrent.TimeUnit;

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
    void manualPauseDuringInFlightPollDoesNotRecreateBaseBuy() throws Exception {
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
        paused.setPauseReason(PauseReason.USER_PAUSED);
        f.strategies.save(paused);

        f.alpaca.releaseBlockedOpenOrders();
        pollingThread.join(3000L);

        Strategy after = f.strategies.findById(strategy.id()).orElseThrow();
        assertEquals(StrategyStatus.PAUSED, after.status());
        assertEquals(PauseReason.USER_PAUSED, after.pauseReason());

        List<StrategyOrder> ordersAfter = f.orders.findByStrategyId(strategy.id());
        long pendingBaseOrders = ordersAfter.stream()
                .filter(order -> order.stage() == StrategyStage.BASE_BUY)
                .filter(order -> order.status() == StrategyOrderStatus.SUBMITTED || order.status() == StrategyOrderStatus.PENDING)
                .count();
        assertEquals(0L, pendingBaseOrders, "No base-buy re-submit after manual pause during in-flight poll");
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
    void autoPauseWhenMarketClosed() {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        f.marketHoursService.open = false;

        f.service.pollDueStrategies();

        Strategy updated = f.strategies.findById(strategy.id()).orElseThrow();
        assertEquals(StrategyStatus.PAUSED, updated.status());
        assertEquals(PauseReason.AUTO_MARKET_CLOSED, updated.pauseReason());
        assertEquals(0, f.alpaca.positionCalls);
        assertEquals(0, f.alpaca.priceCalls);
        assertEquals(0, f.alpaca.openOrderCalls);
    }

    @Test
    void autoResumeWhenMarketOpens() {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        strategy.setStatus(StrategyStatus.PAUSED);
        strategy.setCurrentState(StrategyLifecycleState.PAUSED);
        strategy.setPauseReason(PauseReason.AUTO_MARKET_CLOSED);
        f.strategies.save(strategy);

        f.service.pollDueStrategies();

        Strategy updated = f.strategies.findById(strategy.id()).orElseThrow();
        assertEquals(StrategyStatus.ACTIVE, updated.status());
        assertEquals(PauseReason.NONE, updated.pauseReason());
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
            Strategy strategy = new Strategy(
                    UUID.randomUUID().toString(), "s", "AAPL", StrategyMode.PAPER, StrategyStatus.ACTIVE,
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
        final Map<String, AlpacaOrderData> orderById = new HashMap<>();
        int orderCounter;
        int positionCalls;
        int priceCalls;
        int openOrderCalls;
        private volatile CountDownLatch openOrdersEnteredLatch;
        private volatile CountDownLatch openOrdersReleaseLatch;

        @Override
        public AlpacaOrderData submitLimitBuyOrder(String symbol, int quantity, BigDecimal limitPrice, String clientOrderId) {
            return submit(symbol, "buy", quantity, limitPrice, clientOrderId);
        }

        @Override
        public AlpacaOrderData submitLimitSellOrder(String symbol, int quantity, BigDecimal limitPrice, String clientOrderId) {
            return submit(symbol, "sell", quantity, limitPrice, clientOrderId);
        }

        @Override
        public AlpacaOrderData submitTrailingStopSellOrder(String symbol, int quantity, BigDecimal trailPercent, BigDecimal trailPrice, String clientOrderId) {
            orderCounter++;
            String orderId = "ord-" + orderCounter;
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

        @Override
        public Optional<AlpacaOrderData> getOrder(String orderId) {
            return Optional.ofNullable(orderById.get(orderId));
        }

        @Override
        public List<AlpacaOrderData> getOpenOrders(String symbol) {
            openOrderCalls++;
            awaitOpenOrdersGateIfConfigured();
            return orderById.values().stream().filter(o -> o.symbol().equalsIgnoreCase(symbol)).toList();
        }

        @Override
        public List<AlpacaOrderData> getOpenOrders() {
            openOrderCalls++;
            awaitOpenOrdersGateIfConfigured();
            return new ArrayList<>(orderById.values());
        }

        @Override
        public boolean cancelOrder(String orderId) {
            return orderById.remove(orderId) != null;
        }

        @Override
        public Optional<AlpacaPositionData> getPosition(String symbol) {
            positionCalls++;
            return position;
        }

        @Override
        public List<AlpacaPositionData> getPositions() {
            return position.map(List::of).orElseGet(List::of);
        }

        @Override
        public BigDecimal getLatestPrice(String symbol) {
            priceCalls++;
            return latestPrice;
        }

        void blockNextOpenOrdersCall() {
            openOrdersEnteredLatch = new CountDownLatch(1);
            openOrdersReleaseLatch = new CountDownLatch(1);
        }

        boolean awaitOpenOrdersBlock() throws InterruptedException {
            CountDownLatch entered = openOrdersEnteredLatch;
            return entered != null && entered.await(2, TimeUnit.SECONDS);
        }

        void releaseBlockedOpenOrders() {
            CountDownLatch release = openOrdersReleaseLatch;
            if (release != null) {
                release.countDown();
            }
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
        @Override public void save(Strategy strategy) { store.put(strategy.id(), strategy); }
        @Override public Optional<Strategy> findById(String id) { return Optional.ofNullable(store.get(id)); }
        @Override public List<Strategy> findAll() { return new ArrayList<>(store.values()); }
        @Override public List<Strategy> findActive() { return findAll().stream().filter(s -> s.status() == StrategyStatus.ACTIVE).toList(); }
        @Override public void deleteById(String id) { store.remove(id); }
    }

    private static final class InMemoryOrderRepository implements StrategyOrderRepository {
        private final List<StrategyOrder> orders = new ArrayList<>();
        @Override public void save(StrategyOrder order) {
            orders.removeIf(o -> o.id().equals(order.id()));
            orders.add(order);
        }
        @Override public List<StrategyOrder> findByStrategyId(String strategyId) { return orders.stream().filter(o -> o.strategyId().equals(strategyId)).toList(); }
        @Override public Optional<StrategyOrder> findLatestByStrategyStage(String strategyId, StrategyStage stage) {
            return findByStrategyId(strategyId).stream().filter(o -> o.stage() == stage)
                    .max(Comparator.comparing(StrategyOrder::submittedAt));
        }
        @Override public Optional<StrategyOrder> findByAlpacaOrderId(String alpacaOrderId) {
            return orders.stream()
                    .filter(order -> alpacaOrderId != null && alpacaOrderId.equals(order.alpacaOrderId()))
                    .max(Comparator.comparing(StrategyOrder::submittedAt));
        }
        @Override public Optional<StrategyOrder> findByClientOrderId(String clientOrderId) {
            return orders.stream()
                    .filter(order -> clientOrderId != null && clientOrderId.equals(order.clientOrderId()))
                    .max(Comparator.comparing(StrategyOrder::submittedAt));
        }
        @Override public void deleteByStrategyId(String strategyId) { orders.removeIf(order -> order.strategyId().equals(strategyId)); }
    }

    private static final class InMemoryEventRepository implements StrategyExecutionEventRepository {
        private final List<StrategyExecutionEvent> events = new ArrayList<>();
        @Override public void save(StrategyExecutionEvent event) { events.add(event); }
        @Override public List<StrategyExecutionEvent> findByStrategyId(String strategyId) { return events.stream().filter(e -> e.strategyId().equals(strategyId)).toList(); }
        @Override public void deleteByStrategyId(String strategyId) { events.removeIf(event -> event.strategyId().equals(strategyId)); }
    }
}
