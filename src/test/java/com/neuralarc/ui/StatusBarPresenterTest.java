package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatusBarPresenterTest {
    private final StatusBarPresenter presenter = new StatusBarPresenter();

    @Test
    void pollSummaryShowsClosedWhenSuppressed() {
        StatusBarPresenter.StatusBarViewModel vm = presenter.present(new StatusBarPresenter.StatusBarState(
                1,
                0,
                true,
                true,
                0,
                0,
                3,
                false,
                true,
                "Market: Closed",
                "tooltip",
                false,
                "Market Value: 0",
                "Invested Value: 0",
                "Funds Available: -",
                "Base Buy Pending Total: 0",
                "CPU: -",
                "Memory: 1 MB"
        ));

        assertEquals("Paused for market close", vm.pollingText());
        assertEquals(StatusBarPresenter.Tone.MUTED, vm.pollingTone());
    }

    @Test
    void brokerStatusShowsRetryingWhenConnectionRetryPending() {
        StatusBarPresenter.StatusBarViewModel vm = presenter.present(new StatusBarPresenter.StatusBarState(
                0,
                1,
                false,
                false,
                0,
                0,
                4,
                true,
                false,
                "Market: Open (Regular)",
                "tooltip",
                true,
                "Market Value: 0",
                "Invested Value: 0",
                "Funds Available: -",
                "Base Buy Pending Total: 0",
                "CPU: -",
                "Memory: 1 MB"
        ));

        assertEquals("<html><b>FAILED</b> Retrying...</html>", vm.brokerText());
        assertEquals(StatusBarPresenter.Tone.ERR, vm.brokerTone());
    }

    @Test
    void brokerStatusShowsConnectedWithoutActiveStrategies() {
        StatusBarPresenter.StatusBarViewModel vm = presenter.present(new StatusBarPresenter.StatusBarState(
                0,
                2,
                false,
                false,
                0,
                0,
                5,
                false,
                true,
                "Market: Open (Regular)",
                "tooltip",
                true,
                "Market Value: 0",
                "Invested Value: 0",
                "Funds Available: -",
                "Base Buy Pending Total: 0",
                "CPU: -",
                "Memory: 1 MB"
        ));

        assertEquals("Connected (No active)", vm.brokerText());
        assertEquals("Strategies 2  Active 0  Inactive 2  History 5", vm.strategyCountText());
        assertEquals(StatusBarPresenter.Tone.WARN, vm.brokerTone());
    }

    @Test
    void normalizesStatusBarValuesForItemLayout() {
        StatusBarPresenter.StatusBarViewModel vm = presenter.present(new StatusBarPresenter.StatusBarState(
                1,
                1,
                true,
                false,
                0,
                0,
                5,
                false,
                true,
                "Market: Open (Regular)",
                "tooltip",
                true,
                "Market Value: $1200",
                "Invested Value: $900",
                "Funds Available: $1000",
                "Base Buy Pending Total: $500",
                "CPU: 12%",
                "Memory: 256 MB"
        ));

        assertEquals("Open (Regular)", vm.marketText());
        assertEquals("$1200", vm.marketValueText());
        assertEquals("$900", vm.investedValueText());
        assertEquals("$1000", vm.availableFundsText());
        assertEquals("$500", vm.baseBuyPendingText());
        assertEquals("12%", vm.cpuText());
        assertEquals("256 MB", vm.memoryText());
    }

    @Test
    void ownsNetworkIconStatusModel() {
        StatusBarPresenter.NetworkStatusViewModel vm = presenter.presentNetworkStatus(false);

        assertEquals(StatusBarPresenter.NETWORK_ICON_PATH, vm.iconPath());
        assertEquals(StatusBarPresenter.Tone.ERR, vm.tone());
        assertEquals(false, vm.blink());
    }
}
