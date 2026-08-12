package com.neuralarc.ui;

import com.neuralarc.analytics.AnalyticsEvent;
import com.neuralarc.model.BrokerType;
import com.neuralarc.model.PauseReason;
import com.neuralarc.model.Position;
import com.neuralarc.model.RepositionSubmissionType;
import com.neuralarc.model.SellSubmissionType;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyStatus;
import com.neuralarc.model.TimeInForce;
import com.neuralarc.service.StrategyService;
import com.neuralarc.util.BrokerOrderStatusUtil;
import com.neuralarc.util.Monetary;

import javax.swing.JOptionPane;
import java.math.BigDecimal;
import java.util.Optional;

public final class StrategyActionsController {
    private final Gateway gateway;
    private final UserActionLogSupport actionLog;

    public StrategyActionsController(Gateway gateway) {
        this.gateway = gateway;
        this.actionLog = new UserActionLogSupport(gateway::log);
    }

    public void togglePauseResume(int viewRow) {
        int row = gateway.toModelRow(viewRow);
        if (row < 0 || row >= gateway.strategiesSize()) {
            return;
        }

        ActionEntry entry = gateway.entryAt(row);
        if (!isToggleAllowed(entry.strategy().status()) || entry.isPauseResumeBusy()) {
            return;
        }

        boolean wasPaused = entry.isPaused();
        if (!wasPaused && !confirmCancel(entry.strategy())) {
            actionLog.canceled("Cancel Strategy " + entry.strategy().symbol());
            return;
        }
        boolean manualCancelResume = wasPaused
                && entry.strategy().pauseReason() == PauseReason.MANUAL_LIMIT_BUY_CANCELED;
        String strategyId = entry.strategy().id();
        String symbol = entry.strategy().symbol();
        String actionName = wasPaused
                ? (manualCancelResume ? "Place Limit Buy Again " + symbol : "Resume Strategy " + symbol)
                : "Cancel Strategy " + symbol;
        actionLog.started(actionName);
        entry.setPauseResumeBusy(true);
        entry.setPauseResumeBusyText(wasPaused
                ? (manualCancelResume ? "Placing Limit Buy..." : "Resuming...")
                : "Canceling...");
        gateway.refreshStrategyTableRow(row);

        gateway.runBackgroundTask(
                () -> {
                    if (wasPaused) {
                        gateway.strategyService().resume(strategyId);
                    } else {
                        gateway.strategyService().pause(strategyId);
                    }
                },
                () -> {
                    if (wasPaused) {
                        gateway.log((manualCancelResume
                                ? "Place Limit Buy Again requested for symbol "
                                : "Strategy resumed for symbol ") + symbol);
                        actionLog.completed(actionName);
                    } else {
                        gateway.stopPollingCountdown(strategyId);
                        gateway.log("Manual cancel applied for symbol " + symbol
                                + ". Waiting for user action: Place Limit Buy Again.");
                        actionLog.completed(actionName);
                        gateway.publishAnalytics(new AnalyticsEvent("STRATEGY_PAUSED").put("symbol", symbol));
                    }
                    gateway.findStrategyById(strategyId).ifPresent(updated -> {
                        entry.syncFrom(updated);
                        if (wasPaused) {
                            gateway.startPollingCountdown(strategyId);
                        } else {
                            gateway.resetPollingCountdown(strategyId);
                        }
                    });
                },
                ex -> {
                    gateway.log("Cancel/Resume failed for symbol " + symbol + ": " + ex.getMessage());
                    actionLog.failed(actionName, ex.getMessage());
                },
                () -> {
                    entry.setPauseResumeBusy(false);
                    entry.setPauseResumeBusyText("");
                    gateway.refreshStrategyTableRow(row);
                    gateway.updateStatusBar();
                    gateway.refreshPanels();
                }
        );
    }

