package com.neuralarc.ui;

import com.neuralarc.model.RecommendationType;
import com.neuralarc.model.SellSubmissionType;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.service.StrategyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioCaptureRunsTest {
    private static final PortfolioCaptureRuns.Scope PAPER_PERSONAL =
            new PortfolioCaptureRuns.Scope(StrategyMode.PAPER, "personal-play");
    private static final PortfolioCaptureRuns.Scope PAPER_GROWTH =
            new PortfolioCaptureRuns.Scope(StrategyMode.PAPER, "growth");
    private static final PortfolioCaptureRuns.Scope LIVE_ALL =
            new PortfolioCaptureRuns.Scope(StrategyMode.LIVE, null);

    @TempDir
    Path tempDir;

    private final RecordingHost host = new RecordingHost();
    private PortfolioCaptureRuns runs;

    @AfterEach
    void stopTimers() {
        if (runs != null) {
            runs.stopAll();
            runs.shutdown();
        }
    }

    @Test
    void activatingAnotherWorkspaceDoesNotReplaceTheFirstRun() {
        // Regression guard: with one shared controller, activating monitoring in a second workspace
        // silently replaced the first one's run. The first tab kept showing "Armed" and its dialog had
        // nothing left to deactivate.
        runs = runs();
        runs.controller(PAPER_PERSONAL).activateMonitoring(targetConfig());
        runs.controller(PAPER_GROWTH).activateMonitoring(targetConfig());

        assertTrue(runs.monitoringActive(PAPER_PERSONAL), "the first workspace keeps its run");
        assertTrue(runs.monitoringActive(PAPER_GROWTH));
        assertEquals(Set.of(PAPER_PERSONAL, PAPER_GROWTH), Set.copyOf(runs.activeScopes()));
    }

    @Test
    void deactivatingOneWorkspaceLeavesTheOthersRunning() {
        runs = runs();
        runs.controller(PAPER_PERSONAL).activateMonitoring(targetConfig());
        runs.controller(PAPER_GROWTH).activateMonitoring(targetConfig());

        runs.controller(PAPER_PERSONAL).emergencyStop();

        assertFalse(runs.monitoringActive(PAPER_PERSONAL));
        assertTrue(runs.monitoringActive(PAPER_GROWTH), "another workspace's run is not touched");
    }

    @Test
    void aWorkspaceNeverSeesAnotherWorkspacesRun() {
        runs = runs();
        runs.controller(PAPER_PERSONAL).activateMonitoring(targetConfig());

        assertFalse(runs.monitoringActive(PAPER_GROWTH));
        assertFalse(runs.monitoringActive(new PortfolioCaptureRuns.Scope(StrategyMode.LIVE, "personal-play")),
                "the same workspace in the other mode is its own scope");
        assertFalse(runs.monitoringActive(new PortfolioCaptureRuns.Scope(StrategyMode.PAPER, null)),
                "All Stocks is its own scope, not a wildcard over every workspace");
        assertEquals(new PortfolioCaptureRuns.Scope(StrategyMode.PAPER, "  "),
                new PortfolioCaptureRuns.Scope(StrategyMode.PAPER, null), "a blank workspace id is All Stocks");
    }

    @Test
    void eachRunIsPinnedToItsScopeAndReportsIt() {
        runs = runs();
        runs.controller(PAPER_PERSONAL).activateMonitoring(targetConfig());
        runs.controller(LIVE_ALL).activateMonitoring(targetConfig());

        assertEquals(List.of(PAPER_PERSONAL, LIVE_ALL), host.activated);
        assertNotSame(runs.controller(PAPER_PERSONAL), runs.controller(LIVE_ALL));
    }

    @Test
    void stopAllDeactivatesEveryRunAcrossWorkspacesAndModes() {
        runs = runs();
        runs.controller(PAPER_PERSONAL).activateMonitoring(targetConfig());
        runs.controller(PAPER_GROWTH).activateMonitoring(targetConfig());
        runs.controller(LIVE_ALL).activateMonitoring(targetConfig());

        assertEquals(3, runs.stopAll());

        assertFalse(runs.anyMonitoringActive());
        assertTrue(host.deactivated.containsAll(List.of(PAPER_PERSONAL, PAPER_GROWTH, LIVE_ALL)));
        assertEquals(0, runs.stopAll(), "nothing left to stop");
    }

    @Test
    void everyRunIsRestoredIntoItsOwnScopeAfterRestart() {
        runs = runs();
        runs.controller(PAPER_PERSONAL).activateMonitoring(targetConfig());
        runs.controller(LIVE_ALL).activateMonitoring(targetConfig());
        runs.shutdown();

        runs = runs();
        runs.restoreAll();

        assertEquals(Set.of(PAPER_PERSONAL, LIVE_ALL), Set.copyOf(runs.activeScopes()));
    }

    @Test
    void theLegacySingleStateFileIsMigratedIntoItsScope() {
        new PortfolioCaptureStateStore(tempDir.resolve(PortfolioCaptureRuns.LEGACY_STATE_FILE)).save(
                new PortfolioCaptureStateStore.State(true, targetConfig(), Instant.now(), BigDecimal.ZERO,
                        StrategyMode.PAPER, "personal-play"));

        runs = runs();
        runs.restoreAll();

        assertEquals(List.of(PAPER_PERSONAL), runs.activeScopes());
        assertFalse(Files.exists(tempDir.resolve(PortfolioCaptureRuns.LEGACY_STATE_FILE)), "the legacy file is retired");
    }

    private PortfolioCaptureRuns runs() {
        return new PortfolioCaptureRuns(host, new PortfolioCaptureCalculator(), tempDir,
                new PortfolioCaptureHistoryStore(tempDir.resolve("history.json")));
    }

    private static PortfolioCaptureConfig targetConfig() {
        return new PortfolioCaptureConfig(
                PortfolioCaptureMode.TARGET_MONITORING,
                PortfolioCaptureTargetType.PROFIT_PERCENT,
                new BigDecimal("5"),
                true, 45, true, true,
                PortfolioCaptureExecutionFlow.EXECUTE_ONCE_AND_STOP,
                StrategyMode.PAPER, 1, RecommendationType.SHORT_TERM,
                PortfolioCaptureSmartPicksStrategy.VOLATILE, false);
    }

    private static final class RecordingHost implements PortfolioCaptureRuns.Host {
        private final List<PortfolioCaptureRuns.Scope> activated = new ArrayList<>();
        private final List<PortfolioCaptureRuns.Scope> deactivated = new ArrayList<>();

        @Override public List<ManagedStrategy> strategies() { return List.of(); }
        @Override public BigDecimal realizedPnlForStrategy(String strategyId) { return BigDecimal.ZERO; }
        @Override
        public StrategyService.StrategyCreationResult sellPosition(ManagedStrategy entry, SellSubmissionType submissionType,
                                                                   StrategyService.SellExecutionSource executionSource) {
            throw new AssertionError("no sells expected");
        }
        @Override public int cancelPendingBaseBuys(StrategyMode mode) { return 0; }
        @Override public String runSmartPicksAutomation(PortfolioCaptureConfig config) { return ""; }
        @Override public boolean tradingSessionOpen() { return false; }
        @Override public String nextTradingSessionOpenDisplay() { return ""; }
        @Override
        public void onMonitoringChanged(PortfolioCaptureRuns.Scope scope, boolean active, PortfolioCaptureConfig config) {
            (active ? activated : deactivated).add(scope);
        }
        @Override public void onSnapshotUpdated(PortfolioCaptureRuns.Scope scope, PortfolioCaptureConfig config) { }
        @Override public void onAutomationStateChanged(PortfolioCaptureRuns.Scope scope, PortfolioCaptureAutomationState state) { }
        @Override public void onExecutionStarted(PortfolioCaptureRuns.Scope scope) { }
        @Override public void onExecutionFinished(PortfolioCaptureRuns.Scope scope, PortfolioCaptureExecutionResult result,
                                                  boolean targetTriggered) { }
        @Override public void log(String message) { }
    }
}
