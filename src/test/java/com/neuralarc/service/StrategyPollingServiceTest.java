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

class StrategyPollingServiceTest {
    @Test
    void level1BuyTriggersAfterInitialFill() {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        f.addOrder(f.filledOrder(strategy.id(), StrategyStage.INITIAL_BUY, 10, new BigDecimal("8.00")));
        f.alpaca.latestPrice = new BigDecimal("6.00");

        f.service.pollStrategy(strategy.id());

        assertTrue(f.orders.findLatestByStrategyStage(strategy.id(), StrategyStage.LOSS_BUY_LEVEL_1).isPresent());
    }

    @Test
    void level2BlockedUntilLevel1FullyFilled() {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        f.addOrder(f.filledOrder(strategy.id(), StrategyStage.INITIAL_BUY, 10, new BigDecimal("8.00")));
        StrategyOrder partialL1 = f.filledOrder(strategy.id(), StrategyStage.LOSS_BUY_LEVEL_1, 5, new BigDecimal("6.00"));
        partialL1.setStatus(StrategyOrderStatus.PARTIALLY_FILLED);
        f.addOrder(partialL1);
        f.alpaca.latestPrice = new BigDecimal("5.00");

        f.service.pollStrategy(strategy.id());

        assertTrue(f.orders.findLatestByStrategyStage(strategy.id(), StrategyStage.LOSS_BUY_LEVEL_2).isEmpty());
    }

    @Test
    void level2TriggersAfterLevel1Filled() {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        f.addOrder(f.filledOrder(strategy.id(), StrategyStage.INITIAL_BUY, 10, new BigDecimal("8.00")));
        f.addOrder(f.filledOrder(strategy.id(), StrategyStage.LOSS_BUY_LEVEL_1, 5, new BigDecimal("6.00")));
        f.alpaca.latestPrice = new BigDecimal("5.00");

        f.service.pollStrategy(strategy.id());

        assertTrue(f.orders.findLatestByStrategyStage(strategy.id(), StrategyStage.LOSS_BUY_LEVEL_2).isPresent());
    }

    @Test
    void sellTriggerPlacesLimitSellWhenProfitHoldDisabled() {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        f.alpaca.latestPrice = new BigDecimal("10.00");
        f.alpaca.position = Optional.of(new AlpacaPositionData("AAPL", new BigDecimal("10"), new BigDecimal("8.00"), new BigDecimal("10.00"), "{}"));

        f.service.pollStrategy(strategy.id());

        assertTrue(f.orders.findLatestByStrategyStage(strategy.id(), StrategyStage.SELL).isPresent());
    }

    @Test
    void profitHoldSellsAfterPullbackFromHigh() {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(true);
        f.alpaca.position = Optional.of(new AlpacaPositionData("AAPL", new BigDecimal("10"), new BigDecimal("8.00"), new BigDecimal("10.00"), "{}"));

        f.alpaca.latestPrice = new BigDecimal("10.50");
        f.service.pollStrategy(strategy.id());
        assertTrue(f.orders.findLatestByStrategyStage(strategy.id(), StrategyStage.SELL).isEmpty());

        f.alpaca.latestPrice = new BigDecimal("11.00");
        f.service.pollStrategy(strategy.id());
        assertTrue(f.orders.findLatestByStrategyStage(strategy.id(), StrategyStage.SELL).isEmpty());

        f.alpaca.latestPrice = new BigDecimal("10.78");
        f.service.pollStrategy(strategy.id());
        assertTrue(f.orders.findLatestByStrategyStage(strategy.id(), StrategyStage.SELL).isPresent());
    }

    @Test
    void duplicateOrderPreventionBlocksSecondStageOrder() {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        f.addOrder(f.filledOrder(strategy.id(), StrategyStage.INITIAL_BUY, 10, new BigDecimal("8.00")));
        StrategyOrder pendingL1 = f.filledOrder(strategy.id(), StrategyStage.LOSS_BUY_LEVEL_1, 5, new BigDecimal("6.00"));
        pendingL1.setStatus(StrategyOrderStatus.SUBMITTED);
        f.addOrder(pendingL1);
        int before = f.orders.findByStrategyId(strategy.id()).size();
        f.alpaca.latestPrice = new BigDecimal("6.00");

        f.service.pollStrategy(strategy.id());

        int after = f.orders.findByStrategyId(strategy.id()).size();
        assertEquals(before, after);
    }

