package com.neuralarc.ui;

import com.neuralarc.api.HttpAlpacaClient;
import com.neuralarc.model.ApplicationMode;
import com.neuralarc.model.BrokerType;
import com.neuralarc.model.Position;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyStatus;
import com.neuralarc.service.StrategyRepository;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

final class PortfolioRefreshController {
    private static final String REFRESH_ACTION_NAME = "Refresh & Reevaluate Portfolio";

    interface Gateway {
        boolean isConnected();
        BrokerType brokerType();
        HttpAlpacaClient alpacaClientForMode(ApplicationMode mode);
        void onRefreshStarted();
        void onRefreshFinished();
        void syncStrategies(List<Strategy> strategies);
        void applyPositionSnapshots(Map<String, Position> snapshots);
        void refreshStrategyTableContent();
        void refreshPanels();
        void updateStatusBar();
        void log(String message);
        void showConnectionRequired();
        void showRefreshFailed(String message);
    }

    private final StrategyRepository strategyRepository;
    private final ExecutorService executor;
    private final Gateway gateway;
    private final UserActionLogSupport actionLog;
    private final AtomicBoolean refreshInFlight = new AtomicBoolean(false);

    PortfolioRefreshController(
            StrategyRepository strategyRepository,
            ExecutorService executor,
            Gateway gateway
    ) {
        this.strategyRepository = strategyRepository;
        this.executor = executor;
        this.gateway = gateway;
        this.actionLog = new UserActionLogSupport(gateway::log);
    }

    void refresh(boolean manualTrigger) {
        if (!refreshInFlight.compareAndSet(false, true)) {
            if (manualTrigger) {
                actionLog.skipped(REFRESH_ACTION_NAME, "Refresh already in progress.");
            }
            return;
        }
        if (!gateway.isConnected() || gateway.brokerType() != BrokerType.ALPACA) {
            refreshInFlight.set(false);
            if (manualTrigger) {
                actionLog.failed(REFRESH_ACTION_NAME, "Connect to Alpaca before refreshing the portfolio.");
                runOnEdt(gateway::showConnectionRequired);
            }
            return;
        }
        if (manualTrigger) {
            actionLog.started(REFRESH_ACTION_NAME);
        }
        runOnEdt(gateway::onRefreshStarted);
        executor.submit(() -> refreshInBackground(manualTrigger));
    }

    private void refreshInBackground(boolean manualTrigger) {
        try {
            List<Strategy> stored = strategyRepository.findAll();
            Map<String, Position> snapshots = loadPositionSnapshots(stored);
            runOnEdt(() -> applySuccessfulRefresh(stored, snapshots));
        } catch (Exception ex) {
            runOnEdt(() -> applyFailedRefresh(manualTrigger, ex));
        }
    }

    private void applySuccessfulRefresh(List<Strategy> stored, Map<String, Position> snapshots) {
        try {
            gateway.syncStrategies(stored);
            gateway.applyPositionSnapshots(snapshots);
            gateway.refreshStrategyTableContent();
            gateway.refreshPanels();
            gateway.updateStatusBar();
            actionLog.completed(REFRESH_ACTION_NAME, "Refreshed "
                    + snapshots.size()
                    + " strategy position snapshot(s) from Alpaca.");
        } finally {
            finishRefresh();
        }
    }

    private void applyFailedRefresh(boolean manualTrigger, Exception ex) {
        try {
            actionLog.failed(REFRESH_ACTION_NAME, ex.getMessage());
            if (manualTrigger) {
                gateway.showRefreshFailed(ex.getMessage());
            }
        } finally {
            finishRefresh();
        }
    }

    private void finishRefresh() {
        refreshInFlight.set(false);
        gateway.onRefreshFinished();
    }

    private Map<String, Position> loadPositionSnapshots(List<Strategy> stored) {
        if (stored == null || stored.isEmpty() || gateway.brokerType() != BrokerType.ALPACA) {
            return Map.of();
        }
        return BrokerSnapshotLoader.loadPositionSnapshots(stored, gateway::alpacaClientForMode, this::includeInRefresh);
    }

    private boolean includeInRefresh(Strategy strategy) {
        return strategy != null
                && strategy.status() != StrategyStatus.ARCHIVED
                && strategy.status() != StrategyStatus.STOPPED;
    }

    private void runOnEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }
}
