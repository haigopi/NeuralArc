package com.neuralarc.ui;

import com.neuralarc.model.ProfitHoldType;
import com.neuralarc.model.Position;
import com.neuralarc.model.SellSubmissionType;
import com.neuralarc.model.StopLossType;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyStatus;
import com.neuralarc.service.StrategyService;
import org.junit.jupiter.api.Test;

import javax.swing.JMenuItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioActionsControllerTest {
    @Test
    void repositionExpiredTargetsRunInParallel() {
        BlockingRepositionService service = new BlockingRepositionService();
        PortfolioActionsController controller = new PortfolioActionsController(new FakeGateway(service));
        List<ManagedStrategy> targets = List.of(
                managed("AAPL"),
                managed("MSFT")
        );

        PortfolioActionsSupport.BatchResult result = controller.repositionExpiredTargets(targets);

        assertTrue(result.failures().isEmpty());
        assertTrue(service.maxConcurrent.get() >= 2, "Expected at least two reposition calls in flight at once");
    }

    @Test
    void sellTargetsRunInParallel() {
        BlockingRepositionService service = new BlockingRepositionService();
        FakeGateway gateway = new FakeGateway(service);
        gateway.blockSell = true;
        PortfolioActionsController controller = new PortfolioActionsController(gateway);
        List<ManagedStrategy> targets = List.of(
                managed("AAPL"),
                managed("MSFT")
        );

        PortfolioActionsSupport.BatchResult result = controller.sellTargets(targets, SellSubmissionType.LIMIT);

        assertTrue(result.failures().isEmpty());
        assertTrue(gateway.maxConcurrentSells.get() >= 2, "Expected at least two sell calls in flight at once");
    }

    @Test
    void deleteAllPaperModeTargetsDeletePaperAndSkipLive() {
        FakeGateway gateway = new FakeGateway(new BlockingRepositionService());
        PortfolioActionsController controller = new PortfolioActionsController(gateway);
        ManagedStrategy paper = managed("AAPL");
        ManagedStrategy live = managed("MSFT");
        live.strategy.setMode(StrategyMode.LIVE);

        PortfolioActionsSupport.BatchResult result = controller.deleteAllPaperModeTargets(List.of(paper, live));

        assertEquals(List.of("AAPL"), result.successes());
        assertEquals(List.of("MSFT: skipped non-PAPER strategy"), result.skipped());
        assertTrue(result.failures().isEmpty());
        assertEquals(List.of("AAPL-id"), gateway.deletedPaperStrategyIds);
    }

    @Test
    void deleteAllPaperModeTargetsAreBlockedInLiveView() {
        FakeGateway gateway = new FakeGateway(new BlockingRepositionService());
        gateway.selectedViewMode = StrategyMode.LIVE;
        PortfolioActionsController controller = new PortfolioActionsController(gateway);

        PortfolioActionsSupport.BatchResult result = controller.deleteAllPaperModeTargets(List.of(managed("AAPL")));

        assertTrue(result.successes().isEmpty());
        assertTrue(result.failures().getFirst().contains("LIVE"));
        assertTrue(gateway.deletedPaperStrategyIds.isEmpty());
    }

    @Test
    void placePendingBaseBuyTargetsSubmitThroughGateway() {
        FakeGateway gateway = new FakeGateway(new BlockingRepositionService());
        PortfolioActionsController controller = new PortfolioActionsController(gateway);

        PortfolioActionsSupport.BatchResult result = controller.placePendingBaseBuyTargets(List.of(managed("AAPL")));

        assertEquals(List.of("AAPL"), result.successes());
        assertTrue(result.failures().isEmpty());
        assertEquals(List.of("AAPL-id"), gateway.placedPendingStrategyIds);
    }

    @Test
    void readjustLosingPendingBaseBuyTargetsSubmitThroughGateway() {
        FakeGateway gateway = new FakeGateway(new BlockingRepositionService());
        PortfolioActionsController controller = new PortfolioActionsController(gateway);
        ManagedStrategy pending = managed("AAPL");
        pending.strategy.setLatestOrderStatus("ORB_RECOMMENDED");
        pending.strategy.setBaseBuyLimitPrice(new BigDecimal("101.00"));
        Position position = new Position("AAPL");
        position.setLastPrice(new BigDecimal("100.00"));
        pending.setCachedPosition(position);

        PortfolioActionsSupport.BatchResult result = controller.readjustLosingPendingBaseBuyTargets(List.of(pending));

        assertEquals(List.of("AAPL"), result.successes());
        assertTrue(result.failures().isEmpty());
        assertEquals(List.of("AAPL-id"), gateway.readjustedPendingStrategyIds);
    }

    @Test
    void averageLosingPositionTargetsUseCurrentPositionQuantityAndDiscountedLimit() {
        FakeGateway gateway = new FakeGateway(new BlockingRepositionService());
        PortfolioActionsController controller = new PortfolioActionsController(gateway);

        PortfolioActionsSupport.BatchResult result = controller.averageLosingPositionTargets(
                List.of(managedWithPosition("AAPL", 6, "10.00", "8.00")),
                new AverageLosingPositionsSelection(
                        AverageLosingPositionsSelection.OrderType.LIMIT_BELOW_MARKET,
                        AverageLosingPositionsSelection.QuantityMode.CURRENT_POSITION_QUANTITY,
                        1,
                        new BigDecimal("2.50")
                )
        );

        assertEquals(List.of("AAPL (6)"), result.successes());
        assertTrue(result.failures().isEmpty());
        assertEquals("AAPL-id", gateway.limitBuyStrategyId);
        assertEquals(6, gateway.limitBuyQuantity);
        assertEquals(0, new BigDecimal("7.80").compareTo(gateway.limitBuyPrice));
    }

    @Test
    void averageLosingPositionTargetsCanUseFixedQuantityMarketBuy() {
        FakeGateway gateway = new FakeGateway(new BlockingRepositionService());
        PortfolioActionsController controller = new PortfolioActionsController(gateway);

        PortfolioActionsSupport.BatchResult result = controller.averageLosingPositionTargets(
                List.of(managedWithPosition("AAPL", 6, "10.00", "8.00")),
                new AverageLosingPositionsSelection(
                        AverageLosingPositionsSelection.OrderType.MARKET,
                        AverageLosingPositionsSelection.QuantityMode.FIXED_INPUT_QUANTITY,
                        3,
                        BigDecimal.ZERO
                )
        );

        assertEquals(List.of("AAPL (3)"), result.successes());
        assertTrue(result.failures().isEmpty());
        assertEquals("AAPL-id", gateway.marketBuyStrategyId);
        assertEquals(3, gateway.marketBuyQuantity);
    }

    @Test
    void deletePendingBaseBuyTargetsDeleteThroughGateway() {
        FakeGateway gateway = new FakeGateway(new BlockingRepositionService());
        PortfolioActionsController controller = new PortfolioActionsController(gateway);

        PortfolioActionsSupport.BatchResult result = controller.deletePendingBaseBuyTargets(List.of(managed("AAPL")));

        assertEquals(List.of("AAPL"), result.successes());
        assertTrue(result.failures().isEmpty());
        assertEquals(List.of("AAPL-id"), gateway.deletedPendingBaseBuyIds);
    }

    private static ManagedStrategy managed(String symbol) {
        Strategy strategy = new Strategy(
                symbol + "-id",
                symbol + " Strategy",
                symbol,
                StrategyMode.PAPER,
                StrategyStatus.FAILED,
                StrategyLifecycleState.FAILED,
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
                false,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                false,
                ProfitHoldType.PERCENT_TRAILING,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                25,
                new BigDecimal("300.00"),
                2,
                Instant.now(),
                Instant.now()
        );
        strategy.setLatestOrderStatus("expired");
        return new ManagedStrategy(strategy);
    }

    private static ManagedStrategy managedWithPosition(String symbol, int shares, String averageCost, String lastPrice) {
        ManagedStrategy managed = managed(symbol);
        managed.strategy.setStatus(StrategyStatus.ACTIVE);
        managed.strategy.setCurrentState(StrategyLifecycleState.BUY_LIMIT_1_FILLED);
        Position position = new Position(symbol);
        position.applyBuy(shares, new BigDecimal(averageCost));
        position.setLastPrice(new BigDecimal(lastPrice));
        managed.setCachedPosition(position);
        return managed;
    }

    private static final class BlockingRepositionService extends StrategyService {
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger maxConcurrent = new AtomicInteger();
        private final CountDownLatch bothEntered = new CountDownLatch(2);

        private BlockingRepositionService() {
            super(null, null, null, null, null, true, StrategyMode.PAPER);
        }

        @Override
        public StrategyCreationResult repositionExpiredStrategy(String strategyId) {
            int current = active.incrementAndGet();
            maxConcurrent.accumulateAndGet(current, Math::max);
            bothEntered.countDown();
            try {
                bothEntered.await(1, TimeUnit.SECONDS);
                Thread.sleep(50L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                active.decrementAndGet();
            }
            return StrategyCreationResult.success(strategyId, "order-" + strategyId, "alpaca-" + strategyId, "client-" + strategyId);
        }
    }

    private static final class FakeGateway implements PortfolioActionsController.Gateway {
        private final StrategyService service;
        private final AtomicInteger activeSells = new AtomicInteger();
        private final AtomicInteger maxConcurrentSells = new AtomicInteger();
        private final CountDownLatch sellsEntered = new CountDownLatch(2);
        private final java.util.List<String> deletedPaperStrategyIds = new java.util.ArrayList<>();
        private final java.util.List<String> placedPendingStrategyIds = new java.util.ArrayList<>();
        private final java.util.List<String> readjustedPendingStrategyIds = new java.util.ArrayList<>();
        private final java.util.List<String> deletedPendingBaseBuyIds = new java.util.ArrayList<>();
        private String marketBuyStrategyId;
        private int marketBuyQuantity;
        private String limitBuyStrategyId;
        private int limitBuyQuantity;
        private BigDecimal limitBuyPrice;
        private StrategyMode selectedViewMode = StrategyMode.PAPER;
        private boolean blockSell;

        private FakeGateway(StrategyService service) {
            this.service = service;
        }

        @Override public List<ManagedStrategy> strategies() { return List.of(); }
        @Override public List<ManagedStrategy> currentStrategies() { return List.of(); }
        @Override public List<ManagedStrategy> scopedStrategies() { return List.of(); }
        @Override public StrategyService strategyService() { return service; }
        @Override public StrategyService strategyServiceForMode(StrategyMode mode) { return service; }
        @Override public StrategyMode selectedViewMode() { return selectedViewMode; }
        @Override public StrategyService.ArchiveResult archiveStrategy(String strategyId, String reason) { return StrategyService.ArchiveResult.success(strategyId); }
        @Override public StrategyService.ArchiveResult deleteLocalTradeHistoryStrategy(String strategyId) { return StrategyService.ArchiveResult.success(strategyId); }
        @Override public StrategyService.ArchiveResult deleteLocalPaperStrategy(String strategyId) {
            deletedPaperStrategyIds.add(strategyId);
            return StrategyService.ArchiveResult.success(strategyId);
        }
        @Override
        public StrategyService.StrategyCreationResult sellPosition(
                Strategy strategy,
                SellSubmissionType submissionType,
                StrategyService.SellExecutionSource executionSource
        ) {
            if (blockSell) {
                int current = activeSells.incrementAndGet();
                maxConcurrentSells.accumulateAndGet(current, Math::max);
                sellsEntered.countDown();
                try {
                    sellsEntered.await(1, TimeUnit.SECONDS);
                    Thread.sleep(50L);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    activeSells.decrementAndGet();
                }
            }
            return StrategyService.StrategyCreationResult.success(strategy.id(), "order", "alpaca", "client");
        }
        @Override
        public StrategyService.StrategyCreationResult placePendingBaseBuy(Strategy strategy) {
            placedPendingStrategyIds.add(strategy.id());
            return StrategyService.StrategyCreationResult.success(strategy.id(), "order", "alpaca", "client");
        }
        @Override
        public StrategyService.StrategyCreationResult readjustLosingPendingBaseBuy(ManagedStrategy entry) {
            readjustedPendingStrategyIds.add(entry.strategy.id());
            return StrategyService.StrategyCreationResult.success(entry.strategy.id(), "order", "alpaca", "client");
        }
        @Override public java.util.Optional<AverageLosingPositionsSelection> chooseAverageLosingPositions(List<ManagedStrategy> targets) {
            return java.util.Optional.empty();
        }
        @Override public StrategyService.StrategyCreationResult buyMoreAtMarket(Strategy strategy, int quantity) {
            marketBuyStrategyId = strategy.id();
            marketBuyQuantity = quantity;
            return StrategyService.StrategyCreationResult.success(strategy.id(), "order", "alpaca", "client");
        }
        @Override public StrategyService.StrategyCreationResult buyMoreAtLimit(Strategy strategy, int quantity, BigDecimal limitPrice) {
            limitBuyStrategyId = strategy.id();
            limitBuyQuantity = quantity;
            this.limitBuyPrice = limitPrice;
            return StrategyService.StrategyCreationResult.success(strategy.id(), "order", "alpaca", "client");
        }
        @Override
        public StrategyService.ArchiveResult deletePendingBaseBuyStrategy(String strategyId) {
            deletedPendingBaseBuyIds.add(strategyId);
            return StrategyService.ArchiveResult.success(strategyId);
        }
        @Override public JMenuItem createMenuItem(String text, String iconPath, Runnable action) { return new JMenuItem(text); }
        @Override public int confirm(Object message, String title, int optionType, int messageType) { return 0; }
        @Override public void showMessage(Object message, String title, int messageType) { }
        @Override public void syncStrategiesFromRepository() { }
        @Override public void refreshStrategyTableData() { }
        @Override public void updateSelectedStrategy() { }
        @Override public void refreshPanels() { }
        @Override public void updateStatusBar() { }
        @Override public void log(String message) { }
        @Override public void actionStarted(String actionName) { }
        @Override public void actionCompleted(String actionName, String detail) { }
        @Override public void actionSkipped(String actionName, String reason) { }
        @Override public void actionCanceled(String actionName) { }
        @Override public void actionFailed(String actionName, String reason) { }
    }
}
