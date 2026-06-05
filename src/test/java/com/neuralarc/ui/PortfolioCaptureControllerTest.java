package com.neuralarc.ui;

import com.neuralarc.model.Position;
import com.neuralarc.model.ProfitHoldType;
import com.neuralarc.model.RecommendationType;
import com.neuralarc.model.SellSubmissionType;
import com.neuralarc.model.StopLossType;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyStatus;
import com.neuralarc.service.StrategyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioCaptureControllerTest {
    @TempDir
    Path tempDir;

    @Test
    void activeCaptureExcludesStrategySoldIndividuallyBeforeWorkerReachesIt() throws Exception {
        BlockingGateway gateway = new BlockingGateway(List.of(
                strategy("s1", "AAPL", 10, "100", "110"),
                strategy("s2", "MSFT", 5, "200", "220")
        ));
        PortfolioCaptureController controller = new PortfolioCaptureController(
                gateway,
                new PortfolioCaptureCalculator(),
                new PortfolioCaptureStateStore(tempDir.resolve("state.json")),
                new PortfolioCaptureHistoryStore(tempDir.resolve("history.json"))
        );

        controller.executeNow(config());
        assertTrue(gateway.firstSellStarted.await(2, TimeUnit.SECONDS));

        controller.excludeStrategyFromActiveCapture("s2");
        gateway.releaseFirstSell.countDown();

        assertTrue(gateway.finished.await(2, TimeUnit.SECONDS));
        assertEquals(List.of("s1"), gateway.soldStrategyIds);
        assertEquals(1, gateway.result.capturedCount());
        assertEquals(new BigDecimal("1000.00"), gateway.result.totalInvestment());
        assertEquals(new BigDecimal("1100.00"), gateway.result.estimatedPortfolioValue());
    }

    private PortfolioCaptureConfig config() {
        return new PortfolioCaptureConfig(
                PortfolioCaptureMode.CAPTURE_NOW,
                PortfolioCaptureTargetType.PROFIT_AMOUNT,
                BigDecimal.ZERO,
                true,
                1,
                false,
                false,
                PortfolioCaptureExecutionFlow.EXECUTE_ONCE_AND_STOP,
                StrategyMode.PAPER,
                1,
                RecommendationType.SHORT_TERM,
                PortfolioCaptureLuckyStrategy.VOLATILE,
                false
        );
    }

    private ManagedStrategy strategy(String id, String symbol, int quantity, String averageCost, String lastPrice) {
        Strategy strategy = new Strategy(
                id,
                symbol + " Strategy",
                symbol,
                StrategyMode.PAPER,
                StrategyStatus.ACTIVE,
                StrategyLifecycleState.BASE_BUY_FILLED,
                new BigDecimal(averageCost),
                quantity,
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
                quantity,
                new BigDecimal(averageCost).multiply(BigDecimal.valueOf(quantity)),
                1,
                Instant.now(),
                Instant.now()
        );
        Position position = new Position(symbol);
        position.applyBuy(quantity, new BigDecimal(averageCost));
        position.setLastPrice(new BigDecimal(lastPrice));
        ManagedStrategy managed = new ManagedStrategy(strategy);
        managed.setCachedPosition(position);
        return managed;
    }

    private static final class BlockingGateway implements PortfolioCaptureController.Gateway {
        private final List<ManagedStrategy> strategies;
        private final CountDownLatch firstSellStarted = new CountDownLatch(1);
        private final CountDownLatch releaseFirstSell = new CountDownLatch(1);
        private final CountDownLatch finished = new CountDownLatch(1);
        private final List<String> soldStrategyIds = new ArrayList<>();
        private PortfolioCaptureExecutionResult result;

        private BlockingGateway(List<ManagedStrategy> strategies) {
            this.strategies = strategies;
        }

        @Override public List<ManagedStrategy> strategies() { return strategies; }

        @Override
        public StrategyService.StrategyCreationResult sellPosition(
                ManagedStrategy entry,
                SellSubmissionType submissionType,
                StrategyService.SellExecutionSource executionSource
        ) {
            soldStrategyIds.add(entry.strategy.id());
            if (soldStrategyIds.size() == 1) {
                firstSellStarted.countDown();
                try {
                    assertTrue(releaseFirstSell.await(2, TimeUnit.SECONDS));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            Position position = entry.cachedPosition();
            return StrategyService.StrategyCreationResult.success(
                    entry.strategy.id(),
                    "order-" + entry.strategy.id(),
                    "alpaca-" + entry.strategy.id(),
                    "client-" + entry.strategy.id(),
                    BigDecimal.valueOf(position.getTotalShares()),
                    position.getLastPrice()
            );
        }

        @Override public int cancelPendingBaseBuys() { return 0; }
        @Override public String runLuckyAutomation(PortfolioCaptureConfig config) { return ""; }
        @Override public boolean tradingSessionOpen() { return true; }
        @Override public String nextTradingSessionOpenDisplay() { return ""; }
        @Override public void onMonitoringChanged(boolean active, PortfolioCaptureSnapshot snapshot, PortfolioCaptureConfig config) { }
        @Override public void onSnapshotUpdated(PortfolioCaptureSnapshot snapshot, PortfolioCaptureConfig config) { }
        @Override public void onAutomationStateChanged(PortfolioCaptureAutomationState state, int loopCount, int pendingCanceled) { }
        @Override public void onExecutionStarted() { }
        @Override public void onExecutionFinished(PortfolioCaptureExecutionResult result, boolean targetTriggered) {
            this.result = result;
            finished.countDown();
        }
        @Override public void log(String message) { }
    }
}
