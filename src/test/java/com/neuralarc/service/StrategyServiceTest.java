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
        StrategyService service = new StrategyService(strategies, orders, events, new FakeAlpacaClient(), new StrategyValidator(), false);

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
        StrategyService service = new StrategyService(strategies, orders, events, alpaca, new StrategyValidator(), false);

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
        StrategyService service = new StrategyService(strategies, orders, events, new FakeAlpacaClient(), new StrategyValidator(), false);

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
        StrategyService service = new StrategyService(strategies, orders, events, alpaca, new StrategyValidator(), false);

        Strategy strategy = baseStrategy("AAPL", 10, new BigDecimal("8.00"));
        strategies.save(strategy);
        strategy.setStatus(StrategyStatus.ACTIVE);

        StrategyService.StrategyCreationResult result = service.closePosition(strategy.id());

        assertTrue(result.success());
        assertTrue(orders.findLatestByStrategyStage(strategy.id(), StrategyStage.CLOSE_POSITION).isPresent());
    }

    private Strategy baseStrategy(String symbol, int qty, BigDecimal price) {
        return new Strategy(
                UUID.randomUUID().toString(),
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

        @Override
        public AlpacaOrderData submitLimitBuyOrder(String symbol, int quantity, BigDecimal limitPrice, String clientOrderId) {
            counter++;
            return new AlpacaOrderData("ord-" + counter, clientOrderId, symbol, "buy", "limit", limitPrice, Monetary.zero(), Monetary.zero(), "new", "{}");
        }

        @Override
        public AlpacaOrderData submitLimitSellOrder(String symbol, int quantity, BigDecimal limitPrice, String clientOrderId) {
            counter++;
            return new AlpacaOrderData("ord-" + counter, clientOrderId, symbol, "sell", "limit", limitPrice, Monetary.zero(), Monetary.zero(), "new", "{}");
        }

        @Override
        public Optional<AlpacaOrderData> getOrder(String orderId) { return Optional.empty(); }

        @Override
        public List<AlpacaOrderData> getOpenOrders(String symbol) { return List.of(); }

        @Override
        public Optional<AlpacaPositionData> getPosition(String symbol) { return position; }

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
        @Override public void deleteByStrategyId(String strategyId) { orders.removeIf(order -> order.strategyId().equals(strategyId)); }
    }

    private static final class InMemoryEventRepository implements StrategyExecutionEventRepository {
        private final List<StrategyExecutionEvent> events = new ArrayList<>();
        @Override public void save(StrategyExecutionEvent event) { events.add(event); }
        @Override public List<StrategyExecutionEvent> findByStrategyId(String strategyId) { return events.stream().filter(e -> e.strategyId().equals(strategyId)).toList(); }
        @Override public void deleteByStrategyId(String strategyId) { events.removeIf(event -> event.strategyId().equals(strategyId)); }
    }
}
