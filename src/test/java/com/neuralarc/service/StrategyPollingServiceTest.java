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
    void targetSellPlacesLimitSellWhenProfitHoldDisabled() {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(false);
        f.alpaca.latestPrice = new BigDecimal("10.00");
        f.alpaca.position = Optional.of(new AlpacaPositionData("AAPL", new BigDecimal("10"), new BigDecimal("8.00"), new BigDecimal("10.00"), "{}"));

        f.service.pollStrategy(strategy.id());

        assertTrue(f.orders.findLatestByStrategyStage(strategy.id(), StrategyStage.TARGET_SELL).isPresent());
    }

    @Test
    void profitHoldSellsAfterPullbackFromHigh() {
        Fixture f = new Fixture();
        Strategy strategy = f.activeStrategy(true);
        f.alpaca.position = Optional.of(new AlpacaPositionData("AAPL", new BigDecimal("10"), new BigDecimal("8.00"), new BigDecimal("10.00"), "{}"));

        f.alpaca.latestPrice = new BigDecimal("10.50");
        f.service.pollStrategy(strategy.id());
        assertTrue(f.orders.findLatestByStrategyStage(strategy.id(), StrategyStage.PROFIT_EXIT).isEmpty());

        f.alpaca.latestPrice = new BigDecimal("11.00");
        f.service.pollStrategy(strategy.id());
        assertTrue(f.orders.findLatestByStrategyStage(strategy.id(), StrategyStage.PROFIT_EXIT).isEmpty());

        f.alpaca.latestPrice = new BigDecimal("9.85");
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

    private static final class Fixture {
        final InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        final InMemoryOrderRepository orders = new InMemoryOrderRepository();
        final InMemoryEventRepository events = new InMemoryEventRepository();
        final FakeAlpacaClient alpaca = new FakeAlpacaClient();
        final StrategyPollingService service = new StrategyPollingService(strategies, orders, events, alpaca);

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
                    stage == StrategyStage.TARGET_SELL || stage == StrategyStage.PROFIT_EXIT || stage == StrategyStage.STOP_LOSS || stage == StrategyStage.LOSS_EXIT || stage == StrategyStage.CLOSE_POSITION
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
