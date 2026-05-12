package com.neuralarc.ui;

import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.service.StrategyService;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

final class PortfolioActionsController {
    interface Gateway {
        List<ManagedStrategy> strategies();
        StrategyService strategyService();
        StrategyService strategyServiceForMode(StrategyMode mode);
        StrategyService.ArchiveResult archiveStrategy(String strategyId, String reason);
        StrategyService.StrategyCreationResult sellPosition(Strategy strategy);
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
        menu.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(70, 76, 90), 1, true),
                new EmptyBorder(4, 4, 4, 4)
        ));
        menu.add(gateway.createMenuItem("Sell Profitable Positions", "icons/submit.svg",
                () -> handleSellAction(PortfolioActionsSupport.Scope.PROFITABLE)));
        menu.add(gateway.createMenuItem("Sell All Open Positions", "icons/close.svg",
                () -> handleSellAction(PortfolioActionsSupport.Scope.ALL_OPEN)));
        menu.add(gateway.createMenuItem("Sell Losing Positions", "icons/delete.svg",
                () -> handleSellAction(PortfolioActionsSupport.Scope.LOSS_ONLY)));
        menu.addSeparator();
        menu.add(gateway.createMenuItem("Cancel All Pending Limit Buys", "icons/close.svg",
                this::handleCancelAllPendingLimitBuys));
        menu.add(gateway.createMenuItem("Clean All Expired", "icons/delete.svg",
                this::handleCleanAllExpired));
        menu.add(gateway.createMenuItem("Reposition Expired", "icons/submit.svg",
                this::handleRepositionExpired));
        menu.add(gateway.createMenuItem("Remove Inactive List", "icons/delete.svg",
                this::handleRemoveInactiveList));
        menu.add(gateway.createMenuItem("Promote All to Live", "icons/add-stock-strategy.svg",
                this::handlePromoteAllToLive));
        menu.show(anchor, 0, anchor.getHeight());
        gateway.actionCompleted("Portfolio Actions", "Menu opened.");
    }

    private void handleCancelAllPendingLimitBuys() {
        PortfolioActionsSupport.BulkAction action = PortfolioActionsSupport.BulkAction.CANCEL_PENDING_LIMIT_BUYS;
        List<ManagedStrategy> targets = support.filterTargets(gateway.strategies(), action);
        if (!confirmBulkAction(action, targets)) {
            return;
        }

        new SwingWorker<PortfolioActionsSupport.BatchResult, Void>() {
            @Override
            protected PortfolioActionsSupport.BatchResult doInBackground() {
                List<String> successes = new ArrayList<>();
                List<String> failures = new ArrayList<>();
                List<String> skipped = new ArrayList<>();
                if (gateway.strategyService() == null) {
                    for (ManagedStrategy entry : targets) {
                        failures.add(entry.strategy.symbol() + ": strategy service is not configured");
                    }
                    return new PortfolioActionsSupport.BatchResult(successes, failures);
                }
                for (ManagedStrategy entry : targets) {
                    StrategyService modeAwareService = gateway.strategyServiceForMode(entry.strategy.mode());
                    if (modeAwareService == null) {
                        failures.add(entry.strategy.symbol() + ": broker client is not configured for " + entry.strategy.mode().name());
                        continue;
                    }
                    StrategyService.LimitBuyCancelResult result = modeAwareService.cancelPendingLimitBuys(entry.strategy.id());
                    if (result.success()) {
                        if (result.canceledCount() > 0) {
                            successes.add(entry.strategy.symbol() + " (" + result.canceledCount() + ")");
                        } else {
                            skipped.add(entry.strategy.symbol() + ": no pending limit buy orders were cancelable");
                        }
                    } else {
                        failures.add(entry.strategy.symbol() + ": " + result.error());
                    }
                }
                return new PortfolioActionsSupport.BatchResult(successes, failures, skipped);
            }

            @Override
            protected void done() {
                handleBulkActionResult(action, this);
            }
        }.execute();
    }

    private void handlePromoteAllToLive() {
        PortfolioActionsSupport.BulkAction action = PortfolioActionsSupport.BulkAction.PROMOTE_ALL_TO_LIVE;
        List<ManagedStrategy> targets = support.filterTargets(gateway.strategies(), action);
        if (!confirmBulkAction(action, targets)) {
            return;
        }

        new SwingWorker<PortfolioActionsSupport.BatchResult, Void>() {
            @Override
            protected PortfolioActionsSupport.BatchResult doInBackground() {
                List<String> successes = new ArrayList<>();
                List<String> failures = new ArrayList<>();
                if (gateway.strategyService() == null) {
                    for (ManagedStrategy entry : targets) {
                        failures.add(entry.strategy.symbol() + ": strategy service is not configured");
                    }
                    return new PortfolioActionsSupport.BatchResult(successes, failures);
                }
                for (ManagedStrategy entry : targets) {
                    StrategyService.LivePromotionPreview preview = gateway.strategyService().previewLivePromotion(entry.strategy.id());
                    if (!preview.eligible()) {
                        failures.add(entry.strategy.symbol() + ": " + String.join(" ", preview.issues()));
                        continue;
                    }
                    StrategyService.LivePromotionResult result = gateway.strategyService().promotePaperStrategyToLive(entry.strategy.id());
                    if (result.success()) {
                        successes.add(entry.strategy.symbol());
                    } else {
                        failures.add(entry.strategy.symbol() + ": " + result.error());
                    }
                }
                return new PortfolioActionsSupport.BatchResult(successes, failures);
            }

            @Override
            protected void done() {
                handleBulkActionResult(action, this);
            }
        }.execute();
    }

    private void handleRemoveInactiveList() {
        PortfolioActionsSupport.BulkAction action = PortfolioActionsSupport.BulkAction.REMOVE_INACTIVE_LIST;
        List<ManagedStrategy> targets = support.filterTargets(gateway.strategies(), action);
        if (!confirmBulkAction(action, targets)) {
            return;
        }

        new SwingWorker<PortfolioActionsSupport.BatchResult, Void>() {
            @Override
            protected PortfolioActionsSupport.BatchResult doInBackground() {
                List<String> successes = new ArrayList<>();
                List<String> failures = new ArrayList<>();
                for (ManagedStrategy entry : targets) {
                    StrategyService.ArchiveResult result = gateway.archiveStrategy(
                            entry.strategy.id(),
                            "Archived by Remove Inactive List portfolio action"
                    );
                    if (result.success()) {
                        successes.add(entry.strategy.symbol());
                    } else {
                        failures.add(entry.strategy.symbol() + ": " + result.error());
                    }
                }
                return new PortfolioActionsSupport.BatchResult(successes, failures);
            }

            @Override
            protected void done() {
                handleBulkActionResult(action, this);
            }
        }.execute();
    }

    private void handleCleanAllExpired() {
        PortfolioActionsSupport.BulkAction action = PortfolioActionsSupport.BulkAction.CLEAN_ALL_EXPIRED;
        List<ManagedStrategy> targets = support.filterTargets(gateway.strategies(), action);
        if (!confirmBulkAction(action, targets)) {
            return;
        }

        new SwingWorker<PortfolioActionsSupport.BatchResult, Void>() {
            @Override
            protected PortfolioActionsSupport.BatchResult doInBackground() {
                List<String> successes = new ArrayList<>();
                List<String> failures = new ArrayList<>();
                for (ManagedStrategy entry : targets) {
                    StrategyService.ArchiveResult result = gateway.archiveStrategy(
                            entry.strategy.id(),
                            "Archived by Clean All Expired portfolio action"
                    );
                    if (result.success()) {
                        successes.add(entry.strategy.symbol());
                    } else {
                        failures.add(entry.strategy.symbol() + ": " + result.error());
                    }
                }
                return new PortfolioActionsSupport.BatchResult(successes, failures);
            }

            @Override
            protected void done() {
                handleBulkActionResult(action, this);
            }
        }.execute();
    }

    private void handleRepositionExpired() {
        PortfolioActionsSupport.BulkAction action = PortfolioActionsSupport.BulkAction.REPOSITION_EXPIRED;
        List<ManagedStrategy> targets = support.filterTargets(gateway.strategies(), action);
        if (!confirmBulkAction(action, targets)) {
            return;
        }

        new SwingWorker<PortfolioActionsSupport.BatchResult, Void>() {
            @Override
            protected PortfolioActionsSupport.BatchResult doInBackground() {
                List<String> successes = new ArrayList<>();
                List<String> failures = new ArrayList<>();
                for (ManagedStrategy entry : targets) {
                    StrategyService modeAwareService = gateway.strategyServiceForMode(entry.strategy.mode());
                    if (modeAwareService == null) {
                        failures.add(entry.strategy.symbol() + ": broker client is not configured for " + entry.strategy.mode().name());
                        continue;
                    }
                    StrategyService.StrategyCreationResult result = modeAwareService.repositionExpiredStrategy(entry.strategy.id());
                    if (result.success()) {
                        successes.add(entry.strategy.symbol());
                    } else {
                        failures.add(entry.strategy.symbol() + ": " + result.error());
                    }
                }
                return new PortfolioActionsSupport.BatchResult(successes, failures);
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

    private void handleSellAction(PortfolioActionsSupport.Scope scope) {
        List<ManagedStrategy> targets = support.filterTargets(gateway.strategies(), scope);
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
                List<String> successes = new ArrayList<>();
                List<String> failures = new ArrayList<>();
                for (ManagedStrategy entry : targets) {
                    StrategyService.StrategyCreationResult result = gateway.sellPosition(entry.strategy);
                    if (result.success()) {
                        successes.add(entry.strategy.symbol());
                    } else {
                        failures.add(entry.strategy.symbol() + ": " + result.error());
                    }
                }
                return new PortfolioActionsSupport.BatchResult(successes, failures);
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
}
