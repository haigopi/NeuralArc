package com.neuralarc.ui;

import com.neuralarc.analytics.AnalyticsEvent;
import com.neuralarc.model.BrokerType;
import com.neuralarc.model.Position;
import com.neuralarc.model.RepositionSubmissionType;
import com.neuralarc.model.SellSubmissionType;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyStatus;
import com.neuralarc.service.StrategyService;
import org.junit.jupiter.api.Test;

import javax.swing.JOptionPane;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyActionsControllerTest {
    private final StrategyActionsPresenter presenter = new StrategyActionsPresenter();

    @Test
    void promoteButtonIsVisibleOnlyForPaperMode() {
        assertTrue(presenter.present(new StrategyActionsPresenter.StrategyActionsState(
                StrategyStatus.ACTIVE,
                false,
                false,
                "Working...",
                true,
                false,
                true,
                ""
        )).promoteVisible());
        assertFalse(presenter.present(new StrategyActionsPresenter.StrategyActionsState(
                StrategyStatus.ACTIVE,
                false,
                false,
                "Working...",
                false,
                false,
                true,
                ""
        )).promoteVisible());
    }

    @Test
    void togglePauseResumeReturnsEarlyForArchivedStrategy() {
        FakeGateway gateway = new FakeGateway(baseStrategy(StrategyMode.PAPER, StrategyStatus.ARCHIVED));
        StrategyActionsController controller = new StrategyActionsController(gateway);

        controller.togglePauseResume(0);

        assertEquals(0, gateway.backgroundTasksRun);
        assertEquals(0, gateway.refreshRowCalls);
    }

    @Test
    void togglePauseResumeReturnsEarlyForCompletedStrategy() {
        FakeGateway gateway = new FakeGateway(baseStrategy(StrategyMode.PAPER, StrategyStatus.COMPLETED));
        StrategyActionsController controller = new StrategyActionsController(gateway);

        controller.togglePauseResume(0);

        assertEquals(0, gateway.backgroundTasksRun);
        assertEquals(0, gateway.refreshRowCalls);
    }

    @Test
    void togglePauseResumeRunsForPausedStrategyWhenMarketClosed() {
        FakeGateway gateway = new FakeGateway(baseStrategy(StrategyMode.PAPER, StrategyStatus.PAUSED));
        gateway.marketOpen = false;
        StrategyActionsController controller = new StrategyActionsController(gateway);

        controller.togglePauseResume(0);

        assertEquals(1, gateway.backgroundTasksRun);
        assertEquals(2, gateway.refreshRowCalls);
    }

    @Test
    void cancelActiveStrategyReturnsEarlyWhenConfirmationDeclined() {
        FakeGateway gateway = new FakeGateway(baseStrategy(StrategyMode.PAPER, StrategyStatus.ACTIVE));
        gateway.confirmResult = JOptionPane.NO_OPTION;
        StrategyActionsController controller = new StrategyActionsController(gateway);

        controller.togglePauseResume(0);

        assertEquals(1, gateway.confirmCalls);
        assertEquals(0, gateway.backgroundTasksRun);
        assertEquals(0, gateway.refreshRowCalls);
    }

    @Test
    void cancelActiveStrategyRunsAfterConfirmationAccepted() {
        FakeGateway gateway = new FakeGateway(baseStrategy(StrategyMode.PAPER, StrategyStatus.ACTIVE));
        gateway.confirmResult = JOptionPane.YES_OPTION;
        StrategyActionsController controller = new StrategyActionsController(gateway);

        controller.togglePauseResume(0);

        assertEquals(1, gateway.confirmCalls);
        assertEquals(1, gateway.backgroundTasksRun);
        assertEquals(2, gateway.refreshRowCalls);
    }

    @Test
    void previewLivePromotionReturnsEarlyForLiveStrategy() {
        FakeGateway gateway = new FakeGateway(baseStrategy(StrategyMode.LIVE, StrategyStatus.ACTIVE));
        StrategyActionsController controller = new StrategyActionsController(gateway);

        controller.previewLivePromotion(0);

        assertEquals(0, gateway.previewDialogCalls);
    }

    @Test
    void previewLivePromotionReturnsEarlyForCompletedPaperStrategy() {
        FakeGateway gateway = new FakeGateway(baseStrategy(StrategyMode.PAPER, StrategyStatus.COMPLETED));
        StrategyActionsController controller = new StrategyActionsController(gateway);

        controller.previewLivePromotion(0);

        assertEquals(0, gateway.previewDialogCalls);
    }

    @Test
    void previewLivePromotionOpensForExpiredFailedPaperStrategy() {
        Strategy strategy = baseStrategy(StrategyMode.PAPER, StrategyStatus.FAILED);
        strategy.setLatestOrderStatus("expired");
        FakeGateway gateway = new FakeGateway(strategy);
        gateway.strategyService = new FakePromotionStrategyService(gateway.entry.strategy());
        StrategyActionsController controller = new StrategyActionsController(gateway);

        controller.previewLivePromotion(0);

        assertEquals(1, gateway.previewDialogCalls);
    }

    @Test
    void previewLivePromotionStillOpensWhenMarketClosed() {
        FakeGateway gateway = new FakeGateway(baseStrategy(StrategyMode.PAPER, StrategyStatus.ACTIVE));
        gateway.marketOpen = false;
        gateway.strategyService = new FakePromotionStrategyService(gateway.entry.strategy());
        StrategyActionsController controller = new StrategyActionsController(gateway);

        controller.previewLivePromotion(0);

        assertEquals(1, gateway.previewDialogCalls);
    }

    @Test
    void previewLivePromotionPassesEditedPricesToPromotionService() {
        FakeGateway gateway = new FakeGateway(baseStrategy(StrategyMode.PAPER, StrategyStatus.ACTIVE));
        FakePromotionStrategyService service = new FakePromotionStrategyService(gateway.entry.strategy());
        gateway.strategyService = service;
        gateway.promotionDialogResult = new StrategyActionsController.PromotionDialogResult(
                true,
                false,
                new BigDecimal("8.25"),
                12,
                true,
                new BigDecimal("6.75"),
                6,
                new BigDecimal("5.50"),
                4,
                new BigDecimal("10.75")
        );
        StrategyActionsController controller = new StrategyActionsController(gateway);

        controller.previewLivePromotion(0);

        assertTrue(service.promoteCalled);
        assertEquals(new BigDecimal("8.25"), service.lastEdits.baseBuyPrice());
        assertEquals(12, service.lastEdits.baseBuyQty());
        assertEquals(new BigDecimal("6.75"), service.lastEdits.buyLevel1Price());
        assertEquals(6, service.lastEdits.buyLevel1Qty());
        assertEquals(new BigDecimal("5.50"), service.lastEdits.buyLevel2Price());
        assertEquals(4, service.lastEdits.buyLevel2Qty());
        assertEquals(new BigDecimal("10.75"), service.lastEdits.targetSellPrice());
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
        assertEquals("Skipped. Strategy is not sellable, has no open position, or market is closed.", gateway.lastMessage);
        assertEquals("Unable to Sell", gateway.lastMessageTitle);
        assertEquals(JOptionPane.WARNING_MESSAGE, gateway.lastMessageType);
    }

    @Test
    void sellPositionReturnsEarlyWhenMarketClosed() {
        FakeGateway gateway = new FakeGateway(baseStrategy(StrategyMode.PAPER, StrategyStatus.ACTIVE));
        gateway.marketOpen = false;
        gateway.openPosition = true;
        StrategyActionsController controller = new StrategyActionsController(gateway);

        controller.sellPosition(0);

        assertEquals(0, gateway.confirmCalls);
        assertEquals("Skipped. Strategy is not sellable, has no open position, or market is closed.", gateway.lastMessage);
        assertEquals("Unable to Sell", gateway.lastMessageTitle);
        assertEquals(JOptionPane.WARNING_MESSAGE, gateway.lastMessageType);
    }

    @Test
    void sellPositionReturnsEarlyWhenSubmissionTypeSelectionIsCanceled() {
        FakeGateway gateway = new FakeGateway(baseStrategy(StrategyMode.PAPER, StrategyStatus.ACTIVE));
        gateway.openPosition = true;
        gateway.sellSelection = Optional.empty();
        StrategyActionsController controller = new StrategyActionsController(gateway);

        controller.sellPosition(0);

        assertEquals(0, gateway.confirmCalls);
        assertEquals(0, gateway.backgroundTasksRun);
        assertNull(gateway.lastSellSubmissionType);
    }

    @Test
    void sellPositionSubmitsSelectedMarketOrderType() {
        FakeGateway gateway = new FakeGateway(baseStrategy(StrategyMode.PAPER, StrategyStatus.ACTIVE));
        gateway.openPosition = true;
        gateway.sellSelection = Optional.of(SellSubmissionType.MARKET);
        gateway.confirmResult = JOptionPane.YES_OPTION;
        StrategyActionsController controller = new StrategyActionsController(gateway);

        controller.sellPosition(0);

        assertEquals(1, gateway.confirmCalls);
        assertEquals(1, gateway.backgroundTasksRun);
        assertEquals(SellSubmissionType.MARKET, gateway.lastSellSubmissionType);
    }

    @Test
    void sellAtMarketPlaceSubmitsPricedLimitSellWithoutTypePrompt() {
        FakeGateway gateway = new FakeGateway(baseStrategy(StrategyMode.PAPER, StrategyStatus.ACTIVE));
        gateway.openPosition = true;
        gateway.confirmResult = JOptionPane.YES_OPTION;
        StrategyActionsController controller = new StrategyActionsController(gateway);

        controller.sellPositionAtMarketPlace(0);

        assertEquals(0, gateway.sellSelectionCalls);
        assertEquals(1, gateway.confirmCalls);
        assertEquals(1, gateway.backgroundTasksRun);
        assertEquals(SellSubmissionType.LIMIT, gateway.lastSellSubmissionType);
        assertEquals(gateway.entry.strategy().id(), gateway.excludedCaptureStrategyId);
    }

    @Test
    void buyMoreAtMarketSubmitsManualBuyForSelectedQuantity() {
        Strategy strategy = baseStrategy(StrategyMode.PAPER, StrategyStatus.ACTIVE);
        FakeGateway gateway = new FakeGateway(strategy);
        gateway.marketBuyQuantity = Optional.of(6);
        StrategyActionsController controller = new StrategyActionsController(gateway);

        controller.buyMoreAtMarketPrice(0);

        assertEquals(1, gateway.backgroundTasksRun);
        assertEquals(strategy.id(), gateway.marketBuyStrategyId);
        assertEquals(6, gateway.marketBuyQuantitySubmitted);
    }

    @Test
    void buyMoreAtLimitSubmitsManualLimitBuyForSelectedPriceAndQuantity() {
        Strategy strategy = baseStrategy(StrategyMode.PAPER, StrategyStatus.ACTIVE);
        FakeGateway gateway = new FakeGateway(strategy);
        gateway.limitBuySelection = Optional.of(new ManualLimitBuySelection(5, new BigDecimal("9.25")));
        StrategyActionsController controller = new StrategyActionsController(gateway);

        controller.buyMoreAtLimitPrice(0);

        assertEquals(1, gateway.backgroundTasksRun);
        assertEquals(strategy.id(), gateway.limitBuyStrategyId);
        assertEquals(5, gateway.limitBuyQuantitySubmitted);
        assertEquals(0, new BigDecimal("9.25").compareTo(gateway.limitBuyPriceSubmitted));
    }

    @Test
    void buyMoreAtLimitIsAvailableForCompletedStrategy() {
        Strategy strategy = baseStrategy(StrategyMode.PAPER, StrategyStatus.COMPLETED);
        FakeGateway gateway = new FakeGateway(strategy);
        gateway.limitBuySelection = Optional.of(new ManualLimitBuySelection(5, new BigDecimal("9.25")));
        StrategyActionsController controller = new StrategyActionsController(gateway);

        controller.buyMoreAtLimitPrice(0);

        assertEquals(1, gateway.backgroundTasksRun);
        assertEquals(strategy.id(), gateway.limitBuyStrategyId);
    }

    @Test
    void buyMoreAtMarketRemainsUnavailableForCompletedStrategy() {
        FakeGateway gateway = new FakeGateway(baseStrategy(StrategyMode.PAPER, StrategyStatus.COMPLETED));
        gateway.marketBuyQuantity = Optional.of(5);
        StrategyActionsController controller = new StrategyActionsController(gateway);

        controller.buyMoreAtMarketPrice(0);

        assertEquals(0, gateway.backgroundTasksRun);
        assertNull(gateway.marketBuyStrategyId);
    }

    @Test
    void buyMoreAtLimitPassesAutoRepositionSelection() {
        Strategy strategy = baseStrategy(StrategyMode.PAPER, StrategyStatus.ACTIVE);
        FakeGateway gateway = new FakeGateway(strategy);
        gateway.limitBuySelection = Optional.of(new ManualLimitBuySelection(5, new BigDecimal("9.25"), true));
        StrategyActionsController controller = new StrategyActionsController(gateway);

        controller.buyMoreAtLimitPrice(0);

        assertTrue(gateway.limitBuyRepositionAfterExpirySubmitted);
    }

    @Test
    void buyMoreAtLimitSubmitsManualLimitBuyWhenMarketClosed() {
        Strategy strategy = baseStrategy(StrategyMode.PAPER, StrategyStatus.ACTIVE);
        FakeGateway gateway = new FakeGateway(strategy);
        gateway.marketOpen = false;
        gateway.limitBuySelection = Optional.of(new ManualLimitBuySelection(3, new BigDecimal("7.75")));
        StrategyActionsController controller = new StrategyActionsController(gateway);

        controller.buyMoreAtLimitPrice(0);

        assertEquals(1, gateway.backgroundTasksRun);
        assertEquals(strategy.id(), gateway.limitBuyStrategyId);
        assertEquals(3, gateway.limitBuyQuantitySubmitted);
        assertEquals(0, new BigDecimal("7.75").compareTo(gateway.limitBuyPriceSubmitted));
    }

    @Test
    void buyMoreAtMarketReturnsEarlyWhenMarketClosed() {
        FakeGateway gateway = new FakeGateway(baseStrategy(StrategyMode.PAPER, StrategyStatus.ACTIVE));
        gateway.marketOpen = false;
        StrategyActionsController controller = new StrategyActionsController(gateway);

        controller.buyMoreAtMarketPrice(0);

        assertEquals(0, gateway.confirmCalls);
        assertEquals(0, gateway.backgroundTasksRun);
        assertNull(gateway.marketBuyStrategyId);
    }

    @Test
    void buyMoreAtMarketReturnsEarlyWhenQuantityPromptCanceled() {
        FakeGateway gateway = new FakeGateway(baseStrategy(StrategyMode.PAPER, StrategyStatus.ACTIVE));
        gateway.marketBuyQuantity = Optional.empty();
        StrategyActionsController controller = new StrategyActionsController(gateway);

        controller.buyMoreAtMarketPrice(0);

        assertEquals(0, gateway.backgroundTasksRun);
        assertNull(gateway.marketBuyStrategyId);
    }

    @Test
    void repositionExpiredStrategyUsesExistingServicePath() {
        Strategy strategy = baseStrategy(StrategyMode.PAPER, StrategyStatus.FAILED);
        strategy.setLatestOrderStatus("expired");
        FakeGateway gateway = new FakeGateway(strategy);
        gateway.repositionSelection = Optional.of(RepositionSubmissionType.LIMIT_BUY);
        StrategyActionsController controller = new StrategyActionsController(gateway);

        controller.repositionExpiredStrategy(0);

        assertEquals(1, gateway.backgroundTasksRun);
        assertEquals(strategy.id(), gateway.repositionedStrategyId);
        assertEquals(RepositionSubmissionType.LIMIT_BUY, gateway.repositionSubmissionType);
    }

    @Test
    void repositionExpiredStrategyCanSubmitMarketBuy() {
        Strategy strategy = baseStrategy(StrategyMode.PAPER, StrategyStatus.FAILED);
        strategy.setLatestOrderStatus("expired");
        FakeGateway gateway = new FakeGateway(strategy);
        gateway.repositionSelection = Optional.of(RepositionSubmissionType.MARKET_BUY);
        StrategyActionsController controller = new StrategyActionsController(gateway);

        controller.repositionExpiredStrategy(0);

        assertEquals(1, gateway.backgroundTasksRun);
        assertEquals(strategy.id(), gateway.repositionedStrategyId);
        assertEquals(RepositionSubmissionType.MARKET_BUY, gateway.repositionSubmissionType);
    }

    @Test
    void repositionExpiredStrategyReturnsEarlyForNonExpiredStrategy() {
        FakeGateway gateway = new FakeGateway(baseStrategy(StrategyMode.PAPER, StrategyStatus.ACTIVE));
        StrategyActionsController controller = new StrategyActionsController(gateway);

        controller.repositionExpiredStrategy(0);

        assertEquals(0, gateway.confirmCalls);
        assertEquals(0, gateway.backgroundTasksRun);
    }

    @Test
    void cancelPendingLimitBuyCancelsAtBrokerWhenConfirmed() {
        FakeGateway gateway = new FakeGateway(baseStrategy(StrategyMode.PAPER, StrategyStatus.ACTIVE));
        gateway.cancelablePendingLimitBuy = true;
        gateway.confirmResult = JOptionPane.YES_OPTION;
        StrategyActionsController controller = new StrategyActionsController(gateway);

        controller.cancelPendingLimitBuy(0);

        assertEquals(1, gateway.confirmCalls);
        assertEquals(1, gateway.backgroundTasksRun);
        assertEquals(gateway.entryAt(0).strategy().id(), gateway.canceledLimitBuyStrategyId);
    }

    @Test
    void cancelPendingLimitBuyKeepsPollingWhenServiceLeavesStrategyActive() {
        Strategy strategy = baseStrategy(StrategyMode.PAPER, StrategyStatus.ACTIVE);
        FakeGateway gateway = new FakeGateway(strategy);
        gateway.cancelablePendingLimitBuy = true;
        gateway.confirmResult = JOptionPane.YES_OPTION;
        Strategy updated = baseStrategy(StrategyMode.PAPER, StrategyStatus.ACTIVE);
        updated.setCurrentState(StrategyLifecycleState.STOP_LOSS_ACTIVE);
        gateway.refreshedStrategy = updated;
        StrategyActionsController controller = new StrategyActionsController(gateway);

        controller.cancelPendingLimitBuy(0);

        assertEquals(1, gateway.startPollingCalls);
        assertEquals(0, gateway.stopPollingCalls);
    }

    @Test
    void cancelPendingLimitBuyReturnsEarlyWhenNoOpenOrder() {
        FakeGateway gateway = new FakeGateway(baseStrategy(StrategyMode.PAPER, StrategyStatus.ACTIVE));
        gateway.cancelablePendingLimitBuy = false;
        StrategyActionsController controller = new StrategyActionsController(gateway);

        controller.cancelPendingLimitBuy(0);

        assertEquals(0, gateway.confirmCalls);
        assertEquals(0, gateway.backgroundTasksRun);
        assertNull(gateway.canceledLimitBuyStrategyId);
    }

    @Test
    void deleteCompletedStrategyArchivesWithoutHardDelete() {
        FakeGateway gateway = new FakeGateway(baseStrategy(StrategyMode.PAPER, StrategyStatus.COMPLETED));
        gateway.confirmResult = JOptionPane.YES_OPTION;
        StrategyActionsController controller = new StrategyActionsController(gateway);

        controller.deleteStrategy(0);

        assertEquals(1, gateway.archiveCalls);
        assertEquals(0, gateway.removeCalls);
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
        int confirmResult = JOptionPane.NO_OPTION;
        boolean marketOpen = true;
        boolean openPosition;
        Optional<SellSubmissionType> sellSelection = Optional.of(SellSubmissionType.LIMIT);
        int sellSelectionCalls;
        SellSubmissionType lastSellSubmissionType;
        Optional<RepositionSubmissionType> repositionSelection = Optional.of(RepositionSubmissionType.LIMIT_BUY);
        RepositionSubmissionType repositionSubmissionType;
        String excludedCaptureStrategyId;
        String marketBuyStrategyId;
        Optional<Integer> marketBuyQuantity = Optional.of(1);
        int marketBuyQuantitySubmitted;
        String limitBuyStrategyId;
        Optional<ManualLimitBuySelection> limitBuySelection = Optional.of(new ManualLimitBuySelection(1, new BigDecimal("8.00")));
        int limitBuyQuantitySubmitted;
        BigDecimal limitBuyPriceSubmitted;
        boolean limitBuyRepositionAfterExpirySubmitted;
        String repositionedStrategyId;
        StrategyService strategyService;
        Strategy refreshedStrategy;
        int startPollingCalls;
        int stopPollingCalls;
        int archiveCalls;
        int removeCalls;
        StrategyActionsController.PromotionDialogResult promotionDialogResult =
                new StrategyActionsController.PromotionDialogResult(false, false,
                        new BigDecimal("8.00"), 10,
                        true,
                        new BigDecimal("7.00"), 5,
                        new BigDecimal("6.50"), 5,
                        new BigDecimal("10.00"));
        String lastMessage;
        String lastMessageTitle;
        int lastMessageType;

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
        @Override public StrategyService strategyService() { return strategyService; }
        @Override public StrategyService liveStrategyService() { return strategyService; }
        @Override public Optional<Strategy> findStrategyById(String strategyId) { return Optional.ofNullable(refreshedStrategy); }
        @Override public void refreshStrategyTableRow(int modelRow) { refreshRowCalls++; }
        @Override public void refreshStrategyTableData() { }
        @Override public void refreshPanels() { }
        @Override public void updateStatusBar() { }
        @Override public void restoreSelectedRow() { }
        @Override public void updateSelectedStrategy() { }
        @Override public void syncStrategiesFromRepository() { }
        @Override public void clearStrategySelection() { }
        @Override public void startPollingCountdown(String strategyId) { startPollingCalls++; }
        @Override public void stopPollingCountdown(String strategyId) { stopPollingCalls++; }
        @Override public void resetPollingCountdown(String strategyId) { }
        @Override public Position loadPositionForStrategy(Strategy strategy) { return new Position(strategy.symbol()); }
        @Override public boolean hasOpenPosition(Strategy strategy) { return openPosition; }
        @Override
        public StrategyService.ArchiveResult archiveStrategy(String strategyId, String reason) {
            archiveCalls++;
            return StrategyService.ArchiveResult.success(strategyId);
        }
        @Override public Optional<SellSubmissionType> chooseSellSubmissionType(Strategy strategy) {
            sellSelectionCalls++;
            return sellSelection;
        }
        @Override public Optional<RepositionSubmissionType> chooseRepositionSubmissionType(Strategy strategy) {
            return repositionSelection;
        }
        @Override
        public StrategyService.StrategyCreationResult sellPosition(Strategy strategy, SellSubmissionType submissionType) {
            lastSellSubmissionType = submissionType;
            return StrategyService.StrategyCreationResult.success(strategy.id(), "ord", "alpaca", "client");
        }
        @Override
        public Optional<Integer> chooseMarketBuyQuantity(Strategy strategy) {
            return marketBuyQuantity;
        }
        @Override
        public Optional<ManualLimitBuySelection> chooseLimitBuy(Strategy strategy, BigDecimal currentPrice) {
            return limitBuySelection;
        }
        @Override public BigDecimal currentPriceForStrategy(Strategy strategy) { return new BigDecimal("8.50"); }
        @Override
        public StrategyService.StrategyCreationResult buyMoreAtMarket(Strategy strategy, int quantity) {
            marketBuyStrategyId = strategy.id();
            marketBuyQuantitySubmitted = quantity;
            return StrategyService.StrategyCreationResult.success(strategy.id(), "ord", "alpaca", "client");
        }
        @Override
        public StrategyService.StrategyCreationResult buyMoreAtLimit(
                Strategy strategy,
                int quantity,
                BigDecimal limitPrice,
                boolean repositionAfterExpiry
        ) {
            limitBuyStrategyId = strategy.id();
            limitBuyQuantitySubmitted = quantity;
            limitBuyPriceSubmitted = limitPrice;
            limitBuyRepositionAfterExpirySubmitted = repositionAfterExpiry;
            return StrategyService.StrategyCreationResult.success(strategy.id(), "ord", "alpaca", "client");
        }
        @Override
        public StrategyService.StrategyCreationResult repositionExpiredStrategy(
                String strategyId,
                RepositionSubmissionType submissionType
        ) {
            repositionedStrategyId = strategyId;
            repositionSubmissionType = submissionType;
            return StrategyService.StrategyCreationResult.success(strategyId, "ord", "alpaca", "client");
        }
        boolean cancelablePendingLimitBuy;
        String canceledLimitBuyStrategyId;
        @Override public StrategyService.LimitBuyCancelResult cancelPendingLimitBuys(Strategy strategy) {
            canceledLimitBuyStrategyId = strategy.id();
            return StrategyService.LimitBuyCancelResult.success(1);
        }
        @Override public boolean hasCancelablePendingLimitBuy(Strategy strategy) { return cancelablePendingLimitBuy; }
        @Override public void excludeFromPortfolioCaptureIfRunning(String strategyId) { excludedCaptureStrategyId = strategyId; }
        @Override public BigDecimal realizedPnlForStrategy(String strategyId) { return BigDecimal.ZERO; }
        @Override public String closePaperAccountState(Strategy strategy) { return ""; }
        @Override public void updateHeaderModeStatus(BrokerType brokerType) { }
        @Override public BrokerType currentBrokerType() { return BrokerType.ALPACA; }
        @Override public boolean hasBrokerPositionAccess() { return true; }
        @Override public boolean marketOpenForUi() { return marketOpen; }
        @Override public void setSelectedStrategyId(String strategyId) { }
        @Override public String selectedStrategyId() { return null; }
        @Override public void removeStrategyAt(int modelRow) { removeCalls++; }
        @Override public void log(String message) { }
        @Override public void publishAnalytics(AnalyticsEvent event) { }
        @Override public int confirm(String message, String title, int optionType, int messageType) { confirmCalls++; return confirmResult; }
        @Override public void showMessage(String message, String title, int messageType) {
            lastMessage = message;
            lastMessageTitle = title;
            lastMessageType = messageType;
        }
        @Override public StrategyActionsController.PromotionDialogResult showLivePromotionDialog(StrategyService.LivePromotionPreview preview, String realizedPnl, String unrealizedPnl) {
            previewDialogCalls++;
            return promotionDialogResult;
        }
        @Override
        public void runBackgroundTask(StrategyActionsController.ThrowingRunnable background, Runnable onSuccess, java.util.function.Consumer<Exception> onFailure, Runnable onFinally) {
            backgroundTasksRun++;
            try {
                background.run();
                onSuccess.run();
            } catch (Exception ex) {
                onFailure.accept(ex);
            } finally {
                onFinally.run();
            }
        }
    }

    private static final class FakePromotionStrategyService extends StrategyService {
        private final Strategy previewStrategy;
        private boolean promoteCalled;
        private StrategyService.LivePromotionEdits lastEdits;

        private FakePromotionStrategyService(Strategy previewStrategy) {
            super(null, null, null, null, null, true, StrategyMode.PAPER);
            this.previewStrategy = previewStrategy;
        }

        @Override
        public LivePromotionPreview previewLivePromotion(String strategyId) {
            return new LivePromotionPreview(
                    previewStrategy,
                    true,
                    List.of(),
                    List.of(),
                    0,
                    0,
                    BigDecimal.ZERO,
                    false
            );
        }

        @Override
        public LivePromotionResult promotePaperStrategyToLive(String strategyId, LivePromotionEdits edits) {
            promoteCalled = true;
            lastEdits = edits;
            return LivePromotionResult.success(strategyId, "live-id", "alpaca-id", "client-id");
        }
    }
}
