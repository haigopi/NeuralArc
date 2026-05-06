package com.neuralarc.ui;

import com.neuralarc.analytics.AnalyticsEvent;
import com.neuralarc.model.BrokerType;
import com.neuralarc.model.Position;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyStatus;
import com.neuralarc.service.StrategyService;
import org.junit.jupiter.api.Test;

import javax.swing.JOptionPane;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StrategyActionsControllerTest {
    @Test
    void togglePauseResumeReturnsEarlyForArchivedStrategy() {
        FakeGateway gateway = new FakeGateway(baseStrategy(StrategyMode.PAPER, StrategyStatus.ARCHIVED));
        StrategyActionsController controller = new StrategyActionsController(gateway);

        controller.togglePauseResume(0);

        assertEquals(0, gateway.backgroundTasksRun);
        assertEquals(0, gateway.refreshRowCalls);
    }

    @Test
    void previewLivePromotionReturnsEarlyForLiveStrategy() {
        FakeGateway gateway = new FakeGateway(baseStrategy(StrategyMode.LIVE, StrategyStatus.ACTIVE));
        StrategyActionsController controller = new StrategyActionsController(gateway);

        controller.previewLivePromotion(0);

        assertEquals(0, gateway.previewDialogCalls);
    }

    @Test
    void deleteStrategyReturnsEarlyForOutOfRangeRow() {
        FakeGateway gateway = new FakeGateway(baseStrategy(StrategyMode.PAPER, StrategyStatus.ACTIVE));
        StrategyActionsController controller = new StrategyActionsController(gateway);

        controller.deleteStrategy(5);

        assertEquals(0, gateway.confirmCalls);
    }

    @Test
    void sellPositionReturnsEarlyWhenNoOpenPositionExists() {
        FakeGateway gateway = new FakeGateway(baseStrategy(StrategyMode.PAPER, StrategyStatus.ACTIVE));
        StrategyActionsController controller = new StrategyActionsController(gateway);

        controller.sellPosition(0);

        assertEquals(0, gateway.confirmCalls);
    }

    private static Strategy baseStrategy(StrategyMode mode, StrategyStatus status) {
        Strategy strategy = new Strategy(
                UUID.randomUUID().toString(),
                "s",
                "AAPL",
                mode,
                status,
                StrategyLifecycleState.CREATED,
                new BigDecimal("8.00"),
                10,
                new BigDecimal("7.00"),
                5,
                new BigDecimal("6.50"),
                5,
                true,
                com.neuralarc.model.StopLossType.FIXED_PRICE,
                new BigDecimal("6.00"),
                BigDecimal.ZERO,
                false,
                BigDecimal.ZERO,
                true,
                new BigDecimal("10.00"),
                new BigDecimal("100.00"),
                true,
                false,
                com.neuralarc.model.ProfitHoldType.PERCENT_TRAILING,
                new BigDecimal("10.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                50,
                new BigDecimal("1000.00"),
                10,
                Instant.now(),
                Instant.now()
        );
        return strategy;
    }

    private static final class FakeGateway implements StrategyActionsController.Gateway {
        private final StrategyActionsController.ActionEntry entry;
        int backgroundTasksRun;
        int refreshRowCalls;
        int previewDialogCalls;
        int confirmCalls;

        private FakeGateway(Strategy strategy) {
            this.entry = new StrategyActionsController.ActionEntry() {
                @Override public Strategy strategy() { return strategy; }
                @Override public boolean isPaused() { return strategy.status() == StrategyStatus.PAUSED; }
                @Override public boolean isPauseResumeBusy() { return false; }
                @Override public void setPauseResumeBusy(boolean value) { }
                @Override public void setPauseResumeBusyText(String value) { }
                @Override public void syncFrom(Strategy updated) { }
            };
        }

        @Override public int toModelRow(int viewRow) { return viewRow; }
        @Override public int strategiesSize() { return 1; }
        @Override public StrategyActionsController.ActionEntry entryAt(int modelRow) { return entry; }
        @Override public StrategyService strategyService() { return null; }
        @Override public Optional<Strategy> findStrategyById(String strategyId) { return Optional.empty(); }
        @Override public void refreshStrategyTableRow(int modelRow) { refreshRowCalls++; }
        @Override public void refreshStrategyTableData() { }
        @Override public void refreshPanels() { }
        @Override public void updateStatusBar() { }
        @Override public void restoreSelectedRow() { }
        @Override public void updateSelectedStrategy() { }
        @Override public void syncStrategiesFromRepository() { }
        @Override public void clearStrategySelection() { }
        @Override public void startPollingCountdown(String strategyId) { }
        @Override public void stopPollingCountdown(String strategyId) { }
        @Override public void resetPollingCountdown(String strategyId) { }
        @Override public Position loadPositionForStrategy(Strategy strategy) { return new Position(strategy.symbol()); }
        @Override public boolean hasOpenPosition(Strategy strategy) { return false; }
        @Override public StrategyService.StrategyCreationResult sellPosition(Strategy strategy) { return StrategyService.StrategyCreationResult.failed("not-used"); }
        @Override public BigDecimal realizedPnlForStrategy(String strategyId) { return BigDecimal.ZERO; }
        @Override public String closePaperAccountState(Strategy strategy) { return ""; }
        @Override public void updateHeaderModeStatus(BrokerType brokerType) { }
        @Override public BrokerType currentBrokerType() { return BrokerType.ALPACA; }
        @Override public boolean hasBrokerPositionAccess() { return true; }
        @Override public void setSelectedStrategyId(String strategyId) { }
        @Override public String selectedStrategyId() { return null; }
        @Override public void removeStrategyAt(int modelRow) { }
        @Override public void addArchivedRealized(StrategyMode mode, BigDecimal amount) { }
        @Override public void log(String message) { }
        @Override public void publishAnalytics(AnalyticsEvent event) { }
        @Override public int confirm(String message, String title, int optionType, int messageType) { confirmCalls++; return JOptionPane.NO_OPTION; }
        @Override public void showMessage(String message, String title, int messageType) { }
        @Override public StrategyActionsController.PromotionDialogResult showLivePromotionDialog(StrategyService.LivePromotionPreview preview, String realizedPnl, String unrealizedPnl) {
            previewDialogCalls++;
            return new StrategyActionsController.PromotionDialogResult(false, false);
        }
        @Override
        public void runBackgroundTask(StrategyActionsController.ThrowingRunnable background, Runnable onSuccess, java.util.function.Consumer<Exception> onFailure, Runnable onFinally) {
            backgroundTasksRun++;
        }
    }
}
