package com.neuralarc.ui;

import com.neuralarc.model.SellSubmissionType;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.service.StrategyService;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One independent Liquidate Portfolio run per trading mode and workspace.
 *
 * <p>A single shared controller could hold only one run: activating monitoring in a second tab
 * silently replaced the first tab's run while the first tab's chrome still read "Armed", and its
 * dialog then had nothing to deactivate. Here every scope owns its own controller, its own persisted
 * state file and its own timer, so runs in different workspaces neither replace nor see each other.
 *
 * <p>Each controller is handed a gateway pinned to its scope, so everything the controller resolves
 * through "the selected mode / workspace" — activation, continuous-loop restarts, snapshots — stays on
 * the scope it was started for, whichever tab the operator happens to be viewing.
 */
final class PortfolioCaptureRuns {
    static final String LEGACY_STATE_FILE = "portfolio-capture-state.json";
    private static final String STATE_FILE_PREFIX = "portfolio-capture-state-";

    /** A mode plus workspace; a null workspace is the All Stocks tab. */
    record Scope(StrategyMode mode, String workspaceId) {
        Scope {
            mode = mode == null ? StrategyMode.PAPER : mode;
            workspaceId = workspaceId == null || workspaceId.isBlank() ? null : workspaceId;
        }

        String label() {
            return mode.name() + ":" + (workspaceId == null ? "All Stocks" : workspaceId);
        }

        String fileName() {
            String workspace = workspaceId == null ? "all-stocks" : workspaceId.replaceAll("[^A-Za-z0-9._-]", "_");
            return STATE_FILE_PREFIX + mode.name() + "-" + workspace + ".json";
        }
    }

    /** The frame-level data and actions every run shares, with callbacks that name their scope. */
    interface Host {
        List<ManagedStrategy> strategies();
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
        void onMonitoringChanged(Scope scope, boolean active, PortfolioCaptureConfig config);
        void onSnapshotUpdated(Scope scope, PortfolioCaptureConfig config);
        void onAutomationStateChanged(Scope scope, PortfolioCaptureAutomationState state);
        void onExecutionStarted(Scope scope);
        void onExecutionFinished(Scope scope, PortfolioCaptureExecutionResult result, boolean targetTriggered);
        void log(String message);
    }

    private final Host host;
    private final PortfolioCaptureCalculator calculator;
    private final Path stateDirectory;
    private final PortfolioCaptureHistoryStore historyStore;
    private final Map<Scope, PortfolioCaptureController> controllers = new LinkedHashMap<>();

    PortfolioCaptureRuns(Host host, PortfolioCaptureCalculator calculator, Path stateDirectory,
                         PortfolioCaptureHistoryStore historyStore) {
        this.host = host;
        this.calculator = calculator;
        this.stateDirectory = stateDirectory;
        this.historyStore = historyStore;
    }

    /** The controller for {@code scope}, created on first use. */
    synchronized PortfolioCaptureController controller(Scope scope) {
        return controllers.computeIfAbsent(scope, this::newController);
    }

    boolean monitoringActive(Scope scope) {
        PortfolioCaptureController controller = existing(scope);
        return controller != null && controller.monitoringActive();
    }

    /** Scopes whose monitoring is running right now. */
    synchronized List<Scope> activeScopes() {
        List<Scope> active = new ArrayList<>();
        controllers.forEach((scope, controller) -> {
            if (controller.monitoringActive()) {
                active.add(scope);
            }
        });
        return active;
    }

    boolean anyMonitoringActive() {
        return !activeScopes().isEmpty();
    }

    /** Stops every running liquidation monitor in every mode and workspace; returns how many stopped. */
    int stopAll() {
        List<Scope> active = activeScopes();
        for (Scope scope : active) {
            controller(scope).emergencyStop();
        }
        return active.size();
    }

    /** Excludes a manually sold strategy from whichever run is currently liquidating it. */
    synchronized void excludeStrategyFromActiveCapture(String strategyId) {
        controllers.values().forEach(controller -> controller.excludeStrategyFromActiveCapture(strategyId));
    }

    PortfolioCaptureHistoryStore.Summary captureHistorySummary() {
        return historyStore.summary();
    }

