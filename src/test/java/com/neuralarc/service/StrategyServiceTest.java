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
        assertNotNull(result.alpacaOrderId());
        Strategy stored = strategies.findById(strategy.id()).orElseThrow();
        assertEquals(StrategyStatus.ACTIVE, stored.status());
        StrategyOrder initial = orders.findLatestByStrategyStage(strategy.id(), StrategyStage.INITIAL_BUY).orElseThrow();
        assertEquals(StrategyOrderType.LIMIT, initial.orderType());
        assertTrue(initial.clientOrderId().startsWith("neuralarc-" + strategy.id() + "-INITIAL_BUY-"));
    }

    @Test
    void liveModeBlockedWhenFlagDisabled() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryEventRepository events = new InMemoryEventRepository();
        StrategyService service = new StrategyService(strategies, orders, events, new FakeAlpacaClient(), new StrategyValidator(), false);

        Strategy live = new Strategy(
                UUID.randomUUID().toString(), "live", "AAPL", StrategyMode.LIVE, StrategyStatus.CREATED,
                new BigDecimal("10.00"), 1,
                new BigDecimal("9.00"), new BigDecimal("11.00"),
                false, new BigDecimal("2.00"),
                new BigDecimal("8.00"), 1,
                new BigDecimal("7.00"), 1,
                5, new BigDecimal("50.00"),
                Instant.now(), Instant.now()
        );
        StrategyService.StrategyCreationResult result = service.createAndActivate(live);
        assertFalse(result.success());
        assertTrue(result.error().contains("LIVE mode is disabled"));
    }

    private Strategy baseStrategy(String symbol, int qty, BigDecimal price) {
        return new Strategy(
                UUID.randomUUID().toString(),
                "test",
                symbol,
                StrategyMode.PAPER,
                StrategyStatus.CREATED,
                price,
                qty,
                new BigDecimal("7.00"),
                new BigDecimal("10.00"),
                false,
                new BigDecimal("2.00"),
                new BigDecimal("6.00"),
                5,
                new BigDecimal("5.00"),
                5,
                20,
                new BigDecimal("200.00"),
                Instant.now(),
                Instant.now()
        );
    }

    private static final class FakeAlpacaClient implements AlpacaClient {
        private int counter;

        @Override
        public AlpacaOrderData submitLimitBuyOrder(String symbol, int quantity, BigDecimal limitPrice, String clientOrderId) {
            counter++;
            return new AlpacaOrderData("ord-" + counter, clientOrderId, symbol, "buy", "limit", limitPrice, Monetary.zero(), "new", "{}");
        }

        @Override
        public AlpacaOrderData submitLimitSellOrder(String symbol, int quantity, BigDecimal limitPrice, String clientOrderId) {
            counter++;
            return new AlpacaOrderData("ord-" + counter, clientOrderId, symbol, "sell", "limit", limitPrice, Monetary.zero(), "new", "{}");
        }

        @Override
        public Optional<AlpacaOrderData> getOrder(String orderId) { return Optional.empty(); }

        @Override
        public List<AlpacaOrderData> getOpenOrders(String symbol) { return List.of(); }

        @Override
        public Optional<AlpacaPositionData> getPosition(String symbol) { return Optional.empty(); }

        @Override
        public BigDecimal getLatestPrice(String symbol) { return Monetary.zero(); }
    }

    private static final class InMemoryStrategyRepository implements StrategyRepository {
        private final Map<String, Strategy> store = new HashMap<>();
        @Override public void save(Strategy strategy) { store.put(strategy.id(), strategy); }
        @Override public Optional<Strategy> findById(String id) { return Optional.ofNullable(store.get(id)); }
        @Override public List<Strategy> findAll() { return new ArrayList<>(store.values()); }
        @Override public List<Strategy> findActive() { return findAll().stream().filter(s -> s.status() == StrategyStatus.ACTIVE).toList(); }
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
    }

    private static final class InMemoryEventRepository implements StrategyExecutionEventRepository {
        private final List<StrategyExecutionEvent> events = new ArrayList<>();
        @Override public void save(StrategyExecutionEvent event) { events.add(event); }
        @Override public List<StrategyExecutionEvent> findByStrategyId(String strategyId) { return events.stream().filter(e -> e.strategyId().equals(strategyId)).toList(); }
    }
}

