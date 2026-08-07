package com.neuralarc.ui;

import com.neuralarc.model.StrategyStatus;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PollingCellPresenterTest {
    private final PollingCellPresenter presenter = new PollingCellPresenter();
    private final PollingCellPresenter.PollingCellPalette palette = new PollingCellPresenter.PollingCellPalette(
            Color.BLACK,
            Color.WHITE,
            Color.BLUE,
            Color.WHITE,
            Color.CYAN,
            Color.GRAY,
            Color.GREEN,
            Color.ORANGE,
            Color.MAGENTA,
            Color.BLUE
    );

    @Test
    void activeStrategiesShowPollingIndicator() {
        assertTrue(presenter.shouldShowPollingIndicator(StrategyStatus.ACTIVE, false));
        assertTrue(presenter.shouldShowPollingIndicator(StrategyStatus.COMPLETED, true));
        assertFalse(presenter.shouldShowPollingIndicator(StrategyStatus.COMPLETED, false));
    }

    @Test
    void closedMarketPausedOverridesCountdownWithMarketClosedLabel() {
        PollingCellPresenter.PollingCellViewModel viewModel = presenter.present(
                new PollingCellPresenter.PollingCellState(
                        StrategyStatus.PAUSED,
                        false,
                        true,
                        "Canceled",
                        "Orders canceled by the user",
                        false,
                        true,
                        10_000L,
                        20_000L,
                        false
                ),
                palette,
                5_000L
        );

        assertEquals("Market Closed", viewModel.labelText());
        assertEquals(0, viewModel.progress());
        assertTrue(viewModel.tooltip().contains("market is closed"));
    }

    @Test
    void inFlightPollingShowsPollingLabel() {
        PollingCellPresenter.PollingCellViewModel viewModel = presenter.present(
                new PollingCellPresenter.PollingCellState(
                        StrategyStatus.ACTIVE,
                        false,
                        false,
                        "",
                        "",
                        true,
                        false,
                        10_000L,
                        20_000L,
                        true
                ),
                palette,
                1_200L
        );

        assertEquals("Polling...", viewModel.labelText());
        assertTrue(viewModel.progress() >= 8 && viewModel.progress() <= 92);
    }

    @Test
    void overdueCountdownKeepsShowingRealProgressInsteadOfAnimating() {
        // A countdown that has merely reached zero is not a distinct "poll due" state: it keeps
        // its real (full) bar and rolls into the next cycle, so the bar can never drift away from
        // the seconds label.
        PollingCellPresenter.PollingCellState state = new PollingCellPresenter.PollingCellState(
                StrategyStatus.ACTIVE,
                false,
                false,
                "",
                "",
                false,
                false,
                60_000L,
                1_000L,
                false
        );

        PollingCellPresenter.PollingCellViewModel first = presenter.present(state, palette, 2_400L);
        PollingCellPresenter.PollingCellViewModel second = presenter.present(state, palette, 3_600L);

        assertEquals("0s / 60s", first.labelText());
        assertEquals(100, first.progress());
        assertEquals(first.progress(), second.progress(), "an overdue countdown must not animate");
        assertFalse(first.tooltip().contains("waiting for the polling worker"));
    }
}
