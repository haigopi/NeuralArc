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
    void strategyValidationFailsForEmptySymbol() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        StrategyService service = new StrategyService(strategies, orders, events, new FakeAlpacaClient(), new StrategyValidator(), false, StrategyMode.PAPER);

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
        StrategyService service = new StrategyService(strategies, orders, events, alpaca, new StrategyValidator(), false, StrategyMode.PAPER);

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
    void liveModeBlockedWhenFlagDisabled() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        StrategyService service = new StrategyService(strategies, orders, events, new FakeAlpacaClient(), new StrategyValidator(), false, StrategyMode.PAPER);

        Strategy live = baseStrategy("AAPL", 1, new BigDecimal("10.00"));
        live.setMode(StrategyMode.LIVE);

        StrategyService.StrategyCreationResult result = service.createAndActivate(live);
        assertFalse(result.success());
        assertTrue(result.error().contains("LIVE mode is disabled"));
    }

    @Test
    void closePositionPlacesCloseOrder() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        alpaca.position = Optional.of(new AlpacaPositionData("AAPL", new BigDecimal("7"), new BigDecimal("8.00"), new BigDecimal("9.50"), "{}"));
        alpaca.latestPrice = new BigDecimal("9.50");
        StrategyService service = new StrategyService(strategies, orders, events, alpaca, new StrategyValidator(), false, StrategyMode.PAPER);

        Strategy strategy = baseStrategy("AAPL", 10, new BigDecimal("8.00"));
        strategies.save(strategy);
        strategy.setStatus(StrategyStatus.ACTIVE);

        StrategyService.StrategyCreationResult result = service.closePosition(strategy.id());

        assertTrue(result.success());
        assertTrue(orders.findLatestByStrategyStage(strategy.id(), StrategyStage.CLOSE_POSITION).isPresent());
    }

    @Test
    void pauseCancelsAcceptedOpenOrdersInAlpaca() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        StrategyService service = new StrategyService(strategies, orders, events, alpaca, new StrategyValidator(), false, StrategyMode.PAPER);

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
        assertEquals(StrategyStatus.PAUSED, strategies.findById(strategy.id()).orElseThrow().status());
    }

    @Test
    void resumeResubmitsBaseBuyWhenPausedOrderWasCanceled() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        StrategyService service = new StrategyService(strategies, orders, events, alpaca, new StrategyValidator(), false, StrategyMode.PAPER);

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
    void updateActiveStrategyCancelsOpenOrdersAndRecreatesWithUpdatedConfig() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        StrategyService service = new StrategyService(strategies, orders, events, alpaca, new StrategyValidator(), false, StrategyMode.PAPER);

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
    void updatePausedStrategyDoesNotCancelOrRecreateOrders() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        StrategyService service = new StrategyService(strategies, orders, events, alpaca, new StrategyValidator(), false, StrategyMode.PAPER);

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
        StrategyService service = new StrategyService(strategies, orders, events, alpaca, new StrategyValidator(), false, StrategyMode.PAPER);

        List<Strategy> synced = service.syncRemoteStrategies();

        assertEquals(1, synced.size());
        Strategy syncedStrategy = synced.getFirst();
        assertEquals("NIO", syncedStrategy.symbol());
        assertEquals(StrategyStatus.ACTIVE, syncedStrategy.status());
        assertTrue(orders.findByStrategyId(syncedStrategy.id()).stream().anyMatch(order -> "ord-remote".equals(order.alpacaOrderId())));
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

        @Override
        public AlpacaOrderData submitLimitBuyOrder(String symbol, int quantity, BigDecimal limitPrice, String clientOrderId) {
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
