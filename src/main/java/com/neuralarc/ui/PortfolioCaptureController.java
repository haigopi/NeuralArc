package com.neuralarc.ui;

import com.neuralarc.model.SellSubmissionType;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.service.StrategyService;
import com.neuralarc.util.Monetary;

import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.UUID;

final class PortfolioCaptureController {
    private static final long LOOP_COOLDOWN_MILLIS = 15_000L;

    interface Gateway {
        List<ManagedStrategy> strategies();
        StrategyMode selectedViewMode();
        String selectedWorkspaceId();
        BigDecimal realizedPnlForStrategy(String strategyId);
        StrategyService.StrategyCreationResult sellPosition(
                ManagedStrategy entry,
                SellSubmissionType submissionType,
                StrategyService.SellExecutionSource executionSource
        );
        int cancelPendingBaseBuys(StrategyMode mode);
        String runSmartPicksAutomation(PortfolioCaptureConfig config);
        boolean tradingSessionOpen();
        String nextTradingSessionOpenDisplay();
        void onMonitoringChanged(boolean active, PortfolioCaptureSnapshot snapshot, PortfolioCaptureConfig config,
                                 StrategyMode mode, String workspaceId);
        void onSnapshotUpdated(PortfolioCaptureSnapshot snapshot, PortfolioCaptureConfig config);
        void onAutomationStateChanged(PortfolioCaptureAutomationState state, int loopCount, int pendingCanceled);
        void onExecutionStarted();
        void onExecutionFinished(PortfolioCaptureExecutionResult result, boolean targetTriggered);
        void log(String message);
    }

    private final Gateway gateway;
    private final PortfolioCaptureCalculator calculator;
    private final PortfolioCaptureStateStore stateStore;
    private final PortfolioCaptureHistoryStore historyStore;
    private final AtomicBoolean executing = new AtomicBoolean(false);
    private final Set<String> manuallyExcludedStrategyIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private Timer monitoringTimer;
    private PortfolioCaptureConfig activeConfig;
    private StrategyMode activeMode;
    private String activeWorkspaceId;
    private PortfolioCaptureSnapshot lastSnapshot = PortfolioCaptureSnapshot.empty();
    private PortfolioCaptureAutomationState automationState = PortfolioCaptureAutomationState.STOPPED;
    private int loopCount;
    private int pendingCanceledCount;
    private volatile boolean emergencyStopRequested;
    private boolean marketClosedPauseLogged;
    private PortfolioCaptureHistoryStore.Summary captureHistorySummary;

    PortfolioCaptureController(
            Gateway gateway,
            PortfolioCaptureCalculator calculator,
            PortfolioCaptureStateStore stateStore,
            PortfolioCaptureHistoryStore historyStore
    ) {
        this.gateway = gateway;
        this.calculator = calculator;
        this.stateStore = stateStore;
        this.historyStore = historyStore;
        this.captureHistorySummary = historyStore.summary();
    }

    PortfolioCaptureHistoryStore.Summary captureHistorySummary() {
        return captureHistorySummary;
    }

    PortfolioCaptureSnapshot currentSnapshot(PortfolioCaptureConfig config) {
        lastSnapshot = calculator.calculate(strategiesForContext(gateway.selectedViewMode(), gateway.selectedWorkspaceId()), config, gateway::realizedPnlForStrategy);
        return lastSnapshot;
    }

    void restoreIfNeeded() {
        stateStore.load().ifPresent(state -> {
            if (state.enabled() && state.config().targetValue().compareTo(BigDecimal.ZERO) > 0) {
                activateMonitoring(state.config(), state.mode(), state.workspaceId());
                gateway.log("[Portfolio Liquidation] Monitoring restored after startup.");
            }
        });
    }

    void activateMonitoring(PortfolioCaptureConfig config) {
        activateMonitoring(config, gateway.selectedViewMode(), gateway.selectedWorkspaceId());
    }

