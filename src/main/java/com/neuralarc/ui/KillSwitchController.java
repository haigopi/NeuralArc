package com.neuralarc.ui;

import com.neuralarc.analytics.AnalyticsEvent;
import com.neuralarc.model.StrategyStatus;

import java.util.List;

final class KillSwitchController {
    private final Gateway gateway;

    KillSwitchController(Gateway gateway) {
        this.gateway = gateway;
    }

    void activate() {
        List<ManagedStrategy> strategies = gateway.strategies();
        if (strategies.isEmpty()) {
            gateway.log("[KILL SWITCH] No active strategies to stop.");
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

        gateway.log("[KILL SWITCH] Stopped " + stoppedCount + " strategy(ies) and saved to file.");
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
