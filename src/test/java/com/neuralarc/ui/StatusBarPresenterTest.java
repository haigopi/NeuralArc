package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatusBarPresenterTest {
    private final StatusBarPresenter presenter = new StatusBarPresenter();

    @Test
    void pollSummaryShowsClosedWhenSuppressed() {
        StatusBarPresenter.StatusBarViewModel vm = presenter.present(state(1, 0, true, true, 3, false, true,
                "Market: Closed", false, "Funds Available: -", "CPU: -", "Memory: 1 MB"));

        assertEquals("Paused for market close", vm.pollingText());
        assertEquals(StatusBarPresenter.Tone.MUTED, vm.pollingTone());
    }

    @Test
    void brokerStatusShowsRetryingWhenConnectionRetryPending() {
        StatusBarPresenter.StatusBarViewModel vm = presenter.present(state(0, 1, false, false, 4, true, false,
                "Market: Open (Regular)", true, "Funds Available: -", "CPU: -", "Memory: 1 MB"));

        assertEquals("<html><b>FAILED</b> Retrying...</html>", vm.brokerText());
        assertEquals(StatusBarPresenter.Tone.ERR, vm.brokerTone());
    }

    @Test
    void brokerStatusShowsConnectedWithoutActiveStrategies() {
        StatusBarPresenter.StatusBarViewModel vm = presenter.present(state(0, 2, false, false, 5, false, true,
                "Market: Open (Regular)", true, "Funds Available: -", "CPU: -", "Memory: 1 MB"));

        assertEquals("Connected (No active)", vm.brokerText());
        assertEquals("Strategies 2  Active 0  Inactive 2  History 5", vm.strategyCountText());
        assertEquals(StatusBarPresenter.Tone.WARN, vm.brokerTone());
    }

    @Test
    void normalizesStatusBarValuesForItemLayout() {
        StatusBarPresenter.StatusBarViewModel vm = presenter.present(state(1, 1, true, false, 5, false, true,
                "Market: Open (Regular)", true, "Funds Available: $1000", "CPU: 12%", "Memory: 256 MB"));

        assertEquals("Open (Regular)", vm.marketText());
        assertEquals("$1000", vm.availableFundsText());
        assertEquals("12%", vm.cpuText());
        assertEquals("256 MB", vm.memoryText());
    }

    @Test
    void ownsNetworkIconStatusModel() {
        StatusBarPresenter.NetworkStatusViewModel vm = presenter.presentNetworkStatus(false);

        assertEquals(StatusBarPresenter.NETWORK_ICON_PATH, vm.iconPath());
        assertEquals(StatusBarPresenter.Tone.ERR, vm.tone());
        assertEquals(true, vm.blink());
    }

    private static StatusBarPresenter.StatusBarState state(
            long running,
            long inactive,
            boolean pollEvaluated,
            boolean pollSuppressed,
            long historyRows,
            boolean retryPending,
            boolean connectionOk,
            String marketLabel,
            boolean marketOpen,
            String fundsText,
            String cpuText,
            String memoryText
    ) {
        return new StatusBarPresenter.StatusBarState(running, inactive, pollEvaluated, pollSuppressed, 0, 0, historyRows,
                retryPending, connectionOk, marketLabel, "tooltip", marketOpen, fundsText, cpuText, memoryText);
    }
}
