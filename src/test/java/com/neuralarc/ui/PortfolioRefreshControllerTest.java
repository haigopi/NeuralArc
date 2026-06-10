package com.neuralarc.ui;

import com.neuralarc.api.AlpacaOrderData;
import com.neuralarc.api.AlpacaPositionData;
import com.neuralarc.api.HttpAlpacaClient;
import com.neuralarc.model.ApplicationMode;
import com.neuralarc.model.BrokerType;
import com.neuralarc.model.Position;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyOrderSide;
import com.neuralarc.model.StrategyOrderStatus;
import com.neuralarc.model.StrategyOrderType;
import com.neuralarc.model.StrategyStage;
import com.neuralarc.model.StrategyStatus;
import com.neuralarc.model.StopLossType;
import com.neuralarc.service.StrategyOrderRepository;
import com.neuralarc.service.StrategyRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioRefreshControllerTest {
    @Test
    void refreshReconcilesExpiredLeftoverLocalOrderFromAlpaca() throws Exception {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        Strategy strategy = activePendingStrategy();
        strategy.setLatestOrderStatus("new");
        strategy.setLatestAlpacaOrderId("ord-expired");
        strategies.save(strategy);
        orders.save(order(strategy.id(), "ord-expired"));

        FakeAlpacaClient client = new FakeAlpacaClient();
        client.ordersById.put("ord-expired", new AlpacaOrderData(
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
        ExecutorService executor = Executors.newSingleThreadExecutor();
        FakeGateway gateway = new FakeGateway(client);
        PortfolioRefreshController controller = new PortfolioRefreshController(strategies, orders, executor, gateway);

        controller.refresh(true);

        assertTrue(gateway.finished.await(5, TimeUnit.SECONDS));
        executor.shutdownNow();
        Strategy updated = strategies.findById(strategy.id()).orElseThrow();
        assertEquals(StrategyStatus.FAILED, updated.status());
        assertEquals(StrategyLifecycleState.FAILED, updated.currentState());
        assertEquals("expired", updated.latestOrderStatus());
        assertEquals(StrategyOrderStatus.CANCELED, orders.findByAlpacaOrderId("ord-expired").orElseThrow().status());
    }

    private Strategy activePendingStrategy() {
        return new Strategy(
                UUID.randomUUID().toString(),
                "AAPL Strategy",
                "AAPL",
                StrategyMode.PAPER,
                StrategyStatus.ACTIVE,
                StrategyLifecycleState.BASE_BUY_PLACED,
                new BigDecimal("8.00"),
                10,
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                0,
                false,
                StopLossType.FIXED_PRICE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                BigDecimal.ZERO,
                true,
                new BigDecimal("10.00"),
                BigDecimal.valueOf(100),
                true,
                false,
                false,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                10,
                new BigDecimal("80.00"),
                60,
                Instant.now(),
                Instant.now()
        );
    }

    private StrategyOrder order(String strategyId, String alpacaOrderId) {
        return new StrategyOrder(
                UUID.randomUUID().toString(),
                strategyId,
                StrategyStage.BASE_BUY,
                alpacaOrderId,
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
        );
    }

    private static final class FakeGateway implements PortfolioRefreshController.Gateway {
        private final FakeAlpacaClient client;
        private final CountDownLatch finished = new CountDownLatch(1);

        private FakeGateway(FakeAlpacaClient client) {
            this.client = client;
        }

        @Override public boolean isConnected() { return true; }
        @Override public BrokerType brokerType() { return BrokerType.ALPACA; }
        @Override public HttpAlpacaClient alpacaClientForMode(ApplicationMode mode) { return mode == ApplicationMode.PAPER ? client : null; }
        @Override public void onRefreshStarted() { }
        @Override public void onRefreshFinished() { finished.countDown(); }
        @Override public void syncStrategies(List<Strategy> strategies) { }
        @Override public void applyPositionSnapshots(Map<String, Position> snapshots) { }
        @Override public void handleInvalidBrokerMissingStrategies(List<Strategy> invalidStrategies) { }
        @Override public void refreshStrategyTableContent() { }
        @Override public void refreshPanels() { }
        @Override public void updateStatusBar() { }
        @Override public void log(String message) { }
        @Override public void showConnectionRequired() { }
        @Override public void showRefreshFailed(String message) { }
    }

    private static final class FakeAlpacaClient extends HttpAlpacaClient {
        private final Map<String, AlpacaOrderData> ordersById = new LinkedHashMap<>();

        private FakeAlpacaClient() {
            super("", "", "https://paper-api.alpaca.markets", "https://data.alpaca.markets");
        }

        @Override public List<AlpacaOrderData> getOpenOrders() { return List.of(); }
        @Override public Optional<AlpacaOrderData> getOrder(String orderId) { return Optional.ofNullable(ordersById.get(orderId)); }
        @Override public List<AlpacaPositionData> getPositions() { return List.of(); }
        @Override public Map<String, BigDecimal> getLatestPrices(List<String> symbols) { return Map.of(); }
    }

    private static final class InMemoryStrategyRepository implements StrategyRepository {
        private final Map<String, Strategy> strategies = new LinkedHashMap<>();

        @Override public void save(Strategy strategy) { strategies.put(strategy.id(), strategy); }
        @Override public Optional<Strategy> findById(String id) { return Optional.ofNullable(strategies.get(id)); }
        @Override public List<Strategy> findAll() { return new ArrayList<>(strategies.values()); }
        @Override public List<Strategy> findActive() {
            return strategies.values().stream().filter(strategy -> strategy.status() == StrategyStatus.ACTIVE).toList();
        }
        @Override public void deleteById(String id) { strategies.remove(id); }
    }

    private static final class InMemoryOrderRepository implements StrategyOrderRepository {
        private final Map<String, StrategyOrder> orders = new LinkedHashMap<>();

        @Override public void save(StrategyOrder order) { orders.put(order.id(), order); }
        @Override public List<StrategyOrder> findByStrategyId(String strategyId) {
            return orders.values().stream().filter(order -> strategyId.equals(order.strategyId())).toList();
        }
        @Override public Optional<StrategyOrder> findLatestByStrategyStage(String strategyId, StrategyStage stage) {
            return findByStrategyId(strategyId).stream()
                    .filter(order -> order.stage() == stage)
                    .reduce((ignored, latest) -> latest);
        }
        @Override public Optional<StrategyOrder> findByAlpacaOrderId(String alpacaOrderId) {
            return orders.values().stream().filter(order -> alpacaOrderId.equals(order.alpacaOrderId())).findFirst();
        }
        @Override public Optional<StrategyOrder> findByClientOrderId(String clientOrderId) {
            return orders.values().stream().filter(order -> clientOrderId.equals(order.clientOrderId())).findFirst();
        }
        @Override public void deleteByStrategyId(String strategyId) {
            orders.values().removeIf(order -> strategyId.equals(order.strategyId()));
        }
    }
}
