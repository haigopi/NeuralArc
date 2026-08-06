package com.neuralarc.ui;

import com.neuralarc.model.StrategyStatus;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PositionValidationCellPresenterTest {
    private final PositionValidationCellPresenter presenter = new PositionValidationCellPresenter();
    private final PollingCellPresenter.PollingCellPalette palette = new PollingCellPresenter.PollingCellPalette(
            Color.BLACK, Color.WHITE, Color.BLUE, Color.WHITE, Color.CYAN,
            Color.GRAY, Color.GREEN, Color.ORANGE, Color.MAGENTA, Color.BLUE
    );

    private PollingCellPresenter.PollingCellState activePollingState(boolean pollInFlight) {
        return new PollingCellPresenter.PollingCellState(
                StrategyStatus.ACTIVE, false, false, "", "", pollInFlight, false,
                60_000L, 0L, false
        );
    }

    @Test
    void warningPausedOverridesEverythingElseAndShowsRefreshNow() {
        PositionValidationCellPresenter.State state = new PositionValidationCellPresenter.State(
                activePollingState(false), true, 2, 5, 0L);

        PositionValidationCellPresenter.ViewModel viewModel = presenter.present(state, palette, 10_000L);

        assertEquals(PositionValidationCellPresenter.ValidationLifecycleState.WARNING_PAUSED, viewModel.lifecycleState());
        assertTrue(viewModel.showRefreshNowAffordance());
        assertEquals("Validating positions (Attempt 2 of 5)", viewModel.attemptLabel());
    }

    @Test
    void notActivelyPollingYieldsNullLifecycleStateAndNoAffordances() {
        PollingCellPresenter.PollingCellState completed = new PollingCellPresenter.PollingCellState(
                StrategyStatus.COMPLETED, false, false, "", "", false, false, 60_000L, 0L, false);
        PositionValidationCellPresenter.State state = new PositionValidationCellPresenter.State(
                completed, false, 1, 0, 0L);

        PositionValidationCellPresenter.ViewModel viewModel = presenter.present(state, palette, 10_000L);

        assertNull(viewModel.lifecycleState());
        assertFalse(viewModel.showRefreshNowAffordance());
        assertEquals("", viewModel.countdownText());
        assertEquals("", viewModel.attemptLabel());
    }

    @Test
    void pollInFlightYieldsValidatingSyncing() {
        PositionValidationCellPresenter.State state = new PositionValidationCellPresenter.State(
                activePollingState(true), false, 1, 0, 0L);

        PositionValidationCellPresenter.ViewModel viewModel = presenter.present(state, palette, 10_000L);

        assertEquals(PositionValidationCellPresenter.ValidationLifecycleState.VALIDATING_SYNCING, viewModel.lifecycleState());
    }

    @Test
    void recentSuccessFlashesSuccessState() {
        long now = 10_000L;
        PositionValidationCellPresenter.State state = new PositionValidationCellPresenter.State(
                activePollingState(false), false, 1, 0, now - 500L);

        PositionValidationCellPresenter.ViewModel viewModel = presenter.present(state, palette, now);

        assertEquals(PositionValidationCellPresenter.ValidationLifecycleState.SUCCESS, viewModel.lifecycleState());
    }

    @Test
    void successFlashExpiresIntoCountingDown() {
        long now = 10_000L;
        PositionValidationCellPresenter.State state = new PositionValidationCellPresenter.State(
                activePollingState(false), false, 1, 0, now - 5_000L);

        PositionValidationCellPresenter.ViewModel viewModel = presenter.present(state, palette, now);

        assertEquals(PositionValidationCellPresenter.ValidationLifecycleState.COUNTING_DOWN, viewModel.lifecycleState());
    }

    @Test
    void countingDownProducesHumanReadableCountdownText() {
        PollingCellPresenter.PollingCellState pollingState = new PollingCellPresenter.PollingCellState(
                StrategyStatus.ACTIVE, false, false, "", "", false, false,
                60_000L, 40_000L, false);
        PositionValidationCellPresenter.State state = new PositionValidationCellPresenter.State(
                pollingState, false, 1, 0, 0L);

        PositionValidationCellPresenter.ViewModel viewModel = presenter.present(state, palette, 0L);

        assertTrue(viewModel.countdownText().contains("elapsed out of"), viewModel.countdownText());
        assertTrue(viewModel.countdownText().contains("next validation in"), viewModel.countdownText());
    }

    @Test
    void attemptLabelOmitsMaxWhenUnlimited() {
        PositionValidationCellPresenter.State state = new PositionValidationCellPresenter.State(
                activePollingState(false), false, 3, 0, 0L);

        PositionValidationCellPresenter.ViewModel viewModel = presenter.present(state, palette, 10_000L);

        assertEquals("Validating positions (Attempt 3)", viewModel.attemptLabel());
    }
}
