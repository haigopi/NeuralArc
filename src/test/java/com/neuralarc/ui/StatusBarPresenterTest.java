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
                false,
                true,
                "Market: Closed",
                "tooltip",
                false,
                "Market Value: 0",
                "CPU: -",
                "Memory: 1 MB"
        ));

        assertEquals("Poll: Market Closed", vm.pollingText());
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
                true,
                false,
                "Market: Open (Regular)",
                "tooltip",
                true,
                "Market Value: 0",
                "CPU: -",
                "Memory: 1 MB"
        ));

        assertEquals("<html>Broker: <b>FAILED</b> Retrying...</html>", vm.brokerText());
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
                false,
                true,
                "Market: Open (Regular)",
                "tooltip",
                true,
                "Market Value: 0",
                "CPU: -",
                "Memory: 1 MB"
        ));

        assertEquals("Broker: Connected (No active strategies)", vm.brokerText());
        assertEquals(StatusBarPresenter.Tone.WARN, vm.brokerTone());
    }
}