    /**
     * Restores every run that was monitoring when the app last closed. The pre-per-scope single state
     * file is migrated once into its own scope's file, then retired.
     */
    void restoreAll() {
        migrateLegacyState();
        if (!Files.isDirectory(stateDirectory)) {
            return;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(stateDirectory, STATE_FILE_PREFIX + "*.json")) {
            for (Path file : files) {
                new PortfolioCaptureStateStore(file).load()
                        .filter(PortfolioCaptureStateStore.State::enabled)
                        .ifPresent(state -> controller(new Scope(state.mode(), state.workspaceId())).restoreIfNeeded());
            }
        } catch (IOException ex) {
            host.log("[Portfolio Liquidation] Could not read saved monitoring state: " + ex.getMessage());
        }
    }

    synchronized void shutdown() {
        controllers.values().forEach(PortfolioCaptureController::shutdown);
    }

    private void migrateLegacyState() {
        Path legacy = stateDirectory.resolve(LEGACY_STATE_FILE);
        PortfolioCaptureStateStore legacyStore = new PortfolioCaptureStateStore(legacy);
        legacyStore.load().ifPresent(state -> {
            if (state.enabled()) {
                Scope scope = new Scope(state.mode(), state.workspaceId());
                new PortfolioCaptureStateStore(stateDirectory.resolve(scope.fileName())).save(state);
            }
        });
        try {
            Files.deleteIfExists(legacy);
        } catch (IOException ignored) {
            // A leftover legacy file is harmless: it is only read here and re-migrated next start.
        }
    }

    private synchronized PortfolioCaptureController existing(Scope scope) {
        return controllers.get(scope);
    }

    private PortfolioCaptureController newController(Scope scope) {
        return new PortfolioCaptureController(
                new ScopedGateway(scope),
                calculator,
                new PortfolioCaptureStateStore(stateDirectory.resolve(scope.fileName())),
                historyStore
        );
    }

    /** Pins "the selected mode and workspace" to one scope and tags every callback with it. */
    private final class ScopedGateway implements PortfolioCaptureController.Gateway {
        private final Scope scope;

        private ScopedGateway(Scope scope) {
            this.scope = scope;
        }

        @Override public List<ManagedStrategy> strategies() { return host.strategies(); }
        @Override public StrategyMode selectedViewMode() { return scope.mode(); }
        @Override public String selectedWorkspaceId() { return scope.workspaceId(); }
        @Override public BigDecimal realizedPnlForStrategy(String strategyId) { return host.realizedPnlForStrategy(strategyId); }

        @Override
        public StrategyService.StrategyCreationResult sellPosition(
                ManagedStrategy entry,
                SellSubmissionType submissionType,
                StrategyService.SellExecutionSource executionSource
        ) {
            return host.sellPosition(entry, submissionType, executionSource);
        }

        @Override public int cancelPendingBaseBuys(StrategyMode mode) { return host.cancelPendingBaseBuys(mode); }
        @Override public String runSmartPicksAutomation(PortfolioCaptureConfig config) { return host.runSmartPicksAutomation(config); }
        @Override public boolean tradingSessionOpen() { return host.tradingSessionOpen(); }
        @Override public String nextTradingSessionOpenDisplay() { return host.nextTradingSessionOpenDisplay(); }

        @Override
        public void onMonitoringChanged(boolean active, PortfolioCaptureSnapshot snapshot, PortfolioCaptureConfig config,
                                        StrategyMode mode, String workspaceId) {
            host.onMonitoringChanged(scope, active, config);
        }

        @Override
        public void onSnapshotUpdated(PortfolioCaptureSnapshot snapshot, PortfolioCaptureConfig config) {
            host.onSnapshotUpdated(scope, config);
        }

        @Override
        public void onAutomationStateChanged(PortfolioCaptureAutomationState state, int loopCount, int pendingCanceled) {
            host.onAutomationStateChanged(scope, state);
        }

        @Override public void onExecutionStarted() { host.onExecutionStarted(scope); }

        @Override
        public void onExecutionFinished(PortfolioCaptureExecutionResult result, boolean targetTriggered) {
            host.onExecutionFinished(scope, result, targetTriggered);
        }

        @Override
        public void log(String message) {
            String tag = "[Portfolio Liquidation]";
            host.log(message != null && message.startsWith(tag)
                    ? tag + "[" + scope.label() + "]" + message.substring(tag.length())
                    : message);
        }
    }
}