    private void activateMonitoring(PortfolioCaptureConfig config, StrategyMode mode, String workspaceId) {
        activeConfig = config;
        activeMode = mode == null ? StrategyMode.PAPER : mode;
        activeWorkspaceId = workspaceId;
        stopTimerOnly();
        lastSnapshot = calculator.calculate(strategiesForContext(activeMode, activeWorkspaceId), config, gateway::realizedPnlForStrategy);
        stateStore.save(new PortfolioCaptureStateStore.State(
                true,
                config,
                Instant.now(),
                lastSnapshot.marketValue(),
                activeMode,
                activeWorkspaceId
        ));
        int delayMillis = Math.max(1, config.monitoringIntervalSeconds()) * 1000;
        monitoringTimer = new Timer(delayMillis, ignored -> evaluateMonitoringTick());
        monitoringTimer.setInitialDelay(0);
        monitoringTimer.start();
        gateway.onMonitoringChanged(true, lastSnapshot, config, activeMode, activeWorkspaceId);
        setAutomationState(PortfolioCaptureAutomationState.MONITORING);
        gateway.log("[Portfolio Liquidation] Monitoring activated. Target "
                + config.targetType() + "=" + Monetary.round(config.targetValue())
                + " flow=" + config.executionFlow()
                + " reentryMode=" + config.reentryMode()
                + " reentryTerm=" + config.reentryRecommendationType()
                + " smartPicksStrategy=" + config.reentrySmartPicksStrategy()
                + " reentryQty=" + config.reentryQuantity());
    }

    void deactivateMonitoring() {
        StrategyMode mode = activeMode;
        String workspaceId = activeWorkspaceId;
        stopTimerOnly();
        activeConfig = null;
        activeMode = null;
        activeWorkspaceId = null;
        stateStore.clear();
        gateway.onMonitoringChanged(false, lastSnapshot, null, mode, workspaceId);
        setAutomationState(PortfolioCaptureAutomationState.STOPPED);
        gateway.log("[Portfolio Liquidation] Monitoring deactivated.");
    }

    void emergencyStop() {
        StrategyMode mode = activeMode;
        String workspaceId = activeWorkspaceId;
        emergencyStopRequested = true;
        stopTimerOnly();
        activeConfig = null;
        activeMode = null;
        activeWorkspaceId = null;
        stateStore.clear();
        setAutomationState(PortfolioCaptureAutomationState.STOPPED);
        gateway.onMonitoringChanged(false, lastSnapshot, null, mode, workspaceId);
        gateway.log("[Portfolio Liquidation] Emergency stop used. Automation stopped.");
    }

    boolean monitoringActive() {
        return monitoringTimer != null && monitoringTimer.isRunning();
    }

    void excludeStrategyFromActiveCapture(String strategyId) {
        if (strategyId == null || strategyId.isBlank() || !executing.get()) {
            return;
        }
        manuallyExcludedStrategyIds.add(strategyId);
        gateway.log("[Portfolio Liquidation] Excluded manually sold strategy from in-flight liquidation. strategyId=" + strategyId);
    }

    void executeNow(PortfolioCaptureConfig config) {
        PortfolioCaptureSnapshot snapshot = currentSnapshot(config);
        executeCapture(snapshot, "MANUAL_CAPTURE_NOW", false, config);
    }

    void shutdown() {
        stopTimerOnly();
    }

    private void evaluateMonitoringTick() {
        if (activeConfig == null || executing.get()) {
            return;
        }
        if (!gateway.tradingSessionOpen()) {
            if (!marketClosedPauseLogged) {
                gateway.log("[Portfolio Liquidation] Monitoring paused because the trading session is closed. Next open="
                        + gateway.nextTradingSessionOpenDisplay());
                marketClosedPauseLogged = true;
            }
            setAutomationState(PortfolioCaptureAutomationState.PAUSED_MARKET_CLOSED);
            return;
        }
        if (marketClosedPauseLogged) {
            gateway.log("[Portfolio Liquidation] Trading session reopened. Monitoring resumed.");
            marketClosedPauseLogged = false;
            setAutomationState(PortfolioCaptureAutomationState.MONITORING);
        }
        StrategyMode mode = activeMode == null ? gateway.selectedViewMode() : activeMode;
        String workspaceId = activeMode == null ? gateway.selectedWorkspaceId() : activeWorkspaceId;
        lastSnapshot = calculator.calculate(strategiesForContext(mode, workspaceId), activeConfig, gateway::realizedPnlForStrategy);
        stateStore.save(new PortfolioCaptureStateStore.State(
                true,
                activeConfig,
                Instant.now(),
                lastSnapshot.marketValue(),
                mode,
                workspaceId
        ));
        gateway.onSnapshotUpdated(lastSnapshot, activeConfig);
        if (calculator.targetReached(lastSnapshot, activeConfig)) {
            executeCapture(lastSnapshot, "AUTO_CAPTURE_TARGET_REACHED", true);
        }
    }

