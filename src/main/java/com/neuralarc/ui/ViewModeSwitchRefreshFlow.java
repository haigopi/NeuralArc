package com.neuralarc.ui;

/**
 * Keeps post mode-switch UI updates in one place so tests can verify
 * that strategy sync runs before table refresh.
 */
final class ViewModeSwitchRefreshFlow {
    private ViewModeSwitchRefreshFlow() {
    }

    static void apply(
            Runnable syncStrategies,
            Runnable refreshStrategyTableData,
            Runnable updateSelectedStrategy,
            Runnable refreshPanels,
            Runnable updateStatusBar
    ) {
        syncStrategies.run();
        refreshStrategyTableData.run();
        updateSelectedStrategy.run();
        refreshPanels.run();
        updateStatusBar.run();
    }
}