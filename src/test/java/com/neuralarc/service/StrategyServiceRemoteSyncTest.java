package com.neuralarc.service;

import com.neuralarc.api.AlpacaClient;
import com.neuralarc.api.AlpacaOrderData;
import com.neuralarc.api.AlpacaPositionData;
import com.neuralarc.model.ApplicationMode;
import com.neuralarc.model.BrokerType;
import com.neuralarc.model.ProfitHoldType;
import com.neuralarc.model.StopLossType;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyExecutionEvent;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyStage;
import com.neuralarc.model.StrategyStatus;
import com.neuralarc.util.Monetary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Adoption of broker positions that no local strategy owns. This runs on every portfolio refresh, so
 * it has to be both correct and safe to repeat.
 */
class StrategyServiceRemoteSyncTest {
    @Test
    void aPositionHeldWithNoLocalStrategyIsAdopted() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        alpaca.allPositions = List.of(position("RKLB", "2", "60.71"));

        List<Strategy> created = liveService(strategies, alpaca).syncRemoteStrategies();

        assertEquals(1, created.size());
        assertEquals("RKLB", created.getFirst().symbol());
        assertEquals(2, created.getFirst().baseBuyQuantity());
        assertEquals(new BigDecimal("60.71"), created.getFirst().baseBuyLimitPrice());
    }

    @Test
    void aPaperRowForTheSameSymbolDoesNotBlockAdoptingTheLivePosition() {
        // SMR: held live, but a paper strategy existed for the symbol, so the live stock stayed
        // untracked forever because the symbol looked "already local".
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        strategies.save(strategy("SMR", StrategyMode.PAPER));
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        alpaca.allPositions = List.of(position("SMR", "1", "8.32"));

        List<Strategy> created = liveService(strategies, alpaca).syncRemoteStrategies();

        assertEquals(List.of("SMR"), created.stream().map(Strategy::symbol).toList());
        assertEquals(StrategyMode.LIVE, created.getFirst().mode());
    }

    @Test
    void aSymbolAlreadyTrackedInThisModeIsLeftAlone() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        strategies.save(strategy("NVDA", StrategyMode.LIVE));
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        alpaca.allPositions = List.of(position("NVDA", "10", "211.21"));

        assertTrue(liveService(strategies, alpaca).syncRemoteStrategies().isEmpty());
        assertEquals(1, strategies.findAll().size(), "no duplicate row for a symbol already tracked");
    }

    @Test
    void repeatedRefreshesAdoptOnceRatherThanEveryTime() {
        // Refresh now calls this on every press; a second call must be a no-op.
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        alpaca.allPositions = List.of(position("CURI", "1", "3.53"));
        StrategyService service = liveService(strategies, alpaca);

        assertEquals(1, service.syncRemoteStrategies().size());
        assertTrue(service.syncRemoteStrategies().isEmpty(), "the second refresh adopts nothing new");
        assertTrue(service.syncRemoteStrategies().isEmpty());
        assertEquals(1, strategies.findAll().size());
    }

    @Test
    void adoptionCoversSymbolsKnownOnlyFromAnOpenOrder() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        FakeAlpacaClient alpaca = new FakeAlpacaClient();
        alpaca.openOrders = List.of(new AlpacaOrderData("ord-1", "client-1", "UBER", "buy", "limit",
                new BigDecimal("69.25"), BigDecimal.ZERO, BigDecimal.ZERO, "new", "{\"qty\":\"10\"}", Instant.now()));

        List<Strategy> created = liveService(strategies, alpaca).syncRemoteStrategies();

        assertEquals(List.of("UBER"), created.stream().map(Strategy::symbol).toList());
    }

    @Test
    void aHeldSymbolIsAdoptedByItsOwnModeEvenWhenAnotherModesAccountHoldsNothing() {
        // The shipped bug: adoption ran against whichever single mode the runtime happened to bind,
        // so five symbols held in the live account were checked against the paper one, found absent,
        // and stayed untracked while every refresh reported success.
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();
        FakeAlpacaClient liveAlpaca = new FakeAlpacaClient();
        liveAlpaca.allPositions = List.of(position("RKLB", "2", "60.71"));
        FakeAlpacaClient paperAlpaca = new FakeAlpacaClient();

        assertTrue(service(strategies, paperAlpaca, StrategyMode.PAPER, ApplicationMode.PAPER)
                .syncRemoteStrategies().isEmpty(), "the paper account holds nothing");

        List<Strategy> created = service(strategies, liveAlpaca, StrategyMode.LIVE, ApplicationMode.LIVE)
                .syncRemoteStrategies();

        assertEquals(List.of("RKLB"), created.stream().map(Strategy::symbol).toList());
        assertEquals(StrategyMode.LIVE, created.getFirst().mode());
    }

    @Test
    void nothingAtTheBrokerMeansNothingIsCreated() {
        InMemoryStrategyRepository strategies = new InMemoryStrategyRepository();

        assertTrue(liveService(strategies, new FakeAlpacaClient()).syncRemoteStrategies().isEmpty());
        assertTrue(strategies.findAll().isEmpty());
    }

    private static StrategyService liveService(InMemoryStrategyRepository strategies, FakeAlpacaClient alpaca) {
        return service(strategies, alpaca, StrategyMode.LIVE, ApplicationMode.LIVE);
    }

    private static StrategyService service(
            InMemoryStrategyRepository strategies,
            FakeAlpacaClient alpaca,
            StrategyMode strategyMode,
            ApplicationMode applicationMode
    ) {
        try {
            AppSettingsService settings = new AppSettingsService(
                    java.nio.file.Files.createTempDirectory("neuralarc-remote-sync").resolve("settings.properties"));
            settings.save(new AppSettingsService.AppSettings(
                    "test@example.com", true, true, false, BrokerType.ALPACA, applicationMode, false));
            return new StrategyService(
                    strategies,
                    new InMemoryOrderRepository(),
                    new InMemoryEventRepository(),
                    alpaca,
                    new StrategyValidator(),
                    true,
                    strategyMode,
                    settings,
                    new MarketHoursService());
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static AlpacaPositionData position(String symbol, String quantity, String avgEntry) {
        return new AlpacaPositionData(symbol, new BigDecimal(quantity), new BigDecimal(avgEntry),
                new BigDecimal(avgEntry), "{}");
    }

    private static Strategy strategy(String symbol, StrategyMode mode) {
        return new Strategy(
                UUID.randomUUID().toString(), symbol + " " + mode, symbol, mode, StrategyStatus.ACTIVE,
                StrategyLifecycleState.CREATED, new BigDecimal("10.00"), 1,
                BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, false, StopLossType.FIXED_PRICE,
                BigDecimal.ZERO, BigDecimal.ZERO, false, BigDecimal.ZERO, false, BigDecimal.ZERO,
                BigDecimal.valueOf(100), true, false, false, ProfitHoldType.PERCENT_TRAILING,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false,
                10, new BigDecimal("1000.00"), 60, Instant.now(), Instant.now());
    }

    private static final class InMemoryStrategyRepository implements StrategyRepository {
        private final Map<String, Strategy> rows = new LinkedHashMap<>();

        @Override public void save(Strategy strategy) { rows.put(strategy.id(), strategy); }
        @Override public Optional<Strategy> findById(String id) { return Optional.ofNullable(rows.get(id)); }
        @Override public List<Strategy> findAll() { return List.copyOf(rows.values()); }
        @Override public List<Strategy> findActive() {
            return rows.values().stream().filter(row -> row.status() == StrategyStatus.ACTIVE).toList();
        }
        @Override public void deleteById(String id) { rows.remove(id); }
    }

    private static final class InMemoryOrderRepository implements StrategyOrderRepository {
        private final List<StrategyOrder> orders = new ArrayList<>();

        @Override public void save(StrategyOrder order) { orders.add(order); }
        @Override public List<StrategyOrder> findByStrategyId(String strategyId) {
            return orders.stream().filter(order -> order.strategyId().equals(strategyId)).toList();
        }
        @Override public Optional<StrategyOrder> findLatestByStrategyStage(String strategyId, StrategyStage stage) {
            return orders.stream()
                    .filter(order -> order.strategyId().equals(strategyId) && order.stage() == stage)
                    .reduce((first, second) -> second);
        }
        @Override public Optional<StrategyOrder> findByAlpacaOrderId(String alpacaOrderId) {
            return orders.stream().filter(order -> alpacaOrderId.equals(order.alpacaOrderId())).findFirst();
        }
        @Override public Optional<StrategyOrder> findByClientOrderId(String clientOrderId) {
            return orders.stream().filter(order -> clientOrderId.equals(order.clientOrderId())).findFirst();
        }
        @Override public void deleteByStrategyId(String strategyId) {
            orders.removeIf(order -> order.strategyId().equals(strategyId));
        }
    }

    private static final class InMemoryEventRepository implements StrategyExecutionEventRepository {
        private final List<StrategyExecutionEvent> events = new ArrayList<>();

        @Override public void save(StrategyExecutionEvent event) { events.add(event); }
        @Override public List<StrategyExecutionEvent> findByStrategyId(String strategyId) {
            return events.stream().filter(event -> event.strategyId().equals(strategyId)).toList();
        }
        @Override public void deleteByStrategyId(String strategyId) {
            events.removeIf(event -> event.strategyId().equals(strategyId));
        }
    }

    private static final class FakeAlpacaClient implements AlpacaClient {
        private List<AlpacaPositionData> allPositions = List.of();
        private List<AlpacaOrderData> openOrders = List.of();

        @Override public AlpacaOrderData submitLimitBuyOrder(String symbol, int quantity, BigDecimal limitPrice, String clientOrderId) {
            return order(clientOrderId, symbol, "buy", "limit", limitPrice);
        }
        @Override public AlpacaOrderData submitLimitSellOrder(String symbol, int quantity, BigDecimal limitPrice, String clientOrderId) {
            return order(clientOrderId, symbol, "sell", "limit", limitPrice);
        }
        @Override public AlpacaOrderData submitMarketSellOrder(String symbol, int quantity, String clientOrderId) {
            return order(clientOrderId, symbol, "sell", "market", Monetary.zero());
        }
        @Override public AlpacaOrderData submitTrailingStopSellOrder(String symbol, int quantity, BigDecimal trailPercent, BigDecimal trailPrice, String clientOrderId) {
            return order(clientOrderId, symbol, "sell", "trailing_stop", Monetary.zero());
        }

        private static AlpacaOrderData order(String clientOrderId, String symbol, String side, String type, BigDecimal limitPrice) {
            return new AlpacaOrderData("ord", clientOrderId, symbol, side, type, limitPrice,
                    Monetary.zero(), Monetary.zero(), "new", "{}", Instant.now());
        }
        @Override public Optional<AlpacaOrderData> getOrder(String orderId) { return Optional.empty(); }
        @Override public List<AlpacaOrderData> getOpenOrders(String symbol) {
            return openOrders.stream().filter(order -> order.symbol().equalsIgnoreCase(symbol)).toList();
        }
        @Override public List<AlpacaOrderData> getOpenOrders() { return List.copyOf(openOrders); }
        @Override public boolean cancelOrder(String orderId) { return true; }
        @Override public Optional<AlpacaPositionData> getPosition(String symbol) {
            return allPositions.stream().filter(position -> position.symbol().equalsIgnoreCase(symbol)).findFirst();
        }
        @Override public List<AlpacaPositionData> getPositions() { return List.copyOf(allPositions); }
        @Override public BigDecimal getLatestPrice(String symbol) { return Monetary.zero(); }
    }
}