    private void executeCapture(PortfolioCaptureSnapshot snapshot, String triggerReason, boolean targetTriggered) {
        executeCapture(snapshot, triggerReason, targetTriggered, activeConfig == null ? PortfolioCaptureConfig.captureNow() : activeConfig);
    }

    private void executeCapture(
            PortfolioCaptureSnapshot snapshot,
            String triggerReason,
            boolean targetTriggered,
            PortfolioCaptureConfig executionConfig
    ) {
        if (snapshot == null || snapshot.rows().isEmpty()) {
            gateway.log("[Portfolio Liquidation] Skipped. No eligible portfolio rows.");
            return;
        }
        if (!executing.compareAndSet(false, true)) {
            gateway.log("[Portfolio Liquidation] Duplicate trigger ignored. Liquidation already in progress.");
            return;
        }
        emergencyStopRequested = false;
        manuallyExcludedStrategyIds.clear();
        if (targetTriggered && activeConfig != null
                && (activeConfig.autoStopAfterExecution() || !executionConfig.continuousLoop())) {
            stopTimerOnly();
        }
        gateway.onExecutionStarted();
        setAutomationState(PortfolioCaptureAutomationState.CAPTURING);
        gateway.log("[Portfolio Liquidation] Execution started. Trigger=" + triggerReason
                + " estimatedValue=" + Monetary.round(snapshot.marketValue())
                + " estimatedPnl=" + Monetary.round(snapshot.unrealizedPnl()));

        new CaptureWorker(snapshot, triggerReason, targetTriggered, executionConfig).execute();
    }

    private List<ManagedStrategy> strategiesForContext(StrategyMode mode, String workspaceId) {
        StrategyMode effectiveMode = mode == null ? StrategyMode.PAPER : mode;
        return gateway.strategies().stream()
                .filter(entry -> entry != null && entry.strategy != null && entry.strategy.mode() == effectiveMode)
                .filter(entry -> workspaceId == null || workspaceId.equals(entry.strategy.workspaceId()))
                .toList();
    }

    private void stopTimerOnly() {
        if (monitoringTimer != null) {
            monitoringTimer.stop();
            monitoringTimer = null;
        }
    }

    private final class CaptureWorker extends SwingWorker<PortfolioCaptureExecutionResult, Void> {
        private final PortfolioCaptureSnapshot snapshot;
        private final String triggerReason;
        private final boolean targetTriggered;
        private final PortfolioCaptureConfig executionConfig;

        private CaptureWorker(
                PortfolioCaptureSnapshot snapshot,
                String triggerReason,
                boolean targetTriggered,
                PortfolioCaptureConfig executionConfig
        ) {
            this.snapshot = snapshot;
            this.triggerReason = triggerReason;
            this.targetTriggered = targetTriggered;
            this.executionConfig = executionConfig;
        }