    private boolean confirmCancel(Strategy strategy) {
        String modeLabel = strategy.mode() == StrategyMode.PAPER ? "Paper Trading" : "Live Trading";
        String message = "<html><body style='width:340px'>"
                + "<b>Cancel the \"" + strategy.symbol() + "\" strategy?</b><br><br>"
                + "This cancels open Alpaca orders for this strategy, stops polling, and saves the strategy as canceled.<br><br>"
                + "• Mode: " + modeLabel + "<br>"
                + "• Manual selling remains available if a position is still open.<br><br>"
                + "You can restart it later using <b>Place Limit Buy Again</b>."
                + "</body></html>";
        int choice = gateway.confirm(
                message,
                "Cancel Strategy — " + strategy.symbol(),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        return choice == JOptionPane.YES_OPTION;
    }

    public void previewLivePromotion(int viewRow) {
        int row = gateway.toModelRow(viewRow);
        if (row < 0 || row >= gateway.strategiesSize()) {
            return;
        }

        ActionEntry entry = gateway.entryAt(row);
        if (!StrategyPromotionEligibility.canPromoteToLive(entry.strategy())) {
            actionLog.skipped("Promote to Live", "Strategy is not eligible.");
            return;
        }

        actionLog.started("Promote to Live " + entry.strategy().symbol());
        StrategyService liveService = gateway.liveStrategyService();
        StrategyService.LivePromotionPreview preview = liveService.previewLivePromotion(entry.strategy().id());
        Position paperPosition = gateway.loadPositionForStrategy(entry.strategy());
        String realizedPnl = Monetary.round(gateway.realizedPnlForStrategy(entry.strategy().id())).toPlainString();
        String unrealizedPnl = Monetary.round(paperPosition.unrealizedPnl()).toPlainString();
        PromotionDialogResult dialogResult = gateway.showLivePromotionDialog(preview, realizedPnl, unrealizedPnl);
        if (!dialogResult.proceed()) {
            actionLog.canceled("Promote to Live " + entry.strategy().symbol());
            return;
        }

        StrategyService.LivePromotionResult result = liveService.promotePaperStrategyToLive(
                entry.strategy().id(),
                new StrategyService.LivePromotionEdits(
                        dialogResult.baseBuyPrice(),
                        dialogResult.baseBuyQty(),
                        dialogResult.buyLevel1Price(),
                        dialogResult.buyLevel1Qty(),
                        dialogResult.buyLevel2Price(),
                        dialogResult.buyLevel2Qty(),
                        dialogResult.targetSellPrice(),
                        dialogResult.lossBuyLevelsEnabled()
                )
        );
        if (!result.success()) {
            actionLog.failed("Promote to Live " + entry.strategy().symbol(), result.error());
            gateway.showMessage(result.error(), "Live Promotion Failed", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String cleanupSummary = "";
        if (dialogResult.closePaperPositions()) {
            cleanupSummary = gateway.closePaperAccountState(entry.strategy());
        }

        gateway.syncStrategiesFromRepository();
        gateway.refreshStrategyTableData();
        gateway.setSelectedStrategyId(result.liveStrategyId());
        gateway.restoreSelectedRow();
        gateway.updateSelectedStrategy();
        gateway.refreshPanels();
        gateway.updateStatusBar();
        gateway.log("[" + entry.strategy().symbol() + "] Promoted paper strategy to LIVE and archived the paper copy.");
        actionLog.completed("Promote to Live " + entry.strategy().symbol());
        gateway.showMessage(
                "LIVE strategy created successfully.\nPaper strategy archived locally.\nLive Order ID: " + result.alpacaOrderId(),
                "Promotion Complete",
                JOptionPane.INFORMATION_MESSAGE
        );
        if (!cleanupSummary.isBlank()) {
            gateway.log("[" + entry.strategy().symbol() + "] " + cleanupSummary);
            gateway.showMessage(
                    cleanupSummary,
                    "Paper Cleanup",
                    cleanupSummary.contains("skipped") ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE
            );
        }
    }

    public void sellPosition(int viewRow) {
        sellPosition(viewRow, null);
    }

    public void sellPositionAtMarketPlace(int viewRow) {
        sellPosition(viewRow, SellSubmissionType.LIMIT);
    }

    public void buyMoreAtMarketPrice(int viewRow) {
        buyMore(viewRow, BuyMoreType.MARKET);
    }

    public void buyMoreAtLimitPrice(int viewRow) {
        buyMore(viewRow, BuyMoreType.LIMIT);
    }

    private void buyMore(int viewRow, BuyMoreType type) {
        int row = gateway.toModelRow(viewRow);
        if (row < 0 || row >= gateway.strategiesSize()) {
            return;
        }

        ActionEntry entry = gateway.entryAt(row);
        Strategy strategy = entry.strategy();
        if (!isManualBuyAllowed(strategy.status(), type)) {
            actionLog.skipped("Buy More " + strategy.symbol(), "Strategy is not active or paused.");
            return;
        }
        if (type == BuyMoreType.MARKET && !gateway.marketOpenForUi()) {
            actionLog.skipped("Buy More " + strategy.symbol(), "Market buy is unavailable while the market is closed.");
            return;
        }

        Optional<ManualLimitBuySelection> selection = chooseBuyMoreInput(strategy, type);
        if (selection.isEmpty()) {
            actionLog.canceled("Buy More " + strategy.symbol());
            return;
        }
        int quantity = selection.get().quantity();
        if (quantity <= 0) {
            actionLog.skipped("Buy More " + strategy.symbol(), "Quantity must be greater than zero.");
            return;
        }

        actionLog.started("Buy More " + strategy.symbol());
        gateway.runBackgroundTask(
                () -> {
                    StrategyService.StrategyCreationResult result = type == BuyMoreType.MARKET
                            ? gateway.buyMoreAtMarket(strategy, quantity)
                            : gateway.buyMoreAtLimit(strategy, quantity, selection.get().limitPrice(),
                            selection.get().repositionAfterExpiry());
                    if (!result.success()) {
                        throw new IllegalStateException(result.error());
                    }
                },
                () -> {
                    gateway.findStrategyById(strategy.id()).ifPresent(entry::syncFrom);
                    String successMessage = manualBuySuccessMessage(type, strategy, quantity, selection.get().limitPrice());
                    gateway.log(successMessage);
                    actionLog.completed("Buy More " + strategy.symbol(), successMessage);
                },
                ex -> {
                    actionLog.failed("Buy More " + strategy.symbol(), ex.getMessage());
                    gateway.showMessage(
                            "Failed to submit buy for " + strategy.symbol() + ": " + ex.getMessage(),
                            "Buy Failed",
                            JOptionPane.ERROR_MESSAGE
                    );
                },
                () -> {
                    gateway.refreshStrategyTableRow(row);
                    gateway.updateSelectedStrategy();
                    gateway.refreshPanels();
                    gateway.updateStatusBar();
                }
        );
    }

    private Optional<ManualLimitBuySelection> chooseBuyMoreInput(Strategy strategy, BuyMoreType type) {
        if (type == BuyMoreType.MARKET) {
            return gateway.chooseMarketBuyQuantity(strategy)
                    .map(quantity -> new ManualLimitBuySelection(quantity, BigDecimal.ZERO));
        }
        return gateway.chooseLimitBuy(strategy, gateway.currentPriceForStrategy(strategy));
    }

    private String manualBuySuccessMessage(StrategyActionsController.BuyMoreType type, Strategy strategy, int quantity, BigDecimal limitPrice) {
        if (type == BuyMoreType.MARKET) {
            return "Manual market buy order submitted for " + quantity + " share(s) of " + strategy.symbol() + ".";
        }
        return "Manual limit buy order submitted for " + quantity + " share(s) of " + strategy.symbol()
                + " at $" + limitPrice.toPlainString() + ".";
    }

    public void repositionExpiredStrategy(int viewRow) {
        int row = gateway.toModelRow(viewRow);
        if (row < 0 || row >= gateway.strategiesSize()) {
            return;
        }

        ActionEntry entry = gateway.entryAt(row);
        Strategy strategy = entry.strategy();
        if (!isExpiredRepositionAllowed(strategy)) {
            actionLog.skipped("Reposition Expired " + strategy.symbol(), "Strategy is not expired.");
            return;
        }

        Optional<RepositionSubmissionType> submissionType = gateway.chooseRepositionSubmissionType(strategy);
        if (submissionType.isEmpty()) {
            actionLog.canceled("Reposition Expired " + strategy.symbol());
            return;
        }
        Optional<TimeInForce> timeInForce = Optional.of(TimeInForce.DAY);
        if (submissionType.get() == RepositionSubmissionType.LIMIT_BUY) {
            timeInForce = gateway.chooseRepositionTimeInForce(strategy);
            if (timeInForce.isEmpty()) {
                actionLog.canceled("Reposition Expired " + strategy.symbol());
                return;
            }
        }

        actionLog.started("Reposition Expired " + strategy.symbol());
        Optional<TimeInForce> selectedTimeInForce = timeInForce;
        gateway.runBackgroundTask(
                () -> {
                    StrategyService.StrategyCreationResult result = gateway.repositionExpiredStrategy(
                            strategy.id(),
                            submissionType.get(),
                            selectedTimeInForce.orElse(TimeInForce.DAY)
                    );
                    if (!result.success()) {
                        throw new IllegalStateException(result.error());
                    }
                },
                () -> {
                    gateway.findStrategyById(strategy.id()).ifPresent(entry::syncFrom);
                    gateway.startPollingCountdown(strategy.id());
                    gateway.log("Expired strategy reposition submitted for symbol " + strategy.symbol()
                            + " using " + repositionTypeLabel(submissionType.get()) + ".");
                    actionLog.completed("Reposition Expired " + strategy.symbol(),
                            "Fresh " + repositionTypeLabel(submissionType.get()) + " order submitted.");
                },
                ex -> {
                    actionLog.failed("Reposition Expired " + strategy.symbol(), ex.getMessage());
                    gateway.showMessage(
                            "Failed to reposition expired strategy for " + strategy.symbol() + ": " + ex.getMessage(),
                            "Reposition Failed",
                            JOptionPane.ERROR_MESSAGE
                    );
                },
                () -> {
                    gateway.refreshStrategyTableRow(row);
                    gateway.updateSelectedStrategy();
                    gateway.refreshPanels();
                    gateway.updateStatusBar();
                }
        );
    }

    private String repositionTypeLabel(RepositionSubmissionType submissionType) {
        return submissionType == RepositionSubmissionType.MARKET_BUY ? "market buy" : "base limit buy";
    }

    private void sellPosition(int viewRow, SellSubmissionType forcedSubmissionType) {
        int row = gateway.toModelRow(viewRow);
        if (row < 0 || row >= gateway.strategiesSize()) {
            return;
        }

        ActionEntry entry = gateway.entryAt(row);
        Strategy strategy = entry.strategy();
        if (!isSellAllowed(strategy.status()) || !gateway.hasOpenPosition(strategy) || !gateway.marketOpenForUi()) {
            String reason = "Skipped. Strategy is not sellable, has no open position, or market is closed.";
            actionLog.skipped("Sell Position", reason);
            gateway.showMessage(reason, "Unable to Sell", JOptionPane.WARNING_MESSAGE);
            return;
        }

        SellSubmissionType submissionType;
        if (forcedSubmissionType == null) {
            Optional<SellSubmissionType> selection = gateway.chooseSellSubmissionType(strategy);
            if (selection.isEmpty()) {
                actionLog.canceled("Sell Position " + strategy.symbol());
                return;
            }
            submissionType = selection.get();
        } else {
            submissionType = forcedSubmissionType;
        }

        String restartMessage = strategy.restartAfterExitEnabled()
                ? "After the position fully closes, the strategy can re-initiate its cycle automatically."
                : "After the position fully closes, the strategy will remain completed unless you restart it manually.";
        String executionDetail = forcedSubmissionType != null
                ? "This cancels pending Alpaca orders for this position, then submits a prioritized limit sell at the latest broker-visible price."
                : submissionType == SellSubmissionType.MARKET
                ? "This submits a manual market sell on Alpaca. Fill price can differ from the latest quote due to market movement."
                : "This submits a manual limit sell on Alpaca using the latest broker price.";
        String message = "<html><body style='width:340px'>"
                + "<b>Sell the current " + strategy.symbol() + " position now?</b><br><br>"
                + executionDetail + "<br><br>"
                + restartMessage
                + "</body></html>";
        int choice = gateway.confirm(
                message,
                "Sell Position — " + strategy.symbol(),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (choice != JOptionPane.YES_OPTION) {
            actionLog.canceled("Sell Position " + strategy.symbol());
            return;
        }

        actionLog.started("Sell Position " + strategy.symbol());
        gateway.excludeFromPortfolioCaptureIfRunning(strategy.id());
        gateway.runBackgroundTask(
                () -> {
                    StrategyService.StrategyCreationResult result = gateway.sellPosition(strategy, submissionType);
                    if (!result.success()) {
                        throw new IllegalStateException(result.error());
                    }
                },
                () -> {
                    gateway.findStrategyById(strategy.id()).ifPresent(entry::syncFrom);
                    String sellLabel = forcedSubmissionType == null
                            ? submissionType.name().toLowerCase()
                            : "market-place priced";
                    gateway.log("Manual " + sellLabel + " sell order submitted for symbol " + strategy.symbol());
                    actionLog.completed("Sell Position " + strategy.symbol(), "Manual " + sellLabel + " sell order submitted.");
                },
                ex -> {
                    actionLog.failed("Sell Position " + strategy.symbol(), ex.getMessage());
                    gateway.showMessage(
                            "Failed to submit sell order for " + strategy.symbol() + ": " + ex.getMessage(),
                            "Sell Failed",
                            JOptionPane.ERROR_MESSAGE
                    );
                },
                () -> {
                    gateway.refreshStrategyTableRow(row);
                    gateway.updateSelectedStrategy();
                    gateway.refreshPanels();
                    gateway.updateStatusBar();
                }
        );
    }

    public void cancelPendingLimitBuy(int viewRow) {
        int row = gateway.toModelRow(viewRow);
        if (row < 0 || row >= gateway.strategiesSize()) {
            return;
        }

        ActionEntry entry = gateway.entryAt(row);
        Strategy strategy = entry.strategy();
        if (!gateway.hasCancelablePendingLimitBuy(strategy)) {
            actionLog.skipped("Cancel Pending Limit Buy " + strategy.symbol(),
                    "No open pending limit buy order to cancel.");
            return;
        }

        String message = "<html><body style='width:340px'>"
                + "<b>Cancel the pending limit buy for " + strategy.symbol() + "?</b><br><br>"
                + "This cancels the open buy order at the broker. Any already-filled position is <b>not</b> affected."
                + "</body></html>";
        int choice = gateway.confirm(
                message,
                "Cancel Pending Limit Buy — " + strategy.symbol(),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (choice != JOptionPane.YES_OPTION) {
            actionLog.canceled("Cancel Pending Limit Buy " + strategy.symbol());
            return;
        }

        actionLog.started("Cancel Pending Limit Buy " + strategy.symbol());
        gateway.runBackgroundTask(
                () -> {
                    StrategyService.LimitBuyCancelResult result = gateway.cancelPendingLimitBuys(strategy);
                    if (!result.success()) {
                        throw new IllegalStateException(result.error());
                    }
                },
                () -> {
                    Strategy updated = gateway.findStrategyById(strategy.id()).orElse(strategy);
                    entry.syncFrom(updated);
                    if (updated.status() == StrategyStatus.PAUSED
                            && updated.pauseReason() == PauseReason.MANUAL_LIMIT_BUY_CANCELED) {
                        gateway.stopPollingCountdown(strategy.id());
                    } else if (updated.status() == StrategyStatus.ACTIVE) {
                        gateway.startPollingCountdown(strategy.id());
                    }
                    gateway.log("Pending limit buy canceled at broker for symbol " + strategy.symbol() + ".");
                    actionLog.completed("Cancel Pending Limit Buy " + strategy.symbol());
                    gateway.publishAnalytics(new AnalyticsEvent("PENDING_LIMIT_BUY_CANCELED").put("symbol", strategy.symbol()));
                },
                ex -> {
                    actionLog.failed("Cancel Pending Limit Buy " + strategy.symbol(), ex.getMessage());
                    gateway.showMessage(
                            "Failed to cancel the pending limit buy for " + strategy.symbol() + ": " + ex.getMessage()
                                    + "\nThe order state was left unchanged.",
                            "Cancel Failed",
                            JOptionPane.ERROR_MESSAGE
                    );
                },
                () -> {
                    gateway.refreshStrategyTableRow(row);
                    gateway.updateSelectedStrategy();
                    gateway.refreshPanels();
                    gateway.updateStatusBar();
                }
        );
    }

    public void deleteStrategy(int viewRow) {
        int row = gateway.toModelRow(viewRow);
        if (row < 0 || row >= gateway.strategiesSize()) {
            return;
        }

        ActionEntry entry = gateway.entryAt(row);
        Strategy strategy = entry.strategy();
        String statusLabel = strategy.status().name();
        String modeLabel = strategy.mode() == StrategyMode.PAPER ? "Paper Trading" : "Live Trading";
        String positionNote;
        if (gateway.hasBrokerPositionAccess()) {
            int shares = gateway.loadPositionForStrategy(strategy).getTotalShares();
            positionNote = shares > 0
                    ? "• Open position: " + shares + " share(s) held — these will NOT be automatically sold."
                    : "• No open position.";
        } else {
            positionNote = "• Position data not available (broker not connected).";
        }
        String message = "<html><body style='width:340px'>"
                + "<b>Permanently delete the \"" + strategy.symbol() + "\" strategy?</b><br><br>"
                + "• Status: " + statusLabel + "<br>"
                + "• Mode: " + modeLabel + "<br>"
                + positionNote + "<br><br>"
                + (strategy.status() == StrategyStatus.COMPLETED
                    ? "This removes the strategy from Current Strategies and keeps history/order records.<br>"
                    : "This will immediately stop polling and permanently remove the strategy from saved data.<br>")
                + (strategy.status() == StrategyStatus.COMPLETED
                    ? "You can still review this strategy in Trade History."
                    : "This action <b>cannot be undone</b>.")
                + "</body></html>";
        int choice = gateway.confirm(
                message,
                "Delete Strategy — " + strategy.symbol(),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (choice != JOptionPane.YES_OPTION) {
            actionLog.canceled("Delete Strategy " + strategy.symbol());
            return;
        }

        actionLog.started("Delete Strategy " + strategy.symbol());
        if (strategy.status() == StrategyStatus.COMPLETED) {
            StrategyService.ArchiveResult archiveResult = gateway.archiveStrategy(
                    strategy.id(),
                    "Archived from Current Strategies by delete action for completed strategy"
            );
            if (!archiveResult.success()) {
                actionLog.failed("Delete Strategy " + strategy.symbol(), archiveResult.error());
                gateway.showMessage(
                        "Failed to remove completed strategy from Current Strategies: " + archiveResult.error(),
                        "Remove Failed",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }
            gateway.syncStrategiesFromRepository();
            gateway.log("Removed completed strategy from Current Strategies for symbol " + strategy.symbol());
            gateway.publishAnalytics(new AnalyticsEvent("STRATEGY_ARCHIVED").put("symbol", strategy.symbol()));
        } else {
            gateway.strategyService().delete(strategy.id());
            gateway.removeStrategyAt(row);
            gateway.log("Deleted strategy for symbol " + strategy.symbol());
            gateway.publishAnalytics(new AnalyticsEvent("STRATEGY_DELETED").put("symbol", strategy.symbol()));
        }

        if (gateway.strategiesSize() == 0) {
            gateway.setSelectedStrategyId(null);
        } else {
            int nextModelRow = Math.min(row, gateway.strategiesSize() - 1);
            gateway.setSelectedStrategyId(gateway.entryAt(nextModelRow).strategy().id());
        }

        gateway.updateHeaderModeStatus(gateway.currentBrokerType());
        gateway.refreshStrategyTableData();
        if (gateway.selectedStrategyId() != null) {
            gateway.restoreSelectedRow();
        } else {
            gateway.clearStrategySelection();
        }
        gateway.updateStatusBar();
        gateway.refreshPanels();
        actionLog.completed("Delete Strategy " + strategy.symbol());
    }

    public interface ActionEntry {
        Strategy strategy();
        boolean isPaused();
        boolean isPauseResumeBusy();
        void setPauseResumeBusy(boolean value);
        void setPauseResumeBusyText(String value);
        void syncFrom(Strategy strategy);
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    public interface Gateway {
        int toModelRow(int viewRow);
        int strategiesSize();
        ActionEntry entryAt(int modelRow);
        /** Returns the current-mode strategy service (paper or live per the UI view). */
        StrategyService strategyService();
        /** Always returns the live-mode strategy service, used exclusively for live promotion. */
        StrategyService liveStrategyService();
        Optional<Strategy> findStrategyById(String strategyId);

        void refreshStrategyTableRow(int modelRow);
        void refreshStrategyTableData();
        void refreshPanels();
        void updateStatusBar();
        void restoreSelectedRow();
        void updateSelectedStrategy();
        void syncStrategiesFromRepository();
        void clearStrategySelection();

        void startPollingCountdown(String strategyId);
        void stopPollingCountdown(String strategyId);
        void resetPollingCountdown(String strategyId);

        Position loadPositionForStrategy(Strategy strategy);
        boolean hasOpenPosition(Strategy strategy);
        StrategyService.ArchiveResult archiveStrategy(String strategyId, String reason);
        Optional<SellSubmissionType> chooseSellSubmissionType(Strategy strategy);
        Optional<RepositionSubmissionType> chooseRepositionSubmissionType(Strategy strategy);
        Optional<TimeInForce> chooseRepositionTimeInForce(Strategy strategy);
        Optional<Integer> chooseMarketBuyQuantity(Strategy strategy);
        Optional<ManualLimitBuySelection> chooseLimitBuy(Strategy strategy, BigDecimal currentPrice);
        BigDecimal currentPriceForStrategy(Strategy strategy);
        StrategyService.StrategyCreationResult buyMoreAtMarket(Strategy strategy, int quantity);
        StrategyService.StrategyCreationResult buyMoreAtLimit(
                Strategy strategy,
                int quantity,
                BigDecimal limitPrice,
                boolean repositionAfterExpiry
        );
        StrategyService.StrategyCreationResult sellPosition(Strategy strategy, SellSubmissionType submissionType);
        StrategyService.StrategyCreationResult repositionExpiredStrategy(
                String strategyId,
                RepositionSubmissionType submissionType,
                TimeInForce timeInForce
        );
        StrategyService.LimitBuyCancelResult cancelPendingLimitBuys(Strategy strategy);
        boolean hasCancelablePendingLimitBuy(Strategy strategy);
        void excludeFromPortfolioCaptureIfRunning(String strategyId);
        BigDecimal realizedPnlForStrategy(String strategyId);
        String closePaperAccountState(Strategy strategy);

        void updateHeaderModeStatus(BrokerType brokerType);
        BrokerType currentBrokerType();
        boolean hasBrokerPositionAccess();
        boolean marketOpenForUi();

        void setSelectedStrategyId(String strategyId);
        String selectedStrategyId();
        void removeStrategyAt(int modelRow);

        void log(String message);
        void publishAnalytics(AnalyticsEvent event);

        int confirm(String message, String title, int optionType, int messageType);
        void showMessage(String message, String title, int messageType);
        PromotionDialogResult showLivePromotionDialog(
                StrategyService.LivePromotionPreview preview,
                String realizedPnl,
                String unrealizedPnl
        );

        void runBackgroundTask(
                ThrowingRunnable background,
                Runnable onSuccess,
                java.util.function.Consumer<Exception> onFailure,
                Runnable onFinally
        );
    }

    public record PromotionDialogResult(
            boolean proceed,
            boolean closePaperPositions,
            BigDecimal baseBuyPrice,
            int baseBuyQty,
            boolean lossBuyLevelsEnabled,
            BigDecimal buyLevel1Price,
            int buyLevel1Qty,
            BigDecimal buyLevel2Price,
            int buyLevel2Qty,
            BigDecimal targetSellPrice
    ) {
    }

    private enum BuyMoreType {
        MARKET,
        LIMIT
    }

    private static boolean isToggleAllowed(StrategyStatus status) {
        return status == StrategyStatus.ACTIVE || status == StrategyStatus.PAUSED;
    }

    private static boolean isSellAllowed(StrategyStatus status) {
        return status != StrategyStatus.ARCHIVED && status != StrategyStatus.CREATED;
    }

    private static boolean isManualBuyAllowed(StrategyStatus status, BuyMoreType type) {
        return status == StrategyStatus.ACTIVE
                || status == StrategyStatus.PAUSED
                || (status == StrategyStatus.COMPLETED && type == BuyMoreType.LIMIT);
    }

    private static boolean isExpiredRepositionAllowed(Strategy strategy) {
        return strategy != null
                && strategy.status() == StrategyStatus.FAILED
                && "expired".equals(BrokerOrderStatusUtil.normalize(strategy.latestOrderStatus()));
    }
}
