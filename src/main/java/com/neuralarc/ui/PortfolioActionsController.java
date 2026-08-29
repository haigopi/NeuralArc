package com.neuralarc.ui;

import com.neuralarc.model.SellSubmissionType;
import com.neuralarc.model.ProfitControlMode;
import com.neuralarc.model.ProfitHoldType;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.ThresholdType;
import com.neuralarc.api.AlpacaMarketDataApi;
import com.neuralarc.service.StrategyService;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

final class PortfolioActionsController {
    interface Gateway {
        List<ManagedStrategy> strategies();
        List<ManagedStrategy> currentStrategies();
        List<ManagedStrategy> scopedStrategies();
        StrategyService strategyService();
        StrategyService strategyServiceForMode(StrategyMode mode);
        StrategyMode selectedViewMode();
        StrategyService.ArchiveResult archiveStrategy(String strategyId, String reason);
        StrategyService.ArchiveResult deleteLocalTradeHistoryStrategy(String strategyId);
        StrategyService.ArchiveResult deleteLocalPaperStrategy(String strategyId);
        StrategyService.ArchiveResult deletePendingBaseBuyStrategy(String strategyId);
        StrategyService.StrategyCreationResult sellPosition(
                Strategy strategy,
                SellSubmissionType submissionType,
                StrategyService.SellExecutionSource executionSource
        );
        StrategyService.StrategyCreationResult placeSellTriggerOrder(
                Strategy strategy,
                StrategyService.SellExecutionSource executionSource
        );
        StrategyService.StrategyCreationResult placePendingBaseBuy(Strategy strategy);
        StrategyService.StrategyCreationResult readjustLosingPendingBaseBuy(ManagedStrategy entry);
        Optional<BigDecimal> chooseSellProfitThresholdPercent(List<ManagedStrategy> targets);
        Optional<Strategy> updateStrategy(Strategy strategy);
        Optional<AverageLosingPositionsSelection> chooseAverageLosingPositions(List<ManagedStrategy> targets);
        StrategyService.StrategyCreationResult buyMoreAtMarket(Strategy strategy, int quantity);
        StrategyService.StrategyCreationResult buyMoreAtLimit(Strategy strategy, int quantity, BigDecimal limitPrice);
        ManualPortfolioImportService.ImportResult importManualStocks(List<PortfolioStockImportDialog.ImportedStockDraft> drafts);
        AlpacaMarketDataApi marketDataApiForMode(StrategyMode mode);
        int defaultStrategyPollingSeconds();
        boolean defaultRepeatCycleAfterProfitExitEnabled();
        boolean defaultResubmitOnExpiryEnabled();
        boolean allowDuplicateSymbols();
        String selectedWorkspaceForNewStrategy();
        String selectedModeLabel();
        JMenuItem createMenuItem(String text, String iconPath, Runnable action);
        int confirm(Object message, String title, int optionType, int messageType);
        void showMessage(Object message, String title, int messageType);
        void syncStrategiesFromRepository();
        void refreshStrategyTableData();
        void updateSelectedStrategy();
        void refreshPanels();
        void updateStatusBar();
        void log(String message);
        void actionStarted(String actionName);
        void actionCompleted(String actionName, String detail);
        void actionSkipped(String actionName, String reason);
        void actionCanceled(String actionName);
        void actionFailed(String actionName, String reason);
    }

    private final PortfolioActionsSupport support = new PortfolioActionsSupport();
    private final Gateway gateway;

    PortfolioActionsController(Gateway gateway) {
        this.gateway = gateway;
    }

