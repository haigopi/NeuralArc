package com.neuralarc.ui;

import com.neuralarc.analytics.AnalyticsEvent;
import com.neuralarc.model.ProfitHoldType;
import com.neuralarc.model.StopLossType;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KillSwitchControllerTest {

    @Test
    void activateLogsAndReturnsWhenNoStrategiesExist() {
        FakeGateway gateway = new FakeGateway(List.of());

        new KillSwitchController(gateway).activate();

        assertEquals(1, gateway.logs.size());
        assertTrue(gateway.logs.getFirst().contains("No active strategies"));
        assertEquals(0, gateway.pauseCalls.size());
    }

    @Test
    void activateStopsOnlyActiveStrategiesAndPublishesCount() {
        ManagedStrategy activeA = new ManagedStrategy(baseStrategy("AAPL", StrategyStatus.ACTIVE));
        ManagedStrategy activeB = new ManagedStrategy(baseStrategy("MSFT", StrategyStatus.ACTIVE));
        ManagedStrategy paused = new ManagedStrategy(baseStrategy("TSLA", StrategyStatus.PAUSED));
        FakeGateway gateway = new FakeGateway(List.of(activeA, activeB, paused));

        new KillSwitchController(gateway).activate();

        assertEquals(2, gateway.pauseCalls.size());
        assertTrue(gateway.pauseCalls.contains(activeA.strategy.id()));
        assertTrue(gateway.pauseCalls.contains(activeB.strategy.id()));
        assertEquals(2, gateway.stopPollingCalls);
        assertEquals(1, gateway.syncCalls);
        assertEquals(1, gateway.refreshTableCalls);
        assertEquals(1, gateway.updateStatusCalls);
        assertEquals(1, gateway.refreshPanelsCalls);
        assertEquals("KILL_SWITCH_ACTIVATED", gateway.lastEvent.type());
        assertEquals(2, gateway.lastEvent.payload().get("strategiesStopped"));
    }

    @Test
    void activateWithAllPausedStrategiesStillSyncsRefreshesAndPublishesZeroCount() {
        // Paused strategies must not be stopped, but sync/refresh should still run
        // to guarantee a consistent UI state after the operator triggered kill-switch.
        ManagedStrategy pausedA = new ManagedStrategy(baseStrategy("AAPL", StrategyStatus.PAUSED));
        ManagedStrategy pausedB = new ManagedStrategy(baseStrategy("MSFT", StrategyStatus.PAUSED));
        FakeGateway gateway = new FakeGateway(List.of(pausedA, pausedB));

        new KillSwitchController(gateway).activate();

        assertEquals(0, gateway.pauseCalls.size(), "Paused strategies must not be force-stopped");
        assertEquals(0, gateway.stopPollingCalls);
        assertEquals(1, gateway.syncCalls,          "Sync must run even with zero active strategies");
        assertEquals(1, gateway.refreshTableCalls,  "Table refresh must run even with zero active strategies");
        assertEquals(1, gateway.updateStatusCalls);
        assertEquals(1, gateway.refreshPanelsCalls);
        assertEquals("KILL_SWITCH_ACTIVATED", gateway.lastEvent.type());
        assertEquals(0, gateway.lastEvent.payload().get("strategiesStopped"));
    }

    @Test
    void activateStopsSingleActiveStrategyCorrectly() {
        ManagedStrategy active = new ManagedStrategy(baseStrategy("NVDA", StrategyStatus.ACTIVE));
        FakeGateway gateway = new FakeGateway(List.of(active));

        new KillSwitchController(gateway).activate();

        assertEquals(1, gateway.pauseCalls.size());
        assertTrue(gateway.pauseCalls.contains(active.strategy.id()));
        assertEquals(1, gateway.stopPollingCalls);
        assertEquals(1, gateway.syncCalls);
        assertEquals(1, gateway.refreshTableCalls);
        assertEquals("KILL_SWITCH_ACTIVATED", gateway.lastEvent.type());
        assertEquals(1, gateway.lastEvent.payload().get("strategiesStopped"));
    }

    @Test
    void activateLogsPerSymbolAndFinalSummary() {
        ManagedStrategy activeA = new ManagedStrategy(baseStrategy("AAPL", StrategyStatus.ACTIVE));
        ManagedStrategy activeB = new ManagedStrategy(baseStrategy("TSLA", StrategyStatus.ACTIVE));
        FakeGateway gateway = new FakeGateway(List.of(activeA, activeB));

        new KillSwitchController(gateway).activate();

        // One log per stopped strategy plus the kill-switch summary log
        long symbolLogs = gateway.logs.stream().filter(l -> l.contains("EMERGENCY STOP")).count();
        assertEquals(2, symbolLogs, "Expect one EMERGENCY STOP log per active strategy stopped");

        long summaryLogs = gateway.logs.stream().filter(l -> l.contains("Stopped 2")).count();
        assertEquals(1, summaryLogs, "Expect exactly one summary log showing the stopped count");
    }

    @Test
    void activateDoesNotCallSyncOrRefreshWhenListIsEmpty() {
        FakeGateway gateway = new FakeGateway(List.of());

        new KillSwitchController(gateway).activate();

        assertEquals(0, gateway.syncCalls,         "Sync must not run when strategy list is empty");
        assertEquals(0, gateway.refreshTableCalls, "Table refresh must not run when strategy list is empty");
        assertEquals(0, gateway.updateStatusCalls);
        assertEquals(0, gateway.refreshPanelsCalls);
    }

    private static Strategy baseStrategy(String symbol, StrategyStatus status) {
        return new Strategy(
                UUID.randomUUID().toString(),
                symbol + " Strategy",
                symbol,
                StrategyMode.PAPER,
                status,
                StrategyLifecycleState.CREATED,
                new BigDecimal("10"),
                1,
                new BigDecimal("9"),
                1,
                new BigDecimal("8"),
                1,
                true,
                StopLossType.FIXED_PRICE,
                new BigDecimal("7"),
                new BigDecimal("1"),
                false,
                BigDecimal.ZERO,
                true,
                new BigDecimal("11"),
                new BigDecimal("100"),
                false,
                false,
                ProfitHoldType.PERCENT_TRAILING,
                new BigDecimal("1"),
                new BigDecimal("1"),
                BigDecimal.ZERO,
                false,
                10,
                new BigDecimal("1000"),
                10,
                Instant.now(),
                Instant.now()
        );
    }

    private static final class FakeGateway implements KillSwitchController.Gateway {
        private final List<ManagedStrategy> strategies;
        private final List<String> pauseCalls = new ArrayList<>();
        private final List<String> logs = new ArrayList<>();
        private int stopPollingCalls;
        private int syncCalls;
        private int refreshTableCalls;
        private int updateStatusCalls;
        private int refreshPanelsCalls;
        private AnalyticsEvent lastEvent;

        private FakeGateway(List<ManagedStrategy> strategies) {
            this.strategies = new ArrayList<>(strategies);
        }

        @Override
        public List<ManagedStrategy> strategies() {
            return strategies;
        }

        @Override
        public void pauseStrategy(String strategyId) {
            pauseCalls.add(strategyId);
        }

        @Override
        public void stopPollingCountdown(ManagedStrategy strategy) {
            stopPollingCalls++;
        }

        @Override
        public void syncStrategiesFromRepository() {
            syncCalls++;
        }

        @Override
        public void refreshStrategyTableData() {
            refreshTableCalls++;
        }

        @Override
        public void updateStatusBar() {
            updateStatusCalls++;
        }

        @Override
        public void refreshPanels() {
            refreshPanelsCalls++;
        }

        @Override
        public void log(String message) {
            logs.add(message);
        }

        @Override
        public void publishAnalytics(AnalyticsEvent event) {
            lastEvent = event;
        }
    }
}

