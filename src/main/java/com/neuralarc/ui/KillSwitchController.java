package com.neuralarc.ui;

import com.neuralarc.analytics.AnalyticsEvent;
import com.neuralarc.model.StrategyStatus;

import java.util.List;

final class KillSwitchController {
    private final Gateway gateway;
    private final UserActionLogSupport actionLog;

    KillSwitchController(Gateway gateway) {
        this.gateway = gateway;
        this.actionLog = new UserActionLogSupport(gateway::log);
    }

    void activate() {
        actionLog.started("Kill Switch");
        List<ManagedStrategy> strategies = gateway.strategies();
        if (strategies.isEmpty()) {
            actionLog.skipped("Kill Switch", "No active strategies to stop.");
            return;
        }

        int stoppedCount = 0;
        for (ManagedStrategy strategy : strategies) {
            if (strategy.strategy.status() == StrategyStatus.ACTIVE) {
                gateway.pauseStrategy(strategy.strategy.id());
                gateway.stopPollingCountdown(strategy);
                gateway.log("[" + strategy.strategy.symbol() + "] EMERGENCY STOP");
                stoppedCount++;
            }
        }

        gateway.syncStrategiesFromRepository();
        gateway.refreshStrategyTableData();
        gateway.updateStatusBar();
        gateway.refreshPanels();

        actionLog.completed("Kill Switch", "Stopped " + stoppedCount + " strategy(ies) and saved to file.");
        gateway.publishAnalytics(new AnalyticsEvent("KILL_SWITCH_ACTIVATED").put("strategiesStopped", stoppedCount));
    }

    interface Gateway {
        List<ManagedStrategy> strategies();
        void pauseStrategy(String strategyId);
        void stopPollingCountdown(ManagedStrategy strategy);
        void syncStrategiesFromRepository();
        void refreshStrategyTableData();
        void updateStatusBar();
        void refreshPanels();
        void log(String message);
        void publishAnalytics(AnalyticsEvent event);
    }
}