        @Override
        protected PortfolioCaptureExecutionResult doInBackground() {
            List<String> successes = new ArrayList<>();
            List<String> failures = new ArrayList<>();
            Set<String> skippedManualSales = new HashSet<>();
            BigDecimal actualValueTotal = BigDecimal.ZERO;
            BigDecimal actualPnlTotal = BigDecimal.ZERO;
            if (executionConfig.autoCleanPendingBeforeCycle() && !emergencyStopRequested) {
                setAutomationState(PortfolioCaptureAutomationState.CLEANING_PENDING_ORDERS);
                int canceled = gateway.cancelPendingBaseBuys(executionMode());
                pendingCanceledCount += canceled;
                gateway.log("[Portfolio Liquidation] Pending base buy cleanup canceled " + canceled + " order(s).");
            }
            setAutomationState(PortfolioCaptureAutomationState.CAPTURING);
            for (PortfolioCaptureSnapshot.Row row : snapshot.rows()) {
                if (manuallyExcludedStrategyIds.contains(row.strategyId())) {
                    skippedManualSales.add(row.strategyId());
                    gateway.log("[Portfolio Liquidation] Skipped " + row.symbol()
                            + " because it was sold individually while liquidation was in flight.");
                    continue;
                }
                if (emergencyStopRequested) {
                    failures.add(row.symbol() + ": automation stopped");
                    continue;
                }
                ManagedStrategy entry = findEntry(row.strategyId());
                if (entry == null) {
                    failures.add(row.symbol() + ": strategy no longer visible");
                    continue;
                }
                StrategyService.StrategyCreationResult result = gateway.sellPosition(
                        entry,
                        SellSubmissionType.MARKET,
                        StrategyService.SellExecutionSource.PORTFOLIO_CAPTURE
                );
                if (result.success()) {
                    successes.add(row.symbol());
                    BigDecimal actualValue = actualExecutionValue(row, result);
                    BigDecimal actualPnl = actualValue.subtract(row.investment());
                    BigDecimal variance = PortfolioCaptureSnapshot.percent(actualValue.subtract(row.marketValue()), row.marketValue());
                    actualValueTotal = actualValueTotal.add(actualValue);
                    actualPnlTotal = actualPnlTotal.add(actualPnl);
                    logAudit(row, actualValue, actualPnl, variance, triggerReason);
                } else {
                    failures.add(row.symbol() + ": " + result.error());
                }
            }
            if (!emergencyStopRequested && executionConfig.reentryEnabled() && !successes.isEmpty()) {
                setAutomationState(PortfolioCaptureAutomationState.WAITING_FOR_CONFIRMATION);
                setAutomationState(PortfolioCaptureAutomationState.REENTERING_POSITIONS);
                if (executionConfig.autoCleanPendingBeforeCycle()) {
                    int canceled = gateway.cancelPendingBaseBuys(executionMode());
                    pendingCanceledCount += canceled;
                    gateway.log("[Portfolio Liquidation] Pre re-entry pending base buy cleanup canceled " + canceled + " order(s).");
                }
                String reentrySummary = gateway.runSmartPicksAutomation(executionConfig);
                gateway.log("[Portfolio Liquidation] Smart Picks automation completed: " + reentrySummary);
            }
            PortfolioCaptureSnapshot resultSnapshot = withoutRows(snapshot, skippedManualSales);
            BigDecimal executionVariance = actualValueTotal.subtract(resultSnapshot.marketValue());
            return PortfolioCaptureExecutionResult.from(resultSnapshot, successes, failures, actualValueTotal, actualPnlTotal, executionVariance);
        }

        @Override
        protected void done() {
            try {
                PortfolioCaptureExecutionResult result = get();
                recordCaptureHistory(result, triggerReason, executionConfig);
                if (targetTriggered) {
                    activeConfig = null;
                    activeMode = null;
                    stateStore.clear();
                }
                gateway.onExecutionFinished(result, targetTriggered);
                if (!emergencyStopRequested && executionConfig.continuousLoop() && result.capturedCount() > 0) {
                    restartMonitoringAfterCooldown(executionConfig);
                }
            } catch (Exception ex) {
                gateway.log("[Portfolio Liquidation] Execution failed: " + ex.getMessage());
                setAutomationState(PortfolioCaptureAutomationState.ERROR);
                gateway.onExecutionFinished(PortfolioCaptureExecutionResult.from(snapshot, List.of(), List.of(ex.getMessage())), targetTriggered);
            } finally {
                executing.set(false);
                if (targetTriggered && !executionConfig.continuousLoop()) {
                    gateway.onMonitoringChanged(false, lastSnapshot, null, activeMode, activeWorkspaceId);
                }
            }
        }
    }

    private void restartMonitoringAfterCooldown(PortfolioCaptureConfig config) {
        loopCount++;
        setAutomationState(PortfolioCaptureAutomationState.RESTARTING_MONITORING);
        gateway.log("[Portfolio Liquidation] Cooldown started before next loop. seconds=" + (LOOP_COOLDOWN_MILLIS / 1000));
        Timer restartTimer = new Timer((int) LOOP_COOLDOWN_MILLIS, ignored -> {
            if (!emergencyStopRequested) {
                gateway.log("[Portfolio Liquidation] Restarting continuous monitoring. loop=" + loopCount);
                activateMonitoring(config);
            }
        });
        restartTimer.setRepeats(false);
        restartTimer.start();
    }