    @Test
    void maxQuantityAndCapitalRiskControlsBlockBuy() {
        Fixture f = new Fixture();
        Strategy strategy = new Strategy(
                UUID.randomUUID().toString(), "risk", "AAPL", StrategyMode.PAPER, StrategyStatus.ACTIVE,
                new BigDecimal("8.00"), 10,
                new BigDecimal("7.00"), new BigDecimal("10.00"),
                false, new BigDecimal("2.00"),
                new BigDecimal("6.00"), 5,
                new BigDecimal("5.00"), 5,
                10,
                new BigDecimal("80.00"),
                Instant.now(), Instant.now()
        );
        f.strategies.save(strategy);
        f.addOrder(f.filledOrder(strategy.id(), StrategyStage.INITIAL_BUY, 10, new BigDecimal("8.00")));
        f.alpaca.latestPrice = new BigDecimal("6.00");

        f.service.pollStrategy(strategy.id());

        assertTrue(f.orders.findLatestByStrategyStage(strategy.id(), StrategyStage.LOSS_BUY_LEVEL_1).isEmpty());
        assertNotNull(f.strategies.findById(strategy.id()).orElseThrow().lastError());
    }

    private static final class Fixture {
        final InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        final InMemoryOrderRepository orders = new InMemoryOrderRepository();
        final InMemoryEventRepository events = new InMemoryEventRepository();
        final FakeAlpacaClient alpaca = new FakeAlpacaClient();
        final StrategyPollingService service = new StrategyPollingService(strategies, orders, events, alpaca);

        Strategy activeStrategy(boolean profitHold) {
            Strategy strategy = new Strategy(
                    UUID.randomUUID().toString(), "s", "AAPL", StrategyMode.PAPER, StrategyStatus.ACTIVE,
                    new BigDecimal("8.00"), 10,
                    new BigDecimal("7.00"), new BigDecimal("10.00"),
                    profitHold, new BigDecimal("2.00"),
                    new BigDecimal("6.00"), 5,
                    new BigDecimal("5.00"), 5,
                    25,
                    new BigDecimal("300.00"),
                    Instant.now(), Instant.now()
            );
            strategies.save(strategy);
            return strategy;
        }

        StrategyOrder filledOrder(String strategyId, StrategyStage stage, int qty, BigDecimal price) {
            return new StrategyOrder(
                    UUID.randomUUID().toString(), strategyId, stage,
                    "ord-" + stage.name(), "client-" + stage.name(), "AAPL",
                    stage == StrategyStage.SELL ? StrategyOrderSide.SELL : StrategyOrderSide.BUY,
                    StrategyOrderType.LIMIT,
                    price,
                    qty,
                    new BigDecimal(String.valueOf(qty)),
                    StrategyOrderStatus.FILLED,
                    Instant.now(),
                    Instant.now(),
                    "{}"
            );
        }

        void addOrder(StrategyOrder order) {
            orders.save(order);
            alpaca.orderById.put(order.alpacaOrderId(), new AlpacaOrderData(
                    order.alpacaOrderId(), order.clientOrderId(), order.symbol(), order.side().name().toLowerCase(),
                    "limit", order.limitPrice(), order.filledQuantity(), order.status().name().toLowerCase(), "{}"
            ));
        }
    }

    private static final class FakeAlpacaClient implements AlpacaClient {
        BigDecimal latestPrice = new BigDecimal("8.00");
        Optional<AlpacaPositionData> position = Optional.empty();
        final Map<String, AlpacaOrderData> orderById = new HashMap<>();
        int orderCounter;

        @Override
        public AlpacaOrderData submitLimitBuyOrder(String symbol, int quantity, BigDecimal limitPrice, String clientOrderId) {
            return submit(symbol, "buy", quantity, limitPrice, clientOrderId);
        }

        @Override
        public AlpacaOrderData submitLimitSellOrder(String symbol, int quantity, BigDecimal limitPrice, String clientOrderId) {
            return submit(symbol, "sell", quantity, limitPrice, clientOrderId);
        }

        private AlpacaOrderData submit(String symbol, String side, int quantity, BigDecimal limitPrice, String clientOrderId) {
            orderCounter++;
            String orderId = "ord-" + orderCounter;
            AlpacaOrderData data = new AlpacaOrderData(orderId, clientOrderId, symbol, side, "limit", limitPrice, Monetary.zero(), "new", "{}");
            orderById.put(orderId, data);
            return data;
        }

        @Override
        public Optional<AlpacaOrderData> getOrder(String orderId) {
            return Optional.ofNullable(orderById.get(orderId));
        }

        @Override
        public List<AlpacaOrderData> getOpenOrders(String symbol) {
            return orderById.values().stream().filter(o -> o.symbol().equalsIgnoreCase(symbol)).toList();
        }

        @Override
        public Optional<AlpacaPositionData> getPosition(String symbol) {
            return position;
        }

        @Override
        public BigDecimal getLatestPrice(String symbol) {
            return latestPrice;
        }
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
        @Override public void save(StrategyExecutionEvent event) {}
        @Override public List<StrategyExecutionEvent> findByStrategyId(String strategyId) { return List.of(); }
    }
}

