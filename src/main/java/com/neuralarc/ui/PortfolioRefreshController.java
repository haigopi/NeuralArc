package com.neuralarc.ui;

import com.neuralarc.api.HttpAlpacaClient;
import com.neuralarc.model.ApplicationMode;
import com.neuralarc.model.BrokerType;
import com.neuralarc.model.Position;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyStatus;
import com.neuralarc.service.StrategyRepository;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

final class PortfolioRefreshController {
    private static final String REFRESH_ACTION_NAME = "Refresh & Reevaluate Portfolio";
    private static final int REFRESH_WATCHDOG_TIMEOUT_MILLIS = 45_000;
    private static final int REFRESH_RETRY_DELAY_MILLIS = 3_000;
    private static final int MAX_STUCK_REFRESH_RETRIES = 2;

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
    private final AtomicInteger refreshGeneration = new AtomicInteger();

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
        startRefreshAttempt(manualTrigger, 0);
    }

    private void startRefreshAttempt(boolean manualTrigger, int attempt) {
        int generation = refreshGeneration.incrementAndGet();
        if (attempt > 0) {
            gateway.log("[ACTION][" + REFRESH_ACTION_NAME + "] Retry " + attempt
                    + " of " + MAX_STUCK_REFRESH_RETRIES + " after timeout.");
        }
        startRefreshWatchdog(generation, manualTrigger, attempt);
        executor.submit(() -> refreshInBackground(manualTrigger, generation));
    }

    private void refreshInBackground(boolean manualTrigger, int generation) {
        try {
            List<Strategy> stored = strategyRepository.findAll();
            Map<String, Position> snapshots = loadPositionSnapshots(stored);
            runOnEdt(() -> applySuccessfulRefresh(generation, stored, snapshots));
        } catch (Exception ex) {
            runOnEdt(() -> applyFailedRefresh(generation, manualTrigger, ex));
        }
    }

    private void applySuccessfulRefresh(int generation, List<Strategy> stored, Map<String, Position> snapshots) {
        if (generation != refreshGeneration.get()) {
            return;
        }
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

    private void applyFailedRefresh(int generation, boolean manualTrigger, Exception ex) {
        if (generation != refreshGeneration.get()) {
            return;
        }
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

    private void startRefreshWatchdog(int generation, boolean manualTrigger, int attempt) {
        Timer timer = new Timer(REFRESH_WATCHDOG_TIMEOUT_MILLIS, ignored -> {
            if (generation != refreshGeneration.get()) {
                return;
            }
            if (attempt < MAX_STUCK_REFRESH_RETRIES) {
                scheduleRefreshRetry(generation, manualTrigger, attempt + 1);
                return;
            }
            if (!refreshInFlight.compareAndSet(true, false)) {
                return;
            }
            refreshGeneration.compareAndSet(generation, generation + 1);
            actionLog.failed(REFRESH_ACTION_NAME, "Refresh timed out after "
                    + (MAX_STUCK_REFRESH_RETRIES + 1)
                    + " attempt(s) while waiting for Alpaca portfolio data.");
            gateway.onRefreshFinished();
            if (manualTrigger) {
                gateway.showRefreshFailed("Refresh timed out while waiting for Alpaca portfolio data. "
                        + "The app retried automatically and has re-enabled the button.");
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

    private void scheduleRefreshRetry(int generation, boolean manualTrigger, int nextAttempt) {
        actionLog.failed(REFRESH_ACTION_NAME, "Refresh attempt " + nextAttempt
                + " of " + (MAX_STUCK_REFRESH_RETRIES + 1)
                + " timed out while waiting for Alpaca portfolio data. Retrying automatically.");
        Timer retryTimer = new Timer(REFRESH_RETRY_DELAY_MILLIS, ignored -> {
            if (generation != refreshGeneration.get() || !refreshInFlight.get()) {
                return;
            }
            startRefreshAttempt(manualTrigger, nextAttempt);
        });
        retryTimer.setRepeats(false);
        retryTimer.start();
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