    void showMenu(AbstractButton anchor) {
        gateway.actionStarted("Portfolio Actions");
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(new Color(46, 49, 60));
        menu.setOpaque(true);
        menu.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(70, 76, 90), 1, true),
                new EmptyBorder(4, 4, 4, 4)
        ));
        menu.add(sectionHeader("Sell Actions"));
        menu.add(gateway.createMenuItem("Sell Profitable Positions", "icons/submit.svg",
                () -> handleSellAction(PortfolioActionsSupport.Scope.PROFITABLE, SellSubmissionType.LIMIT)));
        menu.add(gateway.createMenuItem("Sell All Open Positions", "icons/close.svg",
                () -> handleSellAction(PortfolioActionsSupport.Scope.ALL_OPEN, SellSubmissionType.LIMIT)));
        menu.add(gateway.createMenuItem("Sell Losing Positions", "icons/delete.svg",
                () -> handleSellAction(PortfolioActionsSupport.Scope.LOSS_ONLY, SellSubmissionType.LIMIT)));
        menu.add(gateway.createMenuItem("Position All Sell Triggers", "icons/submit.svg",
                this::handlePositionAllSellTriggers));
        menu.add(gateway.createMenuItem("Sell All Profitable Positions at Market Value", "icons/submit.svg",
                () -> handleSellAction(PortfolioActionsSupport.Scope.PROFITABLE_MARKET, SellSubmissionType.MARKET)));
        menu.add(gateway.createMenuItem("Sell All Losing Positions at Market Value", "icons/delete.svg",
                () -> handleSellAction(PortfolioActionsSupport.Scope.LOSS_ONLY_MARKET, SellSubmissionType.MARKET)));
        menu.add(sectionSeparator());
        menu.add(sectionHeader("Order Placement"));
        menu.add(gateway.createMenuItem("Import Stocks", "icons/add-stock-strategy.svg",
                this::handleImportStocks));
        menu.add(gateway.createMenuItem("Place Limit Buy for All Manual Buy Entries", "icons/submit.svg",
                this::handlePlacePendingBaseBuys));
        menu.add(gateway.createMenuItem("Average Down Losing Positions", "icons/submit.svg",
                this::handleAverageLosingPositions));
        menu.add(gateway.createMenuItem("Place Limit Buy for Losing Pending Positions", "icons/submit.svg",
                () -> handlePlacePendingBaseBuys(PortfolioActionsSupport.BulkAction.PLACE_AMBER_PENDING_BASE_BUYS)));
        menu.add(gateway.createMenuItem("Readjust Losing Pending Base Buy Positions", "icons/submit.svg",
                this::handleReadjustLosingPendingBaseBuys));
        menu.add(gateway.createMenuItem("Position All Sell Profit Threshold percentage", "icons/submit.svg",
                this::handlePositionAllSellProfitThresholdPercentage));
        menu.add(gateway.createMenuItem("Place Limit Buy for Gaining Pending Positions", "icons/submit.svg",
                () -> handlePlacePendingBaseBuys(PortfolioActionsSupport.BulkAction.PLACE_GREEN_PENDING_BASE_BUYS)));
        menu.add(gateway.createMenuItem("Place Limit Buy for All Pending Positions", "icons/submit.svg",
                () -> handlePlacePendingBaseBuys(PortfolioActionsSupport.BulkAction.PLACE_PENDING_BASE_BUYS)));
        menu.add(gateway.createMenuItem("Reposition Expired", "icons/submit.svg",
                this::handleRepositionExpired));
        menu.add(sectionSeparator());
        menu.add(sectionHeader("Order Cleanup"));
        menu.add(gateway.createMenuItem("Clean All Pending Base Buys", "icons/delete.svg",
                this::handleCleanPendingBaseBuys));
        menu.add(gateway.createMenuItem("Cancel all Amber Pending Buys (Losers)", "icons/delete.svg",
                () -> handleCancelColoredPendingBuys(PortfolioActionsSupport.BulkAction.CANCEL_AMBER_PENDING_BUYS)));
        menu.add(gateway.createMenuItem("Cancel all Green Pending Buys (Gainer)", "icons/delete.svg",
                () -> handleCancelColoredPendingBuys(PortfolioActionsSupport.BulkAction.CANCEL_GREEN_PENDING_BUYS)));
        menu.add(gateway.createMenuItem("Cancel All Pending Limit Buys", "icons/close.svg",
                this::handleCancelAllPendingLimitBuys));
        menu.add(gateway.createMenuItem("Cancel All Pending Limit Sells", "icons/close.svg",
                this::handleCancelAllPendingLimitSells));
        menu.add(sectionSeparator());
        menu.add(sectionHeader("Lifecycle"));
        menu.add(gateway.createMenuItem("Resume All", "icons/submit.svg",
                this::handleResumeAll));
        menu.add(gateway.createMenuItem("Clean All Expired", "icons/delete.svg",
                this::handleCleanAllExpired));
        menu.add(gateway.createMenuItem("Clean Invalid Strategies", "icons/delete.svg",
                this::handleCleanInvalidStrategies));
        menu.add(gateway.createMenuItem("Clean Trade History", "icons/delete.svg",
                this::handleCleanTradeHistory));
        JMenuItem deletePaperEntries = gateway.createMenuItem("Delete All Paper Mode Entries", "icons/delete.svg",
                this::handleDeleteAllPaperModeEntries);
        if (gateway.selectedViewMode() == StrategyMode.LIVE) {
            deletePaperEntries.setEnabled(false);
            deletePaperEntries.setToolTipText("Paper cleanup is disabled while viewing LIVE mode.");
        }
        menu.add(deletePaperEntries);
        menu.add(gateway.createMenuItem("Remove Inactive List", "icons/delete.svg",
                this::handleRemoveInactiveList));
        JMenuItem promoteAllToLive = gateway.createMenuItem("Promote All to Live", "icons/add-stock-strategy.svg",
                this::handlePromoteAllToLive);
        if (gateway.selectedViewMode() == StrategyMode.LIVE) {
            promoteAllToLive.setEnabled(false);
            promoteAllToLive.setToolTipText("Promote All to Live is unavailable while viewing LIVE mode.");
        }
        menu.add(promoteAllToLive);
        menu.show(anchor, 0, anchor.getHeight());
        gateway.actionCompleted("Portfolio Actions", "Menu opened.");
    }

    private JMenuItem sectionHeader(String text) {
        JMenuItem header = new JMenuItem(text);
        header.setEnabled(false);
        header.setFont(header.getFont().deriveFont(java.awt.Font.BOLD, 11f));
        header.setOpaque(true);
        header.setBackground(new Color(46, 49, 60));
        header.setForeground(new Color(155, 165, 184));
        header.setBorder(new EmptyBorder(6, 10, 4, 12));
        return header;
    }

    private JMenuItem sectionSeparator() {
        JMenuItem separator = new JMenuItem();
        separator.setEnabled(false);
        separator.setOpaque(true);
        separator.setBackground(new Color(46, 49, 60));
        separator.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(70, 76, 90)));
        separator.setPreferredSize(new java.awt.Dimension(220, 3));
        return separator;
    }

    private void handleCancelAllPendingLimitBuys() {
        PortfolioActionsSupport.BulkAction action = PortfolioActionsSupport.BulkAction.CANCEL_PENDING_LIMIT_BUYS;
        List<ManagedStrategy> targets = support.filterTargets(strategiesFor(action), action);
        if (!confirmBulkAction(action, targets)) {
            return;
        }

        new SwingWorker<PortfolioActionsSupport.BatchResult, Void>() {
            @Override
            protected PortfolioActionsSupport.BatchResult doInBackground() {
                return cancelPendingLimitBuyTargets(targets);
            }

            @Override
            protected void done() {
                handleBulkActionResult(action, this);
            }
        }.execute();
    }

    private void handleCleanPendingBaseBuys() {
        PortfolioActionsSupport.BulkAction action = PortfolioActionsSupport.BulkAction.CLEAN_PENDING_BASE_BUYS;
        List<ManagedStrategy> targets = support.filterTargets(strategiesFor(action), action);
        if (!confirmBulkAction(action, targets)) {
            return;
        }

        gateway.log(action.logPrefix() + " preparing to delete " + targets.size()
                + " pending base-buy recommendation(s).");
        new SwingWorker<PortfolioActionsSupport.BatchResult, Void>() {
            @Override
            protected PortfolioActionsSupport.BatchResult doInBackground() {
                return deletePendingBaseBuyTargets(targets);
            }

            @Override
            protected void done() {
                handleBulkActionResult(action, this);
            }
        }.execute();
    }

    private void handleCancelColoredPendingBuys(PortfolioActionsSupport.BulkAction action) {
        List<ManagedStrategy> targets = support.filterTargets(strategiesFor(action), action);
        if (!confirmBulkAction(action, targets)) {
            return;
        }

        gateway.log(action.logPrefix() + " preparing to remove " + targets.size()
                + " pending buy recommendation(s).");
        new SwingWorker<PortfolioActionsSupport.BatchResult, Void>() {
            @Override
            protected PortfolioActionsSupport.BatchResult doInBackground() {
                return deletePendingBaseBuyTargets(targets, action.logPrefix());
            }

            @Override
            protected void done() {
                handleBulkActionResult(action, this);
            }
        }.execute();
    }

    private void handlePlacePendingBaseBuys() {
        handlePlacePendingBaseBuys(PortfolioActionsSupport.BulkAction.PLACE_PENDING_BASE_BUYS);
    }

    private void handleImportStocks() {
        PortfolioStockImportDialog.ImportSelection selection = PortfolioStockImportDialog.show(null);
        if (selection == null || selection.drafts().isEmpty()) {
            gateway.actionCanceled("Import Stocks");
            return;
        }
        gateway.actionStarted("Import Stocks");
        new SwingWorker<ManualPortfolioImportService.ImportResult, Void>() {
            @Override
            protected ManualPortfolioImportService.ImportResult doInBackground() {
                return gateway.importManualStocks(selection.drafts());
            }

            @Override
            protected void done() {
                try {
                    ManualPortfolioImportService.ImportResult result = get();
                    refreshAfterAction();
                    gateway.actionCompleted("Import Stocks", "Imported=" + result.importedSymbols().size()
                            + ", skipped=" + result.skippedReasons().size() + ".");
                    gateway.showMessage(
                            result.summary(gateway.selectedModeLabel()),
                            "Import Stocks",
                            result.skippedReasons().isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE
                    );
                } catch (Exception ex) {
                    gateway.actionFailed("Import Stocks", ex.getMessage());
                    gateway.showMessage("Failed to import stocks: " + ex.getMessage(),
                            "Import Stocks", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void handleAverageLosingPositions() {
        PortfolioActionsSupport.BulkAction action = PortfolioActionsSupport.BulkAction.AVERAGE_LOSING_POSITIONS;
        List<ManagedStrategy> targets = support.filterTargets(strategiesFor(action), action);
        if (targets.isEmpty()) {
            gateway.actionSkipped(action.menuLabel(), action.emptyMessage());
            gateway.showMessage(action.emptyMessage(), action.dialogTitle(), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Optional<AverageLosingPositionsSelection> selection = gateway.chooseAverageLosingPositions(targets);
        if (selection.isEmpty()) {
            gateway.actionCanceled(action.menuLabel());
            return;
        }
        if (!confirmBulkAction(action, targets)) {
            return;
        }

        gateway.log(action.logPrefix() + " preparing " + targets.size()
                + " losing position(s) for averaging.");
        new SwingWorker<PortfolioActionsSupport.BatchResult, Void>() {
            @Override
            protected PortfolioActionsSupport.BatchResult doInBackground() {
                return averageLosingPositionTargets(targets, selection.get());
            }

            @Override
            protected void done() {
                handleBulkActionResult(action, this);
            }
        }.execute();
    }

    private void handlePlacePendingBaseBuys(PortfolioActionsSupport.BulkAction action) {
        List<ManagedStrategy> targets = support.filterTargets(strategiesFor(action), action);
        if (!confirmBulkAction(action, targets)) {
            return;
        }

        gateway.log(action.logPrefix() + " preparing " + targets.size()
                + " pending base-buy recommendation(s) for placement.");
        new SwingWorker<PortfolioActionsSupport.BatchResult, Void>() {
            @Override
            protected PortfolioActionsSupport.BatchResult doInBackground() {
                return placePendingBaseBuyTargets(targets);
            }

            @Override
            protected void done() {
                handleBulkActionResult(action, this);
            }
        }.execute();
    }

    private void handleReadjustLosingPendingBaseBuys() {
        PortfolioActionsSupport.BulkAction action = PortfolioActionsSupport.BulkAction.READJUST_AMBER_PENDING_BASE_BUYS;
        List<ManagedStrategy> targets = support.filterTargets(strategiesFor(action), action);
        if (!confirmBulkAction(action, targets)) {
            return;
        }

        gateway.log(action.logPrefix() + " preparing to readjust " + targets.size()
                + " losing pending base-buy recommendation(s).");
        new SwingWorker<PortfolioActionsSupport.BatchResult, Void>() {
            @Override
            protected PortfolioActionsSupport.BatchResult doInBackground() {
                return readjustLosingPendingBaseBuyTargets(targets);
            }

            @Override
            protected void done() {
                handleBulkActionResult(action, this);
            }
        }.execute();
    }

    private void handleCancelAllPendingLimitSells() {
        PortfolioActionsSupport.BulkAction action = PortfolioActionsSupport.BulkAction.CANCEL_PENDING_LIMIT_SELLS;
        List<ManagedStrategy> targets = support.filterTargets(strategiesFor(action), action);
        if (!confirmBulkAction(action, targets)) {
            return;
        }

        new SwingWorker<PortfolioActionsSupport.BatchResult, Void>() {
            @Override
            protected PortfolioActionsSupport.BatchResult doInBackground() {
                return cancelPendingLimitSellTargets(targets);
            }

            @Override
            protected void done() {
                handleBulkActionResult(action, this);
            }
        }.execute();
    }

    private void handlePromoteAllToLive() {
        PortfolioActionsSupport.BulkAction action = PortfolioActionsSupport.BulkAction.PROMOTE_ALL_TO_LIVE;
        if (gateway.selectedViewMode() == StrategyMode.LIVE) {
            String reason = "Promote All to Live is disabled while viewing LIVE mode.";
            gateway.actionSkipped(action.menuLabel(), reason);
            gateway.showMessage(reason, action.dialogTitle(), JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<ManagedStrategy> targets = support.filterTargets(strategiesFor(action), action);
        if (!confirmBulkAction(action, targets)) {
            return;
        }

        new SwingWorker<PortfolioActionsSupport.BatchResult, Void>() {
            @Override
            protected PortfolioActionsSupport.BatchResult doInBackground() {
                return promoteAllToLiveTargets(targets);
            }

            @Override
            protected void done() {
                handleBulkActionResult(action, this);
            }
        }.execute();
    }

    private void handlePositionAllSellTriggers() {
        PortfolioActionsSupport.Scope scope = PortfolioActionsSupport.Scope.POSITION_ALL_SELL_TRIGGERS;
        List<ManagedStrategy> targets = support.filterTargets(gateway.currentStrategies(), scope);
        if (targets.isEmpty()) {
            gateway.actionSkipped(scope.menuLabel(), scope.emptyMessage());
            gateway.showMessage(scope.emptyMessage(), scope.dialogTitle(), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int choice = gateway.confirm(
                support.buildConfirmationMessage(scope, targets),
                scope.dialogTitle(),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (choice != JOptionPane.YES_OPTION) {
            gateway.actionCanceled(scope.menuLabel());
            return;
        }
        new SwingWorker<PortfolioActionsSupport.BatchResult, Void>() {
            @Override
            protected PortfolioActionsSupport.BatchResult doInBackground() {
                return placeSellTriggerTargets(targets);
            }

            @Override
            protected void done() {
                handleSellActionResult(scope, this);
            }
        }.execute();
    }

    private void handlePositionAllSellProfitThresholdPercentage() {
        PortfolioActionsSupport.BulkAction action = PortfolioActionsSupport.BulkAction.POSITION_SELL_PROFIT_THRESHOLD_PERCENTAGE;
        List<ManagedStrategy> targets = support.filterTargets(gateway.currentStrategies(), action);
        if (targets.isEmpty()) {
            gateway.actionSkipped(action.menuLabel(), action.emptyMessage());
            gateway.showMessage(action.emptyMessage(), action.dialogTitle(), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Optional<BigDecimal> trailingPercent = gateway.chooseSellProfitThresholdPercent(targets);
        if (trailingPercent.isEmpty()) {
            gateway.actionCanceled(action.menuLabel());
            return;
        }
        int choice = gateway.confirm(
                support.buildConfirmationMessage(action, targets)
                        .replace("</body></html>",
                                "<br><br><b>Trailing pullback:</b> "
                                        + trailingPercent.get().toPlainString()
                                        + "%</body></html>"),
                action.dialogTitle(),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (choice != JOptionPane.YES_OPTION) {
            gateway.actionCanceled(action.menuLabel());
            return;
        }
        new SwingWorker<PortfolioActionsSupport.BatchResult, Void>() {
            @Override
            protected PortfolioActionsSupport.BatchResult doInBackground() {
                return positionSellProfitThresholdTargets(targets, trailingPercent.get());
            }

            @Override
            protected void done() {
                handleBulkActionResult(action, this);
            }
        }.execute();
    }

    private void handleResumeAll() {
        PortfolioActionsSupport.BulkAction action = PortfolioActionsSupport.BulkAction.RESUME_ALL;
        List<ManagedStrategy> targets = support.filterTargets(strategiesFor(action), action);
        if (!confirmBulkAction(action, targets)) {
            return;
        }

        new SwingWorker<PortfolioActionsSupport.BatchResult, Void>() {
            @Override
            protected PortfolioActionsSupport.BatchResult doInBackground() {
                return resumeTargets(targets);
            }

            @Override
            protected void done() {
                handleBulkActionResult(action, this);
            }
        }.execute();
    }

    private void handleRemoveInactiveList() {
        PortfolioActionsSupport.BulkAction action = PortfolioActionsSupport.BulkAction.REMOVE_INACTIVE_LIST;
        List<ManagedStrategy> targets = support.filterTargets(strategiesFor(action), action);
        if (!confirmBulkAction(action, targets)) {
            return;
        }

        new SwingWorker<PortfolioActionsSupport.BatchResult, Void>() {
            @Override
            protected PortfolioActionsSupport.BatchResult doInBackground() {
                return archiveTargets(targets, "Archived by Remove Inactive List portfolio action");
            }

            @Override
            protected void done() {
                handleBulkActionResult(action, this);
            }
        }.execute();
    }

    private void handleCleanAllExpired() {
        PortfolioActionsSupport.BulkAction action = PortfolioActionsSupport.BulkAction.CLEAN_ALL_EXPIRED;
        List<ManagedStrategy> targets = support.filterTargets(strategiesFor(action), action);
        if (!confirmBulkAction(action, targets)) {
            return;
        }

        new SwingWorker<PortfolioActionsSupport.BatchResult, Void>() {
            @Override
            protected PortfolioActionsSupport.BatchResult doInBackground() {
                return archiveTargets(targets, "Archived by Clean All Expired portfolio action");
            }

            @Override
            protected void done() {
                handleBulkActionResult(action, this);
            }
        }.execute();
    }

    private void handleCleanInvalidStrategies() {
        PortfolioActionsSupport.BulkAction action = PortfolioActionsSupport.BulkAction.CLEAN_INVALID;
        List<ManagedStrategy> targets = support.filterTargets(strategiesFor(action), action);
        if (!confirmBulkAction(action, targets)) {
            return;
        }

        new SwingWorker<PortfolioActionsSupport.BatchResult, Void>() {
            @Override
            protected PortfolioActionsSupport.BatchResult doInBackground() {
                return deleteLocalTradeHistoryTargets(targets);
            }

            @Override
            protected void done() {
                handleBulkActionResult(action, this);
            }
        }.execute();
    }

    private void handleDeleteAllPaperModeEntries() {
        PortfolioActionsSupport.BulkAction action = PortfolioActionsSupport.BulkAction.DELETE_ALL_PAPER_MODE_ENTRIES;
        if (gateway.selectedViewMode() == StrategyMode.LIVE) {
            String reason = "Paper cleanup is disabled while viewing LIVE mode.";
            gateway.actionSkipped(action.menuLabel(), reason);
            gateway.showMessage(reason, action.dialogTitle(), JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<ManagedStrategy> targets = support.filterTargets(gateway.scopedStrategies(), action);
        if (!confirmBulkAction(action, targets)) {
            return;
        }

        new SwingWorker<PortfolioActionsSupport.BatchResult, Void>() {
            @Override
            protected PortfolioActionsSupport.BatchResult doInBackground() {
                return deleteAllPaperModeTargets(targets);
            }

            @Override
            protected void done() {
                handleBulkActionResult(action, this);
            }
        }.execute();
    }

    private void handleRepositionExpired() {
        PortfolioActionsSupport.BulkAction action = PortfolioActionsSupport.BulkAction.REPOSITION_EXPIRED;
        List<ManagedStrategy> targets = support.filterTargets(strategiesFor(action), action);
        if (!confirmBulkAction(action, targets)) {
            return;
        }

        new SwingWorker<PortfolioActionsSupport.BatchResult, Void>() {
            @Override
            protected PortfolioActionsSupport.BatchResult doInBackground() {
                return repositionExpiredTargets(targets);
            }

            @Override
            protected void done() {
                handleBulkActionResult(action, this);
            }
        }.execute();
    }

    PortfolioActionsSupport.BatchResult repositionExpiredTargets(List<ManagedStrategy> targets) {
        return runTargetsInParallel(targets, this::repositionExpiredTarget);
    }

    PortfolioActionsSupport.BatchResult sellTargets(List<ManagedStrategy> targets, SellSubmissionType submissionType) {
        return runTargetsInParallel(targets, entry -> {
            StrategyService.StrategyCreationResult result = gateway.sellPosition(
                    entry.strategy,
                    submissionType,
                    StrategyService.SellExecutionSource.PORTFOLIO_ACTION
            );
            return result.success()
                    ? TargetResult.success(entry.strategy.symbol())
                    : TargetResult.failure(entry.strategy.symbol() + ": " + result.error());
        });
    }

    PortfolioActionsSupport.BatchResult placeSellTriggerTargets(List<ManagedStrategy> targets) {
        return runTargetsInParallel(targets, entry -> {
            StrategyService.StrategyCreationResult result = gateway.placeSellTriggerOrder(
                    entry.strategy,
                    StrategyService.SellExecutionSource.PORTFOLIO_ACTION
            );
            return result.success()
                    ? TargetResult.success(entry.strategy.symbol())
                    : TargetResult.failure(entry.strategy.symbol() + ": " + result.error());
        });
    }

    PortfolioActionsSupport.BatchResult positionSellProfitThresholdTargets(
            List<ManagedStrategy> targets,
            BigDecimal trailingPercent
    ) {
        return runTargetsInParallel(targets, entry -> {
            Strategy strategy = entry.strategy;
            StrategyService modeAwareService = modeAwareService(entry);
            if (modeAwareService == null) {
                return missingBrokerService(entry);
            }
            BigDecimal baseBuy = strategy.baseBuyLimitPrice();
            BigDecimal sellTrigger = strategy.targetSellPrice();
            if (baseBuy == null || baseBuy.compareTo(BigDecimal.ZERO) <= 0
                    || sellTrigger == null || sellTrigger.compareTo(BigDecimal.ZERO) <= 0) {
                return TargetResult.skipped(strategy.symbol() + ": missing valid base or sell trigger price");
            }
            BigDecimal activationAmount = sellTrigger.subtract(baseBuy);
            if (activationAmount.compareTo(BigDecimal.ZERO) <= 0) {
                return TargetResult.skipped(strategy.symbol() + ": sell trigger must be above base buy to map threshold");
            }

            strategy.setProfitControlMode(ProfitControlMode.PROFIT_HOLD);
            strategy.setAutomaticStopSellThresholdType(ThresholdType.FIXED_AMOUNT);
            strategy.setAutomaticStopSellThreshold(activationAmount);
            strategy.setProfitHoldEnabled(true);
            strategy.setProfitHoldType(ProfitHoldType.PERCENT_TRAILING);
            strategy.setProfitHoldPercent(trailingPercent);
            strategy.setAlpacaTrailingStopEnabled(false);
            strategy.setHighestObservedPriceAfterTarget(BigDecimal.ZERO);
            strategy.setCurrentState(StrategyLifecycleState.BASE_BUY_FILLED);
            strategy.setLatestOrderStatus("");
            strategy.setLatestAlpacaOrderId("");
            strategy.setLastTriggeredRuleType("PROFIT_HOLD");
            strategy.setLastEvent("Portfolio action converted sell trigger to profit threshold with trailing "
                    + trailingPercent.toPlainString() + "%");

            Optional<Strategy> updated = gateway.updateStrategy(strategy);
            if (updated.isEmpty()) {
                return TargetResult.failure(strategy.symbol() + ": failed to update strategy");
            }
            return TargetResult.success(strategy.symbol());
        });
    }

    PortfolioActionsSupport.BatchResult cancelPendingLimitBuyTargets(List<ManagedStrategy> targets) {
        return runTargetsInParallel(targets, entry -> {
            StrategyService modeAwareService = modeAwareService(entry);
            if (modeAwareService == null) {
                return missingBrokerService(entry);
            }
            StrategyService.LimitBuyCancelResult result = modeAwareService.cancelPendingLimitBuys(entry.strategy.id());
            if (!result.success()) {
                return TargetResult.failure(entry.strategy.symbol() + ": " + result.error());
            }
            return result.canceledCount() > 0
                    ? TargetResult.success(entry.strategy.symbol() + " (" + result.canceledCount() + ")")
                    : TargetResult.skipped(entry.strategy.symbol() + ": no pending limit buy orders were cancelable");
        });
    }

    PortfolioActionsSupport.BatchResult placePendingBaseBuyTargets(List<ManagedStrategy> targets) {
        return runTargetsInParallel(targets, entry -> {
            gateway.log("[Place Pending Base Buys] evaluating " + entry.strategy.symbol()
                    + " for pending base-buy placement.");
            if (gateway.strategyServiceForMode(entry.strategy.mode()) == null) {
                return missingBrokerService(entry);
            }
            StrategyService.StrategyCreationResult result = gateway.placePendingBaseBuy(entry.strategy);
            if (result.success()) {
                gateway.log("[Place Pending Base Buys] placed " + entry.strategy.symbol()
                        + " clientOrderId=" + result.clientOrderId() + ".");
                return TargetResult.success(entry.strategy.symbol());
            }
            gateway.log("[Place Pending Base Buys] failed " + entry.strategy.symbol() + ": " + result.error());
            return TargetResult.failure(entry.strategy.symbol() + ": " + result.error());
        });
    }

    PortfolioActionsSupport.BatchResult readjustLosingPendingBaseBuyTargets(List<ManagedStrategy> targets) {
        return runTargetsInParallel(targets, entry -> {
            gateway.log("[Readjust Losing Pending Base Buy Positions] evaluating " + entry.strategy.symbol()
                    + " for base-buy readjustment.");
            StrategyService.StrategyCreationResult result = gateway.readjustLosingPendingBaseBuy(entry);
            if (result.success()) {
                gateway.log("[Readjust Losing Pending Base Buy Positions] readjusted " + entry.strategy.symbol() + ".");
                return TargetResult.success(entry.strategy.symbol());
            }
            gateway.log("[Readjust Losing Pending Base Buy Positions] failed " + entry.strategy.symbol()
                    + ": " + result.error());
            return TargetResult.failure(entry.strategy.symbol() + ": " + result.error());
        });
    }

    PortfolioActionsSupport.BatchResult averageLosingPositionTargets(
            List<ManagedStrategy> targets,
            AverageLosingPositionsSelection selection
    ) {
        return runTargetsInParallel(targets, entry -> {
            int quantity = averageBuyQuantity(entry, selection);
            if (quantity <= 0) {
                return TargetResult.skipped(entry.strategy.symbol() + ": quantity must be greater than zero");
            }
            StrategyService.StrategyCreationResult result;
            if (selection.orderType() == AverageLosingPositionsSelection.OrderType.MARKET) {
                result = gateway.buyMoreAtMarket(entry.strategy, quantity);
            } else {
                BigDecimal limitPrice = averageLimitPrice(entry, selection.limitDiscountPercent());
                if (limitPrice.compareTo(BigDecimal.ZERO) <= 0) {
                    return TargetResult.skipped(entry.strategy.symbol() + ": current market price is unavailable");
                }
                result = gateway.buyMoreAtLimit(entry.strategy, quantity, limitPrice);
            }
            return result.success()
                    ? TargetResult.success(entry.strategy.symbol() + " (" + quantity + ")")
                    : TargetResult.failure(entry.strategy.symbol() + ": " + result.error());
        });
    }

    PortfolioActionsSupport.BatchResult cancelPendingLimitSellTargets(List<ManagedStrategy> targets) {
        return runTargetsInParallel(targets, entry -> {
            StrategyService modeAwareService = modeAwareService(entry);
            if (modeAwareService == null) {
                return missingBrokerService(entry);
            }
            StrategyService.LimitSellCancelResult result = modeAwareService.cancelPendingLimitSells(entry.strategy.id());
            if (!result.success()) {
                return TargetResult.failure(entry.strategy.symbol() + ": " + result.error());
            }
            return result.canceledCount() > 0
                    ? TargetResult.success(entry.strategy.symbol() + " (" + result.canceledCount() + ")")
                    : TargetResult.skipped(entry.strategy.symbol() + ": no pending limit sell orders were cancelable");
        });
    }

    PortfolioActionsSupport.BatchResult promoteAllToLiveTargets(List<ManagedStrategy> targets) {
        return runTargetsInParallel(targets, entry -> {
            StrategyService liveService = gateway.strategyServiceForMode(StrategyMode.LIVE);
            if (liveService == null) {
                return TargetResult.failure(entry.strategy.symbol() + ": LIVE strategy service is not configured");
            }
            StrategyService.LivePromotionPreview preview = liveService.previewLivePromotion(entry.strategy.id());
            if (!preview.eligible()) {
                return TargetResult.failure(entry.strategy.symbol() + ": " + String.join(" ", preview.issues()));
            }
            StrategyService.LivePromotionResult result = liveService.promotePaperStrategyToLive(entry.strategy.id());
            return result.success()
                    ? TargetResult.success(entry.strategy.symbol())
                    : TargetResult.failure(entry.strategy.symbol() + ": " + result.error());
        });
    }

    PortfolioActionsSupport.BatchResult resumeTargets(List<ManagedStrategy> targets) {
        return runTargetsInParallel(targets, entry -> {
            StrategyService modeAwareService = modeAwareService(entry);
            if (modeAwareService == null) {
                return missingBrokerService(entry);
            }
            modeAwareService.resume(entry.strategy.id());
            return TargetResult.success(entry.strategy.symbol());
        });
    }

    PortfolioActionsSupport.BatchResult archiveTargets(List<ManagedStrategy> targets, String reason) {
        return runTargetsInParallel(targets, entry -> {
            StrategyService.ArchiveResult result = gateway.archiveStrategy(entry.strategy.id(), reason);
            return result.success()
                    ? TargetResult.success(entry.strategy.symbol())
                    : TargetResult.failure(entry.strategy.symbol() + ": " + result.error());
        });
    }

    PortfolioActionsSupport.BatchResult deleteLocalTradeHistoryTargets(List<ManagedStrategy> targets) {
        return runTargetsInParallel(targets, entry -> {
            StrategyService.ArchiveResult result = gateway.deleteLocalTradeHistoryStrategy(entry.strategy.id());
            return result.success()
                    ? TargetResult.success(entry.strategy.symbol())
                    : TargetResult.failure(entry.strategy.symbol() + ": " + result.error());
        });
    }

    PortfolioActionsSupport.BatchResult deleteAllPaperModeTargets(List<ManagedStrategy> targets) {
        if (gateway.selectedViewMode() == StrategyMode.LIVE) {
            return new PortfolioActionsSupport.BatchResult(
                    List.of(),
                    List.of("Paper cleanup blocked because the current view mode is LIVE.")
            );
        }
        return runTargetsInParallel(targets, entry -> {
            if (entry.strategy.mode() != StrategyMode.PAPER) {
                return TargetResult.skipped(entry.strategy.symbol() + ": skipped non-PAPER strategy");
            }
            StrategyService.ArchiveResult result = gateway.deleteLocalPaperStrategy(entry.strategy.id());
            return result.success()
                    ? TargetResult.success(entry.strategy.symbol())
                    : TargetResult.failure(entry.strategy.symbol() + ": " + result.error());
        });
    }

    PortfolioActionsSupport.BatchResult deletePendingBaseBuyTargets(List<ManagedStrategy> targets) {
        return deletePendingBaseBuyTargets(targets, "[Clean Pending Base Buys]");
    }

    private PortfolioActionsSupport.BatchResult deletePendingBaseBuyTargets(
            List<ManagedStrategy> targets,
            String logPrefix
    ) {
        return runTargetsInParallel(targets, entry -> {
            gateway.log(logPrefix + " deleting pending recommendation for " + entry.strategy.symbol() + ".");
            StrategyService.ArchiveResult result = gateway.deletePendingBaseBuyStrategy(entry.strategy.id());
            if (result.success()) {
                gateway.log(logPrefix + " deleted " + entry.strategy.symbol() + ".");
                return TargetResult.success(entry.strategy.symbol());
            }
            gateway.log(logPrefix + " failed " + entry.strategy.symbol() + ": " + result.error());
            return TargetResult.failure(entry.strategy.symbol() + ": " + result.error());
        });
    }

    private PortfolioActionsSupport.BatchResult runTargetsInParallel(
            List<ManagedStrategy> targets,
            TargetOperation operation
    ) {
        if (targets == null || targets.isEmpty()) {
            return new PortfolioActionsSupport.BatchResult(List.of(), List.of());
        }
        int threadCount = parallelThreadCount(targets.size());
        ExecutorService executor = Executors.newFixedThreadPool(threadCount, runnable -> {
            Thread thread = new Thread(runnable, "neuralarc-portfolio-action");
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<CompletableFuture<TargetResult>> futures = targets.stream()
                    .map(entry -> CompletableFuture.supplyAsync(() -> runTarget(entry, operation), executor))
                    .toList();
            List<String> successes = new ArrayList<>();
            List<String> failures = new ArrayList<>();
            List<String> skipped = new ArrayList<>();
            for (CompletableFuture<TargetResult> future : futures) {
                TargetResult result = future.join();
                switch (result.status()) {
                    case SUCCESS -> successes.add(result.message());
                    case FAILURE -> failures.add(result.message());
                    case SKIPPED -> skipped.add(result.message());
                }
            }
            return new PortfolioActionsSupport.BatchResult(successes, failures, skipped);
        } finally {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    gateway.log("[Portfolio Actions] Timed out waiting for parallel workers to stop.");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private TargetResult runTarget(ManagedStrategy entry, TargetOperation operation) {
        try {
            return operation.apply(entry);
        } catch (Exception ex) {
            return TargetResult.failure(entry.strategy.symbol() + ": " + ex.getMessage());
        }
    }

    private TargetResult repositionExpiredTarget(ManagedStrategy entry) {
        StrategyService modeAwareService = modeAwareService(entry);
        if (modeAwareService == null) {
            return missingBrokerService(entry);
        }
        StrategyService.StrategyCreationResult result = modeAwareService.repositionExpiredStrategy(entry.strategy.id());
        return result.success()
                ? TargetResult.success(entry.strategy.symbol())
                : TargetResult.failure(entry.strategy.symbol() + ": " + result.error());
    }

    private StrategyService modeAwareService(ManagedStrategy entry) {
        return gateway.strategyServiceForMode(entry.strategy.mode());
    }

    private int averageBuyQuantity(ManagedStrategy entry, AverageLosingPositionsSelection selection) {
        if (selection.quantityMode() == AverageLosingPositionsSelection.QuantityMode.FIXED_INPUT_QUANTITY) {
            return selection.quantity();
        }
        return entry.cachedPosition().getTotalShares();
    }

    private BigDecimal averageLimitPrice(ManagedStrategy entry, BigDecimal discountPercent) {
        BigDecimal currentPrice = entry.cachedPosition().getLastPrice();
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal discount = discountPercent == null ? BigDecimal.ZERO : discountPercent;
        BigDecimal multiplier = BigDecimal.ONE.subtract(discount.divide(new BigDecimal("100"), 8, java.math.RoundingMode.HALF_UP));
        return com.neuralarc.util.Monetary.round(currentPrice.multiply(multiplier));
    }

    private TargetResult missingBrokerService(ManagedStrategy entry) {
        return TargetResult.failure(entry.strategy.symbol() + ": broker client is not configured for "
                + entry.strategy.mode().name());
    }

    private int parallelThreadCount(int targetCount) {
        return Math.min(targetCount, Math.max(2, Math.min(6, Runtime.getRuntime().availableProcessors())));
    }

    private void handleCleanTradeHistory() {
        PortfolioActionsSupport.BulkAction action = PortfolioActionsSupport.BulkAction.CLEAN_TRADE_HISTORY;
        List<ManagedStrategy> targets = support.filterTargets(strategiesFor(action), action);
        if (!confirmBulkAction(action, targets)) {
            return;
        }

        new SwingWorker<PortfolioActionsSupport.BatchResult, Void>() {
            @Override
            protected PortfolioActionsSupport.BatchResult doInBackground() {
                return deleteLocalTradeHistoryTargets(targets);
            }

            @Override
            protected void done() {
                handleBulkActionResult(action, this);
            }
        }.execute();
    }

    private boolean confirmBulkAction(PortfolioActionsSupport.BulkAction action, List<ManagedStrategy> targets) {
        if (targets.isEmpty()) {
            gateway.actionSkipped(action.menuLabel(), action.emptyMessage());
            gateway.showMessage(action.emptyMessage(), action.dialogTitle(), JOptionPane.INFORMATION_MESSAGE);
            return false;
        }
        int choice = gateway.confirm(
                support.buildConfirmationMessage(action, targets),
                action.dialogTitle(),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        boolean confirmed = choice == JOptionPane.YES_OPTION;
        if (!confirmed) {
            gateway.actionCanceled(action.menuLabel());
        }
        return confirmed;
    }

    private void handleBulkActionResult(
            PortfolioActionsSupport.BulkAction action,
            SwingWorker<PortfolioActionsSupport.BatchResult, Void> worker
    ) {
        try {
            PortfolioActionsSupport.BatchResult result = worker.get();
            refreshAfterAction();
            gateway.log(action.logPrefix() + " completed for " + result.successes().size() + " strategy(ies).");
            if (!result.failures().isEmpty()) {
                gateway.log(action.logPrefix() + " failures: " + String.join(" | ", result.failures()));
            }
            gateway.actionCompleted(action.menuLabel(), "Succeeded=" + result.successes().size()
                    + ", failed=" + result.failures().size() + ".");
            gateway.showMessage(
                    support.buildResultMessage(action, result),
                    action.dialogTitle(),
                    result.failures().isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE
            );
        } catch (Exception ex) {
            gateway.actionFailed(action.menuLabel(), ex.getMessage());
            gateway.showMessage(
                    "Failed to complete portfolio action: " + ex.getMessage(),
                    action.dialogTitle(),
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void handleSellAction(PortfolioActionsSupport.Scope scope, SellSubmissionType submissionType) {
        List<ManagedStrategy> targets = support.filterTargets(gateway.currentStrategies(), scope);
        if (targets.isEmpty()) {
            gateway.actionSkipped(scope.menuLabel(), scope.emptyMessage());
            gateway.showMessage(scope.emptyMessage(), scope.dialogTitle(), JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int choice = gateway.confirm(
                support.buildConfirmationMessage(scope, targets),
                scope.dialogTitle(),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (choice != JOptionPane.YES_OPTION) {
            gateway.actionCanceled(scope.menuLabel());
            return;
        }

        new SwingWorker<PortfolioActionsSupport.BatchResult, Void>() {
            @Override
            protected PortfolioActionsSupport.BatchResult doInBackground() {
                return sellTargets(targets, submissionType);
            }

            @Override
            protected void done() {
                handleSellActionResult(scope, this);
            }
        }.execute();
    }

    private void handleSellActionResult(
            PortfolioActionsSupport.Scope scope,
            SwingWorker<PortfolioActionsSupport.BatchResult, Void> worker
    ) {
        try {
            PortfolioActionsSupport.BatchResult result = worker.get();
            refreshAfterAction();
            gateway.log(scope.logPrefix() + " submitted for " + result.successes().size() + " strategy(ies).");
            if (!result.failures().isEmpty()) {
                gateway.log(scope.logPrefix() + " failures: " + String.join(" | ", result.failures()));
            }
            gateway.actionCompleted(scope.menuLabel(), "Submitted=" + result.successes().size()
                    + ", failed=" + result.failures().size() + ".");
            gateway.showMessage(
                    support.buildResultMessage(scope, result),
                    scope.dialogTitle(),
                    result.failures().isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE
            );
        } catch (Exception ex) {
            gateway.actionFailed(scope.menuLabel(), ex.getMessage());
            gateway.showMessage(
                    "Failed to submit the requested sell orders: " + ex.getMessage(),
                    scope.dialogTitle(),
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void refreshAfterAction() {
        gateway.syncStrategiesFromRepository();
        gateway.refreshStrategyTableData();
        gateway.updateSelectedStrategy();
        gateway.refreshPanels();
        gateway.updateStatusBar();
    }

    private List<ManagedStrategy> strategiesFor(PortfolioActionsSupport.BulkAction action) {
        if (action == PortfolioActionsSupport.BulkAction.CLEAN_TRADE_HISTORY) {
            return gateway.scopedStrategies();
        }
        return gateway.currentStrategies();
    }

    @FunctionalInterface
    private interface TargetOperation {
        TargetResult apply(ManagedStrategy entry) throws Exception;
    }

    private enum TargetStatus {
        SUCCESS,
        FAILURE,
        SKIPPED
    }

    private record TargetResult(TargetStatus status, String message) {
        static TargetResult success(String message) {
            return new TargetResult(TargetStatus.SUCCESS, message);
        }

        static TargetResult failure(String message) {
            return new TargetResult(TargetStatus.FAILURE, message);
        }

        static TargetResult skipped(String message) {
            return new TargetResult(TargetStatus.SKIPPED, message);
        }
    }
}