    private StrategyMode executionMode() {
        return activeMode == null ? gateway.selectedViewMode() : activeMode;
    }

    private void recordCaptureHistory(
            PortfolioCaptureExecutionResult result,
            String triggerReason,
            PortfolioCaptureConfig executionConfig
    ) {
        if (result == null || result.capturedCount() <= 0) {
            return;
        }
        int completedLoopNumber = executionConfig != null && executionConfig.continuousLoop()
                ? loopCount + 1
                : 0;
        captureHistorySummary = historyStore.append(new PortfolioCaptureHistoryStore.Entry(
                UUID.randomUUID().toString(),
                result.timestamp(),
                completedLoopNumber,
                triggerReason,
                executionConfig == null ? "" : executionConfig.executionFlow().name(),
                result.capturedCount(),
                result.totalInvestment(),
                result.estimatedPortfolioValue(),
                result.actualBrokerExecutionValue(),
                result.estimatedPnl(),
                result.actualPnl(),
                result.executionVariance()
        ));
        gateway.log("[Portfolio Liquidation] Liquidation history updated. runs=" + captureHistorySummary.captureCount()
                + " stocks=" + captureHistorySummary.capturedStocks()
                + " cumulativeActualPnl=$" + Monetary.round(captureHistorySummary.actualPnl())
                + " cumulativeEstimatedPnl=$" + Monetary.round(captureHistorySummary.estimatedPnl()));
    }

    private void setAutomationState(PortfolioCaptureAutomationState state) {
        automationState = state;
        gateway.onAutomationStateChanged(automationState, loopCount, pendingCanceledCount);
    }

    private ManagedStrategy findEntry(String strategyId) {
        for (ManagedStrategy entry : gateway.strategies()) {
            if (entry.strategy.id().equals(strategyId)) {
                return entry;
            }
        }
        return null;
    }

    private PortfolioCaptureSnapshot withoutRows(PortfolioCaptureSnapshot snapshot, Set<String> excludedStrategyIds) {
        if (snapshot == null || excludedStrategyIds == null || excludedStrategyIds.isEmpty()) {
            return snapshot;
        }
        List<PortfolioCaptureSnapshot.Row> rows = snapshot.rows().stream()
                .filter(row -> !excludedStrategyIds.contains(row.strategyId()))
                .toList();
        BigDecimal investment = rows.stream()
                .map(PortfolioCaptureSnapshot.Row::investment)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal marketValue = rows.stream()
                .map(PortfolioCaptureSnapshot.Row::marketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pnl = rows.stream()
                .map(PortfolioCaptureSnapshot.Row::estimatedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PortfolioCaptureSnapshot(
                Monetary.round(investment),
                Monetary.round(marketValue),
                Monetary.round(pnl),
                Monetary.round(PortfolioCaptureSnapshot.percent(pnl, investment)),
                snapshot.targetProgressPercent(),
                rows.size(),
                rows,
                snapshot.calculatedAt()
        );
    }

    private BigDecimal actualExecutionValue(
            PortfolioCaptureSnapshot.Row row,
            StrategyService.StrategyCreationResult result
    ) {
        if (result.filledQuantity().compareTo(BigDecimal.ZERO) > 0
                && result.filledAveragePrice().compareTo(BigDecimal.ZERO) > 0) {
            return result.filledQuantity().multiply(result.filledAveragePrice());
        }
        return row.marketValue();
    }

    private void logAudit(
            PortfolioCaptureSnapshot.Row row,
            BigDecimal actualValue,
            BigDecimal actualPnl,
            BigDecimal variance,
            String triggerReason
    ) {
        gateway.log("[PORTFOLIO_CAPTURE_AUDIT] timestamp=" + Instant.now()
                + " symbol=" + row.symbol()
                + " quantity=" + row.quantity()
                + " estimatedMarketPrice=" + Monetary.round(row.marketPrice())
                + " actualBrokerExecutionValue=" + Monetary.round(actualValue)
                + " estimatedPnl=" + Monetary.round(row.estimatedPnl())
                + " actualPnl=" + Monetary.round(actualPnl)
                + " executionVariancePercent=" + Monetary.round(variance)
                + " triggerReason=" + triggerReason
                + " totalEstimatedValue=" + Monetary.round(lastSnapshot.marketValue()));
    }
}
