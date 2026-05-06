package com.neuralarc.ui;

import com.neuralarc.analytics.AnalyticsEvent;
import com.neuralarc.model.BrokerType;
import com.neuralarc.model.Position;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyStatus;
import com.neuralarc.service.StrategyService;
import com.neuralarc.util.Monetary;

import javax.swing.JOptionPane;
import java.math.BigDecimal;
import java.util.Optional;

public final class StrategyActionsController {
    private final Gateway gateway;

    public StrategyActionsController(Gateway gateway) {
        this.gateway = gateway;
    }

    public void togglePauseResume(int viewRow) {
        int row = gateway.toModelRow(viewRow);
        if (row < 0 || row >= gateway.strategiesSize()) {
            return;
        }

        ActionEntry entry = gateway.entryAt(row);
        if (entry.strategy().status() == StrategyStatus.ARCHIVED || entry.isPauseResumeBusy()) {
            return;
        }

        boolean wasPaused = entry.isPaused();
        String strategyId = entry.strategy().id();
        String symbol = entry.strategy().symbol();
        entry.setPauseResumeBusy(true);
        entry.setPauseResumeBusyText(wasPaused ? "Resuming..." : "Canceling...");
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
                        gateway.log("Strategy resumed for symbol " + symbol);
                    } else {
                        gateway.stopPollingCountdown(strategyId);
                        gateway.log("Strategy canceled for symbol " + symbol);
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
                ex -> gateway.log("Cancel/Resume failed for symbol " + symbol + ": " + ex.getMessage()),
                () -> {
                    entry.setPauseResumeBusy(false);
                    entry.setPauseResumeBusyText("");
                    gateway.refreshStrategyTableRow(row);
                    gateway.updateStatusBar();
                    gateway.refreshPanels();
                }
        );
    }

    public void previewLivePromotion(int viewRow) {
        int row = gateway.toModelRow(viewRow);
        if (row < 0 || row >= gateway.strategiesSize()) {
            return;
        }

        ActionEntry entry = gateway.entryAt(row);
        if (entry.strategy().mode() != StrategyMode.PAPER || entry.strategy().status() == StrategyStatus.ARCHIVED) {
            return;
        }

        StrategyService.LivePromotionPreview preview = gateway.strategyService().previewLivePromotion(entry.strategy().id());
        Position paperPosition = gateway.loadPositionForStrategy(entry.strategy());
        String realizedPnl = Monetary.round(gateway.realizedPnlForStrategy(entry.strategy().id())).toPlainString();
        String unrealizedPnl = Monetary.round(paperPosition.unrealizedPnl()).toPlainString();
        PromotionDialogResult dialogResult = gateway.showLivePromotionDialog(preview, realizedPnl, unrealizedPnl);
        if (!dialogResult.proceed()) {
            return;
        }

        StrategyService.LivePromotionResult result = gateway.strategyService().promotePaperStrategyToLive(entry.strategy().id());
        if (!result.success()) {
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
        int row = gateway.toModelRow(viewRow);
        if (row < 0 || row >= gateway.strategiesSize()) {
            return;
        }

        ActionEntry entry = gateway.entryAt(row);
        Strategy strategy = entry.strategy();
        if (strategy.status() == StrategyStatus.ARCHIVED || !gateway.hasOpenPosition(strategy)) {
            return;
        }

        String restartMessage = strategy.restartAfterExitEnabled()
                ? "After the position fully closes, the strategy can re-initiate its cycle automatically."
                : "After the position fully closes, the strategy will remain completed unless you restart it manually.";
        String message = "<html><body style='width:340px'>"
                + "<b>Sell the current " + strategy.symbol() + " position now?</b><br><br>"
                + "This submits a manual limit sell on Alpaca using the latest broker price.<br><br>"
                + restartMessage
                + "</body></html>";
        int choice = gateway.confirm(
                message,
                "Sell Position — " + strategy.symbol(),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        gateway.runBackgroundTask(
                () -> {
                    StrategyService.StrategyCreationResult result = gateway.sellPosition(strategy);
                    if (!result.success()) {
                        throw new IllegalStateException(result.error());
                    }
                },
                () -> {
                    gateway.findStrategyById(strategy.id()).ifPresent(entry::syncFrom);
                    gateway.log("Manual sell order submitted for symbol " + strategy.symbol());
                },
                ex -> gateway.showMessage(
                        "Failed to submit sell order for " + strategy.symbol() + ": " + ex.getMessage(),
                        "Sell Failed",
                        JOptionPane.ERROR_MESSAGE
                ),
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
                + "This will immediately stop polling and permanently remove the strategy from saved data.<br>"
                + "This action <b>cannot be undone</b>."
                + "</body></html>";
        int choice = gateway.confirm(
                message,
                "Delete Strategy — " + strategy.symbol(),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        BigDecimal realizedAtDeletion = gateway.realizedPnlForStrategy(strategy.id());
        gateway.addArchivedRealized(strategy.mode(), realizedAtDeletion);
        gateway.strategyService().delete(strategy.id());
        gateway.removeStrategyAt(row);
        gateway.log("Deleted strategy for symbol " + strategy.symbol());
        gateway.publishAnalytics(new AnalyticsEvent("STRATEGY_DELETED").put("symbol", strategy.symbol()));

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
        StrategyService strategyService();
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
        StrategyService.StrategyCreationResult sellPosition(Strategy strategy);
        BigDecimal realizedPnlForStrategy(String strategyId);
        String closePaperAccountState(Strategy strategy);

        void updateHeaderModeStatus(BrokerType brokerType);
        BrokerType currentBrokerType();
        boolean hasBrokerPositionAccess();

        void setSelectedStrategyId(String strategyId);
        String selectedStrategyId();
        void removeStrategyAt(int modelRow);
        void addArchivedRealized(StrategyMode mode, BigDecimal amount);

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

    public record PromotionDialogResult(boolean proceed, boolean closePaperPositions) {
    }
}
