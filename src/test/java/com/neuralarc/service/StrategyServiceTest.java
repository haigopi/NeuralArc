package com.neuralarc.service;

import com.neuralarc.api.AlpacaClient;
import com.neuralarc.api.AlpacaOrderData;
import com.neuralarc.api.AlpacaPositionData;
import com.neuralarc.model.*;
import com.neuralarc.util.Monetary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class StrategyServiceTest {
    @Test
    void mapOrderStatusTreatsBrokerFailedAsInfrastructureFailure() {
        assertEquals(StrategyOrderStatus.FAILED, StrategyService.mapOrderStatus("failed"));
    }

    @Test
    void mapOrderStatusTreatsTransportFailureAsFailed() {
        assertEquals(StrategyOrderStatus.FAILED, StrategyService.mapOrderStatus("failed_transport"));
    }

    @Test
    void mapOrderStatusTreatsApiErrorAsFailed() {
        assertEquals(StrategyOrderStatus.FAILED, StrategyService.mapOrderStatus("api_error"));
    }

    @Test
    void strategyValidationFailsForEmptySymbol() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        StrategyService service = service(strategies, orders, events, new FakeAlpacaClient());

        Strategy invalid = baseStrategy("", 10, new BigDecimal("8.00"));
        StrategyService.StrategyCreationResult result = service.createAndActivate(invalid);

        assertFalse(result.success());
        assertEquals(StrategyStatus.FAILED, strategies.findById(invalid.id()).orElseThrow().status());
    }

    @Test
    void initialOrderIsPlacedAfterSave() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        StrategyService service = service(strategies, orders, events, alpaca);

        Strategy strategy = baseStrategy("AAPL", 10, new BigDecimal("8.00"));
        StrategyService.StrategyCreationResult result = service.createAndActivate(strategy);

        assertTrue(result.success());
        Strategy stored = strategies.findById(strategy.id()).orElseThrow();
        assertEquals(StrategyStatus.ACTIVE, stored.status());
        assertEquals(StrategyLifecycleState.BASE_BUY_PLACED, stored.currentState());
        StrategyOrder initial = orders.findLatestByStrategyStage(strategy.id(), StrategyStage.BASE_BUY).orElseThrow();
        assertEquals(StrategyOrderType.LIMIT, initial.orderType());
        assertTrue(initial.clientOrderId().startsWith("neuralarc-" + strategy.id() + "-BASE_BUY-"));
    }

    @Test
    void initialOrderUsesStrategyDialogConfigurationValues() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        StrategyService service = service(strategies, orders, events, alpaca);
        StrategyConfig config = new StrategyConfig(
                "msft",
                new BigDecimal("412.34"),
                7,
                true,
                new BigDecimal("390.00"),
                true,
                new BigDecimal("450.00"),
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                0,
                false,
                false,
                BigDecimal.ZERO,
                60,
                true,
                false,
                false,
                ProfitHoldType.PERCENT_TRAILING,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                ProfitControlMode.NONE,
                ThresholdType.PERCENTAGE,
                BigDecimal.ZERO,
                TrailingType.PERCENTAGE,
                BigDecimal.ZERO
        );
        Strategy strategy = Strategy.fromConfig("dialog-config-strategy", "MSFT Strategy", config, StrategyMode.PAPER);

        StrategyService.StrategyCreationResult result = service.createAndActivate(strategy);

        assertTrue(result.success());
        assertEquals(1, alpaca.submittedOrders.size());
        AlpacaOrderData submitted = alpaca.submittedOrders.getFirst();
        assertEquals("MSFT", submitted.symbol());
        assertEquals("buy", submitted.side());
        assertEquals("limit", submitted.type());
        assertEquals(new BigDecimal("412.34"), submitted.limitPrice());
        assertTrue(submitted.rawJson().contains("\"qty\":\"7\""));
    }

    @Test
    void initialBaseBuyCanBePlacedWhenMarketIsClosed() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        StrategyService service = service(strategies, orders, events, alpaca, new AlwaysClosedMarketHoursService());

        Strategy strategy = baseStrategy("ASML", 10, new BigDecimal("1366.10"));
        strategy.setMaxCapitalAllowed(new BigDecimal("20000.00"));
        strategy.setMaxTotalQuantity(100);
        StrategyService.StrategyCreationResult result = service.createAndActivate(strategy);

        assertTrue(result.success());
        assertEquals(1, alpaca.submittedOrders.size());
        assertTrue(orders.findLatestByStrategyStage(strategy.id(), StrategyStage.BASE_BUY).isPresent());
    }

    @Test
    void liveModeBlockedWhenFlagDisabled() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        StrategyService service = service(strategies, orders, events, new FakeAlpacaClient());

        Strategy live = baseStrategy("AAPL", 1, new BigDecimal("10.00"));
        live.setMode(StrategyMode.LIVE);

        StrategyService.StrategyCreationResult result = service.createAndActivate(live);
        assertFalse(result.success());
        assertTrue(result.error().contains("LIVE mode is disabled"));
    }

    @Test
    void closePositionPlacesManualExitOrder() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        alpaca.position = Optional.of(new AlpacaPositionData("AAPL", new BigDecimal("7"), new BigDecimal("8.00"), new BigDecimal("9.50"), "{}"));
        alpaca.latestPrice = new BigDecimal("9.50");
        StrategyService service = service(strategies, orders, events, alpaca);

        Strategy strategy = baseStrategy("AAPL", 10, new BigDecimal("8.00"));
        strategies.save(strategy);
        strategy.setStatus(StrategyStatus.ACTIVE);

        StrategyService.StrategyCreationResult result = service.closePosition(strategy.id());

        assertTrue(result.success());
        assertTrue(orders.findLatestByStrategyStage(strategy.id(), StrategyStage.MANUAL_EXIT).isPresent());
    }

    @Test
    void closePositionWithMarketTypeSubmitsMarketSellOrder() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        alpaca.position = Optional.of(new AlpacaPositionData("AAPL", new BigDecimal("7"), new BigDecimal("8.00"), new BigDecimal("9.50"), "{}"));
        StrategyService service = service(strategies, orders, events, alpaca);

        Strategy strategy = baseStrategy("AAPL", 10, new BigDecimal("8.00"));
        strategies.save(strategy);

        StrategyService.StrategyCreationResult result = service.closePosition(strategy.id(), SellSubmissionType.MARKET);

        assertTrue(result.success());
        assertEquals(1, alpaca.submittedOrders.size());
        AlpacaOrderData submitted = alpaca.submittedOrders.getFirst();
        assertEquals("sell", submitted.side());
        assertEquals("market", submitted.type());
        StrategyOrder local = orders.findLatestByStrategyStage(strategy.id(), StrategyStage.MANUAL_EXIT).orElseThrow();
        assertEquals(StrategyOrderType.MARKET, local.orderType());
        assertEquals(0, local.limitPrice().compareTo(BigDecimal.ZERO));
    }

    @Test
    void closePositionFailsWhenStrategyDoesNotExist() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        StrategyService service = service(strategies, orders, events, new FakeAlpacaClient());

        StrategyService.StrategyCreationResult result = service.closePosition("missing-strategy-id");

        assertFalse(result.success());
        assertTrue(result.error().contains("Strategy not found"));
    }

    @Test
    void closePositionFallsBackToLatestPriceWhenPositionMarketPriceMissing() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        alpaca.position = Optional.of(new AlpacaPositionData("AAPL", new BigDecimal("3"), new BigDecimal("8.00"), BigDecimal.ZERO, "{}"));
        alpaca.latestPrice = new BigDecimal("9.25");
        StrategyService service = service(strategies, orders, events, alpaca);

        Strategy strategy = baseStrategy("AAPL", 10, new BigDecimal("8.00"));
        strategies.save(strategy);

        StrategyService.StrategyCreationResult result = service.closePosition(strategy.id());

        assertTrue(result.success());
        assertEquals(1, alpaca.submittedOrders.size());
        AlpacaOrderData submitted = alpaca.submittedOrders.getFirst();
        assertEquals(new BigDecimal("9.25"), submitted.limitPrice());
    }

    @Test
    void pauseCancelsAcceptedOpenOrdersInAlpaca() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        StrategyService service = service(strategies, orders, events, alpaca);

        Strategy strategy = baseStrategy("AAPL", 10, new BigDecimal("8.00"));
        strategy.setStatus(StrategyStatus.ACTIVE);
        strategies.save(strategy);
        StrategyOrder order = new StrategyOrder(
                UUID.randomUUID().toString(), strategy.id(), StrategyStage.BASE_BUY, "ord-1", "client-1", "AAPL",
                StrategyOrderSide.BUY, StrategyOrderType.LIMIT, new BigDecimal("8.00"), BigDecimal.ZERO,
                new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, StrategyOrderStatus.SUBMITTED,
                Instant.now(), Instant.now(), null, "{}"
        );
        orders.save(order);
        alpaca.openOrders.add(new AlpacaOrderData("ord-1", "client-1", "AAPL", "buy", "limit", new BigDecimal("8.00"), BigDecimal.ZERO, BigDecimal.ZERO, "accepted", "{\"qty\":\"10\"}"));

        service.pause(strategy.id());

        assertTrue(alpaca.canceledOrderIds.contains("ord-1"));
        Strategy paused = strategies.findById(strategy.id()).orElseThrow();
        assertEquals(StrategyStatus.PAUSED, paused.status());
        assertEquals(PauseReason.MANUAL_LIMIT_BUY_CANCELED, paused.pauseReason());
    }

    @Test
    void resumeResubmitsBaseBuyWhenPausedOrderWasCanceled() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        StrategyService service = service(strategies, orders, events, alpaca);

        Strategy strategy = baseStrategy("AAPL", 10, new BigDecimal("8.00"));
        strategy.setStatus(StrategyStatus.ACTIVE);
        strategies.save(strategy);
        StrategyOrder originalOrder = new StrategyOrder(
                UUID.randomUUID().toString(), strategy.id(), StrategyStage.BASE_BUY, "ord-1", "client-1", "AAPL",
                StrategyOrderSide.BUY, StrategyOrderType.LIMIT, new BigDecimal("8.00"), BigDecimal.ZERO,
                new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, StrategyOrderStatus.SUBMITTED,
                Instant.now(), Instant.now(), null, "{}"
        );
        orders.save(originalOrder);
        alpaca.openOrders.add(new AlpacaOrderData("ord-1", "client-1", "AAPL", "buy", "limit", new BigDecimal("8.00"), BigDecimal.ZERO, BigDecimal.ZERO, "accepted", "{\"qty\":\"10\"}"));

        service.pause(strategy.id());
        service.resume(strategy.id());

        assertTrue(alpaca.canceledOrderIds.contains("ord-1"));
        assertEquals(1, alpaca.submittedOrders.size());
        AlpacaOrderData resubmitted = alpaca.submittedOrders.getLast();
        assertEquals("AAPL", resubmitted.symbol());
        assertEquals("buy", resubmitted.side());
        assertEquals(StrategyStatus.ACTIVE, strategies.findById(strategy.id()).orElseThrow().status());
    }

    @Test
    void resumeRestoresLifecycleStateBeforePause() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        StrategyService service = service(strategies, orders, events, alpaca, new AlwaysClosedMarketHoursService());

        Strategy strategy = baseStrategy("TSLA", 10, new BigDecimal("350.00"));
        strategy.setStatus(StrategyStatus.PAUSED);
        strategy.setCurrentState(StrategyLifecycleState.PAUSED);
        strategy.setResumeStateBeforePause(StrategyLifecycleState.BUY_LIMIT_1_PLACED);
        strategy.setPauseReason(PauseReason.USER_PAUSED);
        strategies.save(strategy);

        service.resume(strategy.id());

        Strategy resumed = strategies.findById(strategy.id()).orElseThrow();
        assertEquals(StrategyStatus.ACTIVE, resumed.status());
        assertEquals(StrategyLifecycleState.BUY_LIMIT_1_PLACED, resumed.currentState());
        assertEquals(PauseReason.MANUAL_MARKET_CLOSED_OVERRIDE, resumed.pauseReason());
    }

    @Test
    void manualResumeDuringMarketCloseIsNotOverwrittenByAutoPause() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        StrategyService service = service(strategies, orders, events, alpaca, new AlwaysClosedMarketHoursService());

        Strategy strategy = baseStrategy("TSLA", 10, new BigDecimal("350.00"));
        strategy.setStatus(StrategyStatus.PAUSED);
        strategy.setCurrentState(StrategyLifecycleState.PAUSED);
        strategy.setResumeStateBeforePause(StrategyLifecycleState.BUY_LIMIT_1_PLACED);
        strategy.setPauseReason(PauseReason.AUTO_MARKET_CLOSED);
        strategies.save(strategy);

        service.resume(strategy.id());
        service.autoPauseForMarketClose(strategy.id(), "Strategy auto-paused because market is closed");

        Strategy resumed = strategies.findById(strategy.id()).orElseThrow();
        assertEquals(StrategyStatus.ACTIVE, resumed.status());
        assertEquals(StrategyLifecycleState.BUY_LIMIT_1_PLACED, resumed.currentState());
        assertEquals(PauseReason.MANUAL_MARKET_CLOSED_OVERRIDE, resumed.pauseReason());
        assertTrue(alpaca.submittedOrders.isEmpty());
    }

    @Test
    void autoResumeFromMarketCloseSkipsManuallyCanceledStrategies() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        StrategyService service = service(strategies, orders, events, alpaca, new AlwaysOpenMarketHoursService());

        Strategy strategy = baseStrategy("TSLA", 10, new BigDecimal("350.00"));
        strategy.setStatus(StrategyStatus.PAUSED);
        strategy.setCurrentState(StrategyLifecycleState.PAUSED);
        strategy.setPauseReason(PauseReason.MANUAL_LIMIT_BUY_CANCELED);
        strategies.save(strategy);

        service.autoResumeFromMarketClose(strategy.id(), "Strategy auto-resumed because market is open");

        Strategy after = strategies.findById(strategy.id()).orElseThrow();
        assertEquals(StrategyStatus.PAUSED, after.status());
        assertEquals(PauseReason.MANUAL_LIMIT_BUY_CANCELED, after.pauseReason());
        assertTrue(alpaca.submittedOrders.isEmpty());
    }

    @Test
    void staleRestartFailureIsRecoveredAfterProfitableExit() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        alpaca.position = Optional.empty();
        StrategyService service = service(strategies, orders, events, alpaca);

        Strategy strategy = baseStrategy("TSLA", 10, new BigDecimal("373.00"));
        strategy.setStatus(StrategyStatus.FAILED);
        strategy.setCurrentState(StrategyLifecycleState.FAILED);
        strategy.setLastError("Projected quantity exceeds maxTotalQuantity");
        strategy.setRestartAfterExitEnabled(true);
        strategies.save(strategy);

        orders.save(new StrategyOrder(
                UUID.randomUUID().toString(), strategy.id(), StrategyStage.BASE_BUY, "base", "client-base", "TSLA",
                StrategyOrderSide.BUY, StrategyOrderType.LIMIT, new BigDecimal("373.00"), BigDecimal.ZERO,
                new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("372.85"), StrategyOrderStatus.FILLED,
                Instant.now(), Instant.now(), Instant.now(), "{}"
        ));
        orders.save(new StrategyOrder(
                UUID.randomUUID().toString(), strategy.id(), StrategyStage.TARGET_SELL, "sell", "client-sell", "TSLA",
                StrategyOrderSide.SELL, StrategyOrderType.LIMIT, new BigDecimal("392.17"), BigDecimal.ZERO,
                new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("392.36"), StrategyOrderStatus.FILLED,
                Instant.now(), Instant.now(), Instant.now(), "{}"
        ));
        orders.save(new StrategyOrder(
                UUID.randomUUID().toString(), strategy.id(), StrategyStage.BUY_LIMIT_1, "stale-buy", "client-stale", "TSLA",
                StrategyOrderSide.BUY, StrategyOrderType.LIMIT, new BigDecimal("370.00"), BigDecimal.ZERO,
                new BigDecimal("5"), BigDecimal.ZERO, BigDecimal.ZERO, StrategyOrderStatus.SUBMITTED,
                Instant.now(), Instant.now(), null, "{}"
        ));
        alpaca.openOrders.add(new AlpacaOrderData("stale-buy", "client-stale", "TSLA", "buy", "limit",
                new BigDecimal("370.00"), BigDecimal.ZERO, BigDecimal.ZERO, "new", "{\"qty\":\"5\"}"));

        Strategy recovered = service.recoverStaleRestartFailure(strategy.id()).orElseThrow();

        assertEquals(StrategyStatus.ACTIVE, recovered.status());
        assertEquals(StrategyLifecycleState.CREATED, recovered.currentState());
        assertTrue(recovered.lastError() == null || recovered.lastError().isBlank());
        assertTrue(alpaca.canceledOrderIds.contains("stale-buy"));
        assertEquals(StrategyOrderStatus.CANCELED, orders.findByAlpacaOrderId("stale-buy").orElseThrow().status());
    }

    @Test
    void updateActiveStrategyCancelsOpenOrdersAndRecreatesWithUpdatedConfig() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        StrategyService service = service(strategies, orders, events, alpaca);

        Strategy strategy = baseStrategy("AAPL", 10, new BigDecimal("8.00"));
        strategy.setStatus(StrategyStatus.ACTIVE);
        strategy.setCurrentState(StrategyLifecycleState.BASE_BUY_PLACED);
        strategies.save(strategy);

        StrategyOrder pending = new StrategyOrder(
                UUID.randomUUID().toString(), strategy.id(), StrategyStage.BASE_BUY, "ord-1", "client-1", "AAPL",
                StrategyOrderSide.BUY, StrategyOrderType.LIMIT, new BigDecimal("8.00"), BigDecimal.ZERO,
                new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, StrategyOrderStatus.SUBMITTED,
                Instant.now(), Instant.now(), null, "{}"
        );
        orders.save(pending);
        alpaca.openOrders.add(new AlpacaOrderData("ord-1", "client-1", "AAPL", "buy", "limit", new BigDecimal("8.00"), BigDecimal.ZERO, BigDecimal.ZERO, "accepted", "{\"qty\":\"10\"}"));

        Strategy updated = strategyWithId(strategy.id(), "AAPL", 10, new BigDecimal("7.50"));
        updated.setStatus(StrategyStatus.ACTIVE);
        updated.setCurrentState(strategy.currentState());

        Optional<Strategy> result = service.updateStrategy(updated);

        assertTrue(result.isPresent());
        assertTrue(alpaca.canceledOrderIds.contains("ord-1"));
        assertEquals(1, alpaca.submittedOrders.size());
        AlpacaOrderData recreated = alpaca.submittedOrders.getFirst();
        assertEquals(new BigDecimal("7.50"), recreated.limitPrice());
        assertTrue(recreated.rawJson().contains("\"10\""));

        StrategyOrder canceledLocal = orders.findByClientOrderId("client-1").orElseThrow();
        assertEquals(StrategyOrderStatus.CANCELED, canceledLocal.status());
    }

    @Test
    void updateActiveStrategyDuringMarketCloseKeepsManualMarketClosedOverride() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        StrategyService service = service(strategies, orders, events, alpaca, new AlwaysClosedMarketHoursService());

        Strategy strategy = baseStrategy("AAPL", 10, new BigDecimal("8.00"));
        strategy.setStatus(StrategyStatus.ACTIVE);
        strategy.setCurrentState(StrategyLifecycleState.BASE_BUY_PLACED);
        strategy.setPauseReason(PauseReason.NONE);
        strategies.save(strategy);

        Strategy updated = strategyWithId(strategy.id(), "AAPL", 10, new BigDecimal("7.50"));
        updated.setStatus(StrategyStatus.ACTIVE);
        updated.setCurrentState(StrategyLifecycleState.BASE_BUY_PLACED);

        Optional<Strategy> result = service.updateStrategy(updated);

        assertTrue(result.isPresent());
        Strategy persisted = strategies.findById(strategy.id()).orElseThrow();
        assertEquals(StrategyStatus.ACTIVE, persisted.status());
        assertEquals(StrategyLifecycleState.BASE_BUY_PLACED, persisted.currentState());
        assertEquals(PauseReason.NONE, persisted.pauseReason());
        assertEquals(1, alpaca.submittedOrders.size());
    }

    @Test
    void updateActiveStrategyQueuesForOpenWhenBrokerRejectsClosedSession() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        alpaca.rejectBuyOrdersWithSessionMessage = true;
        StrategyService service = service(strategies, orders, events, alpaca, new AlwaysClosedMarketHoursService());

        Strategy strategy = baseStrategy("AAPL", 10, new BigDecimal("8.00"));
        strategy.setStatus(StrategyStatus.ACTIVE);
        strategy.setCurrentState(StrategyLifecycleState.BASE_BUY_PLACED);
        strategies.save(strategy);

        Strategy updated = strategyWithId(strategy.id(), "AAPL", 10, new BigDecimal("7.50"));
        updated.setStatus(StrategyStatus.ACTIVE);
        updated.setCurrentState(StrategyLifecycleState.BASE_BUY_PLACED);

        Optional<Strategy> result = service.updateStrategy(updated);

        assertTrue(result.isPresent());
        Strategy persisted = strategies.findById(strategy.id()).orElseThrow();
        assertEquals(StrategyStatus.ACTIVE, persisted.status());
        assertEquals(StrategyLifecycleState.QUEUED_FOR_OPEN, persisted.currentState());
        assertTrue(persisted.lastError() == null || persisted.lastError().isBlank());
    }

    @Test
    void updateExpiredClosedStrategyStartsNewBaseBuyCycle() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        StrategyService service = service(strategies, orders, events, alpaca);

        Strategy strategy = baseStrategy("AAPL", 10, new BigDecimal("8.00"));
        strategy.setStatus(StrategyStatus.FAILED);
        strategy.setCurrentState(StrategyLifecycleState.FAILED);
        strategy.setLatestOrderStatus("expired");
        strategy.setLatestAlpacaOrderId("expired-order");
        strategy.setLossBuyLevelsEnabled(false);
        strategy.setMaxCapitalAllowed(new BigDecimal("100.00"));
        strategies.save(strategy);
        StrategyOrder staleLocalPending = new StrategyOrder(
                UUID.randomUUID().toString(), strategy.id(), StrategyStage.BASE_BUY, "expired-order", "expired-client", "AAPL",
                StrategyOrderSide.BUY, StrategyOrderType.LIMIT, new BigDecimal("8.00"), BigDecimal.ZERO,
                new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, StrategyOrderStatus.SUBMITTED,
                Instant.now(), Instant.now(), null, "{}"
        );
        orders.save(staleLocalPending);

        Strategy updated = strategyWithId(strategy.id(), "AAPL", 10, new BigDecimal("7.50"));
        updated.setStatus(StrategyStatus.FAILED);
        updated.setCurrentState(StrategyLifecycleState.FAILED);
        updated.setLatestOrderStatus("expired");
        updated.setLatestAlpacaOrderId("expired-order");
        updated.setLossBuyLevelsEnabled(false);
        updated.setMaxCapitalAllowed(new BigDecimal("100.00"));

        Optional<Strategy> result = service.updateStrategy(updated);

        assertTrue(result.isPresent());
        Strategy persisted = strategies.findById(strategy.id()).orElseThrow();
        assertEquals(StrategyStatus.ACTIVE, persisted.status());
        assertEquals(StrategyLifecycleState.BASE_BUY_PLACED, persisted.currentState());
        assertEquals("new", persisted.latestOrderStatus());
        assertEquals(1, alpaca.submittedOrders.size());
        AlpacaOrderData submitted = alpaca.submittedOrders.getFirst();
        assertEquals("buy", submitted.side());
        assertEquals(new BigDecimal("7.50"), submitted.limitPrice());
        assertTrue(strategies.findActive().stream().anyMatch(active -> active.id().equals(strategy.id())));
        StrategyOrder localOrder = orders.findLatestByStrategyStage(strategy.id(), StrategyStage.BASE_BUY).orElseThrow();
        assertEquals(StrategyOrderStatus.SUBMITTED, localOrder.status());
        assertEquals(StrategyOrderStatus.CANCELED, orders.findByClientOrderId("expired-client").orElseThrow().status());
    }

    @Test
    void updatePositionClosedCanceledStrategyStartsNewBaseBuyCycle() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        StrategyService service = service(strategies, orders, events, alpaca);

        Strategy strategy = baseStrategy("AAPL", 10, new BigDecimal("8.00"));
        strategy.setStatus(StrategyStatus.FAILED);
        strategy.setCurrentState(StrategyLifecycleState.FAILED);
        strategy.setLatestOrderStatus("canceled");
        strategies.save(strategy);

        Strategy updated = strategyWithId(strategy.id(), "AAPL", 10, new BigDecimal("7.50"));
        updated.setStatus(StrategyStatus.FAILED);
        updated.setCurrentState(StrategyLifecycleState.FAILED);
        updated.setLatestOrderStatus("canceled");

        Optional<Strategy> result = service.updateStrategy(updated);

        assertTrue(result.isPresent());
        Strategy persisted = strategies.findById(strategy.id()).orElseThrow();
        assertEquals(StrategyStatus.ACTIVE, persisted.status());
        assertEquals(StrategyLifecycleState.BASE_BUY_PLACED, persisted.currentState());
        assertEquals("new", persisted.latestOrderStatus());
        assertEquals(1, alpaca.submittedOrders.size());
    }

    @Test
    void repositionExpiredStrategyStartsNewBaseBuyCycle() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        StrategyService service = service(strategies, orders, events, alpaca);

        Strategy strategy = baseStrategy("AAPL", 10, new BigDecimal("8.00"));
        strategy.setStatus(StrategyStatus.FAILED);
        strategy.setCurrentState(StrategyLifecycleState.FAILED);
        strategy.setLatestOrderStatus("expired");
        strategy.setLatestAlpacaOrderId("expired-order");
        strategies.save(strategy);

        StrategyService.StrategyCreationResult result = service.repositionExpiredStrategy(strategy.id());

        assertTrue(result.success());
        Strategy persisted = strategies.findById(strategy.id()).orElseThrow();
        assertEquals(StrategyStatus.ACTIVE, persisted.status());
        assertEquals(StrategyLifecycleState.BASE_BUY_PLACED, persisted.currentState());
        assertEquals("new", persisted.latestOrderStatus());
        assertEquals(1, alpaca.submittedOrders.size());
    }

    @Test
    void repositionExpiredStrategyFailsWhenStrategyIsNotExpired() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        StrategyService service = service(strategies, orders, events, alpaca);

        Strategy strategy = baseStrategy("AAPL", 10, new BigDecimal("8.00"));
        strategy.setStatus(StrategyStatus.FAILED);
        strategy.setCurrentState(StrategyLifecycleState.FAILED);
        strategy.setLatestOrderStatus("rejected");
        strategies.save(strategy);

        StrategyService.StrategyCreationResult result = service.repositionExpiredStrategy(strategy.id());

        assertFalse(result.success());
        assertTrue(result.error().contains("not in an expired state"));
        assertTrue(alpaca.submittedOrders.isEmpty());
    }

    @Test
    void updateFailedPositionClosedStrategyStartsNewBaseBuyCycleEvenWithoutBrokerStatus() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        StrategyService service = service(strategies, orders, events, alpaca);

        Strategy strategy = baseStrategy("AAPL", 10, new BigDecimal("8.00"));
        strategy.setStatus(StrategyStatus.FAILED);
        strategy.setCurrentState(StrategyLifecycleState.FAILED);
        strategy.setLatestOrderStatus("");
        strategies.save(strategy);

        Strategy updated = strategyWithId(strategy.id(), "AAPL", 10, new BigDecimal("7.50"));
        updated.setStatus(StrategyStatus.FAILED);
        updated.setCurrentState(StrategyLifecycleState.FAILED);
        updated.setLatestOrderStatus("");

        Optional<Strategy> result = service.updateStrategy(updated);

        assertTrue(result.isPresent());
        Strategy persisted = strategies.findById(strategy.id()).orElseThrow();
        assertEquals(StrategyStatus.ACTIVE, persisted.status());
        assertEquals(StrategyLifecycleState.BASE_BUY_PLACED, persisted.currentState());
        assertEquals("new", persisted.latestOrderStatus());
        assertEquals(1, alpaca.submittedOrders.size());
        assertEquals(new BigDecimal("7.50"), alpaca.submittedOrders.getFirst().limitPrice());
    }

    @Test
    void updateFailedRejectedPositionClosedStrategyStartsNewBaseBuyCycle() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        StrategyService service = service(strategies, orders, events, alpaca);

        Strategy strategy = baseStrategy("AAPL", 10, new BigDecimal("8.00"));
        strategy.setStatus(StrategyStatus.FAILED);
        strategy.setCurrentState(StrategyLifecycleState.FAILED);
        strategy.setLatestOrderStatus("rejected");
        strategies.save(strategy);

        Strategy updated = strategyWithId(strategy.id(), "AAPL", 10, new BigDecimal("7.50"));
        updated.setStatus(StrategyStatus.FAILED);
        updated.setCurrentState(StrategyLifecycleState.FAILED);
        updated.setLatestOrderStatus("rejected");

        Optional<Strategy> result = service.updateStrategy(updated);

        assertTrue(result.isPresent());
        Strategy persisted = strategies.findById(strategy.id()).orElseThrow();
        assertEquals(StrategyStatus.ACTIVE, persisted.status());
        assertEquals(StrategyLifecycleState.BASE_BUY_PLACED, persisted.currentState());
        assertEquals(1, alpaca.submittedOrders.size());
    }

    @Test
    void updateManuallyCanceledStrategyStartsNewBaseBuyCycle() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        StrategyService service = service(strategies, orders, events, alpaca);

        Strategy strategy = baseStrategy("AAPL", 10, new BigDecimal("8.00"));
        strategy.setStatus(StrategyStatus.PAUSED);
        strategy.setCurrentState(StrategyLifecycleState.PAUSED);
        strategy.setPauseReason(PauseReason.MANUAL_LIMIT_BUY_CANCELED);
        strategies.save(strategy);
        StrategyOrder staleLocalPending = new StrategyOrder(
                UUID.randomUUID().toString(), strategy.id(), StrategyStage.BASE_BUY, "canceled-order", "canceled-client", "AAPL",
                StrategyOrderSide.BUY, StrategyOrderType.LIMIT, new BigDecimal("8.00"), BigDecimal.ZERO,
                new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, StrategyOrderStatus.SUBMITTED,
                Instant.now(), Instant.now(), null, "{}"
        );
        orders.save(staleLocalPending);

        Strategy updated = strategyWithId(strategy.id(), "AAPL", 10, new BigDecimal("7.50"));
        updated.setStatus(StrategyStatus.PAUSED);
        updated.setCurrentState(StrategyLifecycleState.PAUSED);
        updated.setPauseReason(PauseReason.MANUAL_LIMIT_BUY_CANCELED);

        Optional<Strategy> result = service.updateStrategy(updated);

        assertTrue(result.isPresent());
        Strategy persisted = strategies.findById(strategy.id()).orElseThrow();
        assertEquals(StrategyStatus.ACTIVE, persisted.status());
        assertEquals(StrategyLifecycleState.BASE_BUY_PLACED, persisted.currentState());
        assertEquals(PauseReason.NONE, persisted.pauseReason());
        assertEquals("new", persisted.latestOrderStatus());
        assertEquals(1, alpaca.submittedOrders.size());
        assertEquals(new BigDecimal("7.50"), alpaca.submittedOrders.getFirst().limitPrice());
        assertTrue(strategies.findActive().stream().anyMatch(active -> active.id().equals(strategy.id())));
        assertEquals(StrategyOrderStatus.CANCELED, orders.findByClientOrderId("canceled-client").orElseThrow().status());
    }

    @Test
    void updatePausedStrategyDoesNotCancelOrRecreateOrders() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        StrategyService service = service(strategies, orders, events, alpaca);

        Strategy strategy = baseStrategy("AAPL", 10, new BigDecimal("8.00"));
        strategy.setStatus(StrategyStatus.PAUSED);
        strategy.setCurrentState(StrategyLifecycleState.PAUSED);
        strategies.save(strategy);

        Strategy updated = strategyWithId(strategy.id(), "AAPL", 10, new BigDecimal("7.50"));
        updated.setStatus(StrategyStatus.PAUSED);
        updated.setCurrentState(strategy.currentState());

        Optional<Strategy> result = service.updateStrategy(updated);

        assertTrue(result.isPresent());
        assertTrue(alpaca.canceledOrderIds.isEmpty());
        assertTrue(alpaca.submittedOrders.isEmpty());
    }

    @Test
    void syncRemoteStrategiesImportsMissingAlpacaSymbols() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        alpaca.allPositions = List.of(new AlpacaPositionData("NIO", new BigDecimal("5"), new BigDecimal("6.20"), new BigDecimal("6.30"), "{}"));
        alpaca.openOrders.add(new AlpacaOrderData("ord-remote", "client-remote", "NIO", "buy", "limit", new BigDecimal("6.21"), BigDecimal.ZERO, BigDecimal.ZERO, "accepted", "{\"qty\":\"5\"}"));
        StrategyService service = service(strategies, orders, events, alpaca);

        List<Strategy> synced = service.syncRemoteStrategies();

        assertEquals(1, synced.size());
        Strategy syncedStrategy = synced.getFirst();
        assertEquals("NIO", syncedStrategy.symbol());
        assertEquals(StrategyStatus.ACTIVE, syncedStrategy.status());
        assertTrue(orders.findByStrategyId(syncedStrategy.id()).stream().anyMatch(order -> "ord-remote".equals(order.alpacaOrderId())));
    }

    @Test
    void cancelPendingLimitBuysCancelsOnlyPendingLimitBuyOrdersAndPausesStrategy() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        StrategyService service = service(strategies, orders, events, alpaca);

        Strategy strategy = baseStrategy("AAPL", 10, new BigDecimal("180.00"));
        strategy.setStatus(StrategyStatus.ACTIVE);
        strategy.setCurrentState(StrategyLifecycleState.BASE_BUY_PLACED);
        strategies.save(strategy);
        StrategyOrder buyOrder = new StrategyOrder(
                UUID.randomUUID().toString(), strategy.id(), StrategyStage.BASE_BUY, "ord-buy", "client-buy", "AAPL",
                StrategyOrderSide.BUY, StrategyOrderType.LIMIT, new BigDecimal("180.00"), BigDecimal.ZERO,
                new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, StrategyOrderStatus.SUBMITTED,
                Instant.now(), Instant.now(), null, "{}"
        );
        StrategyOrder sellOrder = new StrategyOrder(
                UUID.randomUUID().toString(), strategy.id(), StrategyStage.TARGET_SELL, "ord-sell", "client-sell", "AAPL",
                StrategyOrderSide.SELL, StrategyOrderType.LIMIT, new BigDecimal("190.00"), BigDecimal.ZERO,
                new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, StrategyOrderStatus.SUBMITTED,
                Instant.now(), Instant.now(), null, "{}"
        );
        orders.save(buyOrder);
        orders.save(sellOrder);
        alpaca.openOrders.add(new AlpacaOrderData("ord-buy", "client-buy", "AAPL", "buy", "limit", new BigDecimal("180.00"), BigDecimal.ZERO, BigDecimal.ZERO, "new", "{}"));
        alpaca.openOrders.add(new AlpacaOrderData("ord-sell", "client-sell", "AAPL", "sell", "limit", new BigDecimal("190.00"), BigDecimal.ZERO, BigDecimal.ZERO, "new", "{}"));

        StrategyService.LimitBuyCancelResult result = service.cancelPendingLimitBuys(strategy.id());

        assertTrue(result.success());
        assertEquals(1, result.canceledCount());
        assertEquals(List.of("ord-buy"), alpaca.canceledOrderIds);
        assertEquals(StrategyOrderStatus.CANCELED, orders.findByAlpacaOrderId("ord-buy").orElseThrow().status());
        assertEquals(StrategyOrderStatus.SUBMITTED, orders.findByAlpacaOrderId("ord-sell").orElseThrow().status());
        Strategy paused = strategies.findById(strategy.id()).orElseThrow();
        assertEquals(StrategyStatus.PAUSED, paused.status());
        assertEquals(StrategyLifecycleState.PAUSED, paused.currentState());
        assertEquals(PauseReason.MANUAL_LIMIT_BUY_CANCELED, paused.pauseReason());
    }

    @Test
    void cancelPendingLimitBuysReportsFailureWhenNoLimitBuyIsPending() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        StrategyService service = service(strategies, orders, events, alpaca);

        Strategy strategy = baseStrategy("AAPL", 10, new BigDecimal("180.00"));
        strategy.setStatus(StrategyStatus.ACTIVE);
        strategies.save(strategy);
        orders.save(new StrategyOrder(
                UUID.randomUUID().toString(), strategy.id(), StrategyStage.BASE_BUY, "ord-filled", "client-filled", "AAPL",
                StrategyOrderSide.BUY, StrategyOrderType.LIMIT, new BigDecimal("180.00"), BigDecimal.ZERO,
                new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("180.00"), StrategyOrderStatus.FILLED,
                Instant.now(), Instant.now(), Instant.now(), "{}"
        ));

        StrategyService.LimitBuyCancelResult result = service.cancelPendingLimitBuys(strategy.id());

        assertFalse(result.success());
        assertTrue(result.error().contains("No pending limit buy orders"));
        assertTrue(alpaca.canceledOrderIds.isEmpty());
        assertEquals(StrategyStatus.ACTIVE, strategies.findById(strategy.id()).orElseThrow().status());
    }

    @Test
    void cancelPendingLimitSellsCancelsOnlyPendingLimitSellOrdersAndRestoresWaitingState() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        StrategyService service = service(strategies, orders, events, alpaca);

        Strategy strategy = baseStrategy("AAPL", 10, new BigDecimal("180.00"));
        strategy.setStatus(StrategyStatus.ACTIVE);
        strategy.setCurrentState(StrategyLifecycleState.SELL_PLACED);
        strategies.save(strategy);
        StrategyOrder buyOrder = new StrategyOrder(
                UUID.randomUUID().toString(), strategy.id(), StrategyStage.BASE_BUY, "ord-buy", "client-buy", "AAPL",
                StrategyOrderSide.BUY, StrategyOrderType.LIMIT, new BigDecimal("175.00"), BigDecimal.ZERO,
                new BigDecimal("5"), BigDecimal.ZERO, BigDecimal.ZERO, StrategyOrderStatus.SUBMITTED,
                Instant.now(), Instant.now(), null, "{}"
        );
        StrategyOrder sellOrder = new StrategyOrder(
                UUID.randomUUID().toString(), strategy.id(), StrategyStage.TARGET_SELL, "ord-sell", "client-sell", "AAPL",
                StrategyOrderSide.SELL, StrategyOrderType.LIMIT, new BigDecimal("190.00"), BigDecimal.ZERO,
                new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, StrategyOrderStatus.SUBMITTED,
                Instant.now(), Instant.now(), null, "{}"
        );
        orders.save(buyOrder);
        orders.save(sellOrder);
        alpaca.openOrders.add(new AlpacaOrderData("ord-buy", "client-buy", "AAPL", "buy", "limit", new BigDecimal("175.00"), BigDecimal.ZERO, BigDecimal.ZERO, "new", "{}"));
        alpaca.openOrders.add(new AlpacaOrderData("ord-sell", "client-sell", "AAPL", "sell", "limit", new BigDecimal("190.00"), BigDecimal.ZERO, BigDecimal.ZERO, "new", "{}"));

        StrategyService.LimitSellCancelResult result = service.cancelPendingLimitSells(strategy.id());

        assertTrue(result.success());
        assertEquals(1, result.canceledCount());
        assertEquals(List.of("ord-sell"), alpaca.canceledOrderIds);
        assertEquals(StrategyOrderStatus.SUBMITTED, orders.findByAlpacaOrderId("ord-buy").orElseThrow().status());
        assertEquals(StrategyOrderStatus.CANCELED, orders.findByAlpacaOrderId("ord-sell").orElseThrow().status());
        Strategy updated = strategies.findById(strategy.id()).orElseThrow();
        assertEquals(StrategyStatus.ACTIVE, updated.status());
        assertEquals(StrategyLifecycleState.BASE_BUY_FILLED, updated.currentState());
        assertEquals("canceled", updated.latestOrderStatus());
    }

    @Test
    void cancelPendingLimitSellsReportsFailureWhenNoLimitSellIsPending() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        StrategyService service = service(strategies, orders, events, alpaca);

        Strategy strategy = baseStrategy("AAPL", 10, new BigDecimal("180.00"));
        strategy.setStatus(StrategyStatus.ACTIVE);
        strategy.setCurrentState(StrategyLifecycleState.SELL_PLACED);
        strategies.save(strategy);
        orders.save(new StrategyOrder(
                UUID.randomUUID().toString(), strategy.id(), StrategyStage.TARGET_SELL, "ord-filled", "client-filled", "AAPL",
                StrategyOrderSide.SELL, StrategyOrderType.LIMIT, new BigDecimal("190.00"), BigDecimal.ZERO,
                new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("190.00"), StrategyOrderStatus.FILLED,
                Instant.now(), Instant.now(), Instant.now(), "{}"
        ));

        StrategyService.LimitSellCancelResult result = service.cancelPendingLimitSells(strategy.id());

        assertFalse(result.success());
        assertTrue(result.error().contains("No pending limit sell orders"));
        assertTrue(alpaca.canceledOrderIds.isEmpty());
        assertEquals(StrategyLifecycleState.SELL_PLACED, strategies.findById(strategy.id()).orElseThrow().currentState());
    }

    @Test
    void promotePaperStrategyToLiveClonesAndArchivesPaperStrategy() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        StrategyService service = service(
                strategies, orders, events, alpaca,
                new AlwaysOpenMarketHoursService(),
                true,
                StrategyMode.LIVE,
                ApplicationMode.LIVE
        );

        Strategy paper = baseStrategy("TSLA", 10, new BigDecimal("350.00"));
        paper.setStatus(StrategyStatus.ACTIVE);
        paper.setCurrentState(StrategyLifecycleState.VALIDATED);
        paper.setMaxCapitalAllowed(new BigDecimal("10000.00"));
        strategies.save(paper);

        StrategyService.LivePromotionResult result = service.promotePaperStrategyToLive(paper.id());

        assertTrue(result.success());
        Strategy archivedPaper = strategies.findById(paper.id()).orElseThrow();
        assertEquals(StrategyStatus.ARCHIVED, archivedPaper.status());
        assertEquals(StrategyLifecycleState.STOPPED, archivedPaper.currentState());

        Strategy live = strategies.findById(result.liveStrategyId()).orElseThrow();
        assertEquals(StrategyMode.LIVE, live.mode());
        assertEquals(StrategyStatus.ACTIVE, live.status());
        assertEquals("TSLA", live.symbol());
        assertEquals(paper.baseBuyLimitPrice(), live.baseBuyLimitPrice());
        assertEquals(1, alpaca.submittedOrders.size());
    }

    @Test
    void promotePaperStrategyToLiveBlocksDuplicateLiveSymbol() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        StrategyService service = service(
                strategies, orders, events, alpaca,
                new AlwaysOpenMarketHoursService(),
                true,
                StrategyMode.LIVE,
                ApplicationMode.LIVE
        );

        Strategy paper = baseStrategy("TSLA", 10, new BigDecimal("350.00"));
        strategies.save(paper);
        Strategy live = strategyWithId(UUID.randomUUID().toString(), "TSLA", 10, new BigDecimal("352.00"));
        live.setMode(StrategyMode.LIVE);
        live.setStatus(StrategyStatus.ACTIVE);
        strategies.save(live);

        StrategyService.LivePromotionResult result = service.promotePaperStrategyToLive(paper.id());

        assertFalse(result.success());
        assertTrue(result.error().contains("already exists"));
        assertEquals(0, alpaca.submittedOrders.size());
        assertEquals(StrategyStatus.CREATED, strategies.findById(paper.id()).orElseThrow().status());
    }

    @Test
    void promotePaperStrategyToLiveBlocksPendingPaperOrders() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        StrategyService service = service(
                strategies, orders, events, alpaca,
                new AlwaysOpenMarketHoursService(),
                true,
                StrategyMode.LIVE,
                ApplicationMode.LIVE
        );

        Strategy paper = baseStrategy("AAPL", 10, new BigDecimal("180.00"));
        paper.setMaxCapitalAllowed(new BigDecimal("10000.00"));
        strategies.save(paper);
        orders.save(new StrategyOrder(
                UUID.randomUUID().toString(), paper.id(), StrategyStage.BASE_BUY, "ord-paper", "client-paper", "AAPL",
                StrategyOrderSide.BUY, StrategyOrderType.LIMIT, new BigDecimal("180.00"), BigDecimal.ZERO,
                new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, StrategyOrderStatus.SUBMITTED,
                Instant.now(), Instant.now(), null, "{}"
        ));

        StrategyService.LivePromotionPreview preview = service.previewLivePromotion(paper.id());
        StrategyService.LivePromotionResult result = service.promotePaperStrategyToLive(paper.id());

        assertFalse(preview.eligible());
        assertEquals(1, preview.pendingPaperOrders());
        assertFalse(result.success());
        assertTrue(result.error().contains("pending local order"));
    }

    @Test
    void promotePaperStrategyToLiveBlocksExistingLivePosition() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        alpaca.position = Optional.of(new AlpacaPositionData("MSFT", new BigDecimal("5"), new BigDecimal("420.00"), new BigDecimal("425.00"), "{}"));
        StrategyService service = service(
                strategies, orders, events, alpaca,
                new AlwaysOpenMarketHoursService(),
                true,
                StrategyMode.LIVE,
                ApplicationMode.LIVE
        );

        Strategy paper = baseStrategy("MSFT", 10, new BigDecimal("410.00"));
        paper.setMaxCapitalAllowed(new BigDecimal("10000.00"));
        strategies.save(paper);

        StrategyService.LivePromotionPreview preview = service.previewLivePromotion(paper.id());
        StrategyService.LivePromotionResult result = service.promotePaperStrategyToLive(paper.id());

        assertFalse(preview.eligible());
        assertEquals(0, preview.livePositionQuantity().compareTo(new BigDecimal("5")));
        assertFalse(result.success());
        assertTrue(result.error().contains("open position"));
    }

    private StrategyService service(
            InMemoryStrategyRepository strategies,
            InMemoryOrderRepository orders,
            InMemoryEventRepository events,
            FakeAlpacaClient alpaca
    ) {
        return service(strategies, orders, events, alpaca, new AlwaysOpenMarketHoursService());
    }

    private StrategyService service(
            InMemoryStrategyRepository strategies,
            InMemoryOrderRepository orders,
            InMemoryEventRepository events,
            FakeAlpacaClient alpaca,
            MarketHoursService marketHoursService
    ) {
        return service(strategies, orders, events, alpaca, marketHoursService, false, StrategyMode.PAPER, ApplicationMode.PAPER);
    }

    private StrategyService service(
            InMemoryStrategyRepository strategies,
            InMemoryOrderRepository orders,
            InMemoryEventRepository events,
            FakeAlpacaClient alpaca,
            MarketHoursService marketHoursService,
            boolean liveTradingEnabled,
            StrategyMode defaultStrategyMode,
            ApplicationMode applicationMode
    ) {
        try {
            AppSettingsService settingsService = new AppSettingsService(java.nio.file.Files.createTempDirectory("neuralarc-service-test").resolve("settings.properties"));
            settingsService.save(new AppSettingsService.AppSettings(
                    "test@example.com",
                    true,
                    true,
                    false,
                    BrokerType.ALPACA,
                    applicationMode,
                    false
            ));
            return new StrategyService(
                    strategies,
                    orders,
                    events,
                    alpaca,
                    new StrategyValidator(),
                    liveTradingEnabled,
                    defaultStrategyMode,
                    settingsService,
                    marketHoursService
            );
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private Strategy baseStrategy(String symbol, int qty, BigDecimal price) {
        return strategyWithId(UUID.randomUUID().toString(), symbol, qty, price);
    }

    private Strategy strategyWithId(String id, String symbol, int qty, BigDecimal price) {
        return new Strategy(
                id,
                "test",
                symbol,
                StrategyMode.PAPER,
                StrategyStatus.CREATED,
                StrategyLifecycleState.CREATED,
                price,
                qty,
                new BigDecimal("6.00"),
                5,
                new BigDecimal("5.00"),
                5,
                true,
                StopLossType.FIXED_PRICE,
                new BigDecimal("7.00"),
                BigDecimal.ZERO,
                false,
                BigDecimal.ZERO,
                true,
                new BigDecimal("10.00"),
                new BigDecimal("100.00"),
                true,
                false,
                ProfitHoldType.PERCENT_TRAILING,
                new BigDecimal("10.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                20,
                new BigDecimal("200.00"),
                2,
                Instant.now(),
                Instant.now()
        );
    }

    private static final class FakeAlpacaClient implements AlpacaClient {
        private int counter;
        private BigDecimal latestPrice = Monetary.zero();
        private Optional<AlpacaPositionData> position = Optional.empty();
        private final List<AlpacaOrderData> openOrders = new ArrayList<>();
        private final List<String> canceledOrderIds = new ArrayList<>();
        private List<AlpacaPositionData> allPositions = List.of();
        private final List<AlpacaOrderData> submittedOrders = new ArrayList<>();
        private boolean rejectBuyOrdersWithSessionMessage;

        @Override
        public AlpacaOrderData submitLimitBuyOrder(String symbol, int quantity, BigDecimal limitPrice, String clientOrderId) {
            if (rejectBuyOrdersWithSessionMessage) {
                return new AlpacaOrderData(
                        "",
                        clientOrderId,
                        symbol,
                        "buy",
                        "limit",
                        limitPrice,
                        Monetary.zero(),
                        Monetary.zero(),
                        "rejected",
                        "{\"message\":\"order rejected: market is closed for this order type\"}"
                );
            }
            counter++;
            AlpacaOrderData order = new AlpacaOrderData("ord-" + counter, clientOrderId, symbol, "buy", "limit", limitPrice, Monetary.zero(), Monetary.zero(), "new", "{\"qty\":\"" + quantity + "\"}");
            submittedOrders.add(order);
            openOrders.add(order);
            return order;
        }

        @Override
        public AlpacaOrderData submitLimitSellOrder(String symbol, int quantity, BigDecimal limitPrice, String clientOrderId) {
            counter++;
            AlpacaOrderData order = new AlpacaOrderData("ord-" + counter, clientOrderId, symbol, "sell", "limit", limitPrice, Monetary.zero(), Monetary.zero(), "new", "{\"qty\":\"" + quantity + "\"}");
            submittedOrders.add(order);
            openOrders.add(order);
            return order;
        }

        @Override
        public AlpacaOrderData submitMarketSellOrder(String symbol, int quantity, String clientOrderId) {
            counter++;
            AlpacaOrderData order = new AlpacaOrderData("ord-" + counter, clientOrderId, symbol, "sell", "market", Monetary.zero(), Monetary.zero(), Monetary.zero(), "new", "{\"qty\":\"" + quantity + "\"}");
            submittedOrders.add(order);
            openOrders.add(order);
            return order;
        }

        @Override
        public AlpacaOrderData submitTrailingStopSellOrder(String symbol, int quantity, BigDecimal trailPercent, BigDecimal trailPrice, String clientOrderId) {
            counter++;
            BigDecimal effectiveLimit = trailPrice != null && trailPrice.compareTo(BigDecimal.ZERO) > 0 ? trailPrice : Monetary.zero();
            AlpacaOrderData order = new AlpacaOrderData("ord-" + counter, clientOrderId, symbol, "sell", "trailing_stop", effectiveLimit, Monetary.zero(), Monetary.zero(), "new", "{\"qty\":\"" + quantity + "\"}");
            submittedOrders.add(order);
            openOrders.add(order);
            return order;
        }

        @Override
        public Optional<AlpacaOrderData> getOrder(String orderId) { return Optional.empty(); }

        @Override
        public List<AlpacaOrderData> getOpenOrders(String symbol) {
            if (symbol == null || symbol.isBlank()) {
                return List.copyOf(openOrders);
            }
            return openOrders.stream().filter(order -> order.symbol().equalsIgnoreCase(symbol)).toList();
        }

        @Override
        public List<AlpacaOrderData> getOpenOrders() {
            return List.copyOf(openOrders);
        }

        @Override
        public boolean cancelOrder(String orderId) {
            canceledOrderIds.add(orderId);
            openOrders.removeIf(order -> order.orderId().equals(orderId));
            return true;
        }

        @Override
        public Optional<AlpacaPositionData> getPosition(String symbol) { return position; }

        @Override
        public List<AlpacaPositionData> getPositions() { return allPositions; }

        @Override
        public BigDecimal getLatestPrice(String symbol) { return latestPrice; }
    }

    private static final class AlwaysOpenMarketHoursService extends MarketHoursService {
        @Override
        public boolean isTradingSessionOpen(boolean extendedHoursEnabled) {
            return true;
        }

        @Override
        public boolean isTradingSessionOpen(Instant instant, boolean extendedHoursEnabled) {
            return true;
        }
    }

    private static final class AlwaysClosedMarketHoursService extends MarketHoursService {
        @Override
        public boolean isTradingSessionOpen(boolean extendedHoursEnabled) {
            return false;
        }

        @Override
        public boolean isTradingSessionOpen(Instant instant, boolean extendedHoursEnabled) {
            return false;
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
