package com.neuralarc.ui;

/**
 * Redesigned progress/status view model for the polling cell: human-readable countdown text, an
 * "Attempt N (of M)" label, and distinct lifecycle states (Counting Down / Validating-Syncing /
 * Success / Warning-Paused with a manual "Refresh Now" affordance). Composes the existing, already
 * well-tested {@link PollingCellPresenter} for the countdown-percent/seconds-remaining math rather
 * than duplicating it, so that presenter and its test stay untouched.
 */
final class PositionValidationCellPresenter {
    private static final long SUCCESS_FLASH_WINDOW_MILLIS = 1_500L;

    private final PollingCellPresenter pollingCellPresenter;

    PositionValidationCellPresenter() {
        this(new PollingCellPresenter());
    }

    PositionValidationCellPresenter(PollingCellPresenter pollingCellPresenter) {
        this.pollingCellPresenter = pollingCellPresenter;
    }

    ViewModel present(State state, PollingCellPresenter.PollingCellPalette palette, long nowMillis) {
        PollingCellPresenter.PollingCellViewModel base = pollingCellPresenter.present(state.pollingState(), palette, nowMillis);

        if (state.warningPaused()) {
            return new ViewModel(base, ValidationLifecycleState.WARNING_PAUSED, "", attemptLabel(state), true);
        }
        if (!base.showPollingIndicator()) {
            return new ViewModel(base, null, "", "", false);
        }
        if (state.pollingState().pollInFlight()) {
            return new ViewModel(base, ValidationLifecycleState.VALIDATING_SYNCING, "", attemptLabel(state), false);
        }
        if (state.lastValidationSuccessAtMillis() > 0L
                && nowMillis - state.lastValidationSuccessAtMillis() <= SUCCESS_FLASH_WINDOW_MILLIS) {
            return new ViewModel(base, ValidationLifecycleState.SUCCESS, "", attemptLabel(state), false);
        }
        return new ViewModel(base, ValidationLifecycleState.COUNTING_DOWN, countdownText(state, nowMillis), attemptLabel(state), false);
    }

    private String countdownText(State state, long nowMillis) {
        long pollIntervalMillis = state.pollingState().pollIntervalMillis();
        long secondsRemaining = pollingCellPresenter.pollingSecondsRemaining(
                true, pollIntervalMillis, state.pollingState().nextPollDueAtMillis(), nowMillis);
        long totalSeconds = Math.max(1L, Math.round(pollIntervalMillis / 1000.0d));
        long elapsedSeconds = Math.max(0L, totalSeconds - secondsRemaining);
        return elapsedSeconds + "s elapsed out of " + totalSeconds + "s — next validation in " + secondsRemaining + "s";
    }

    private String attemptLabel(State state) {
        return state.maxAttemptsConfigured() > 0
                ? "Validating positions (Attempt " + state.activeAttempt() + " of " + state.maxAttemptsConfigured() + ")"
                : "Validating positions (Attempt " + state.activeAttempt() + ")";
    }

    enum ValidationLifecycleState {
        COUNTING_DOWN,
        VALIDATING_SYNCING,
        SUCCESS,
        WARNING_PAUSED
    }

    record State(
            PollingCellPresenter.PollingCellState pollingState,
            boolean warningPaused,
            int activeAttempt,
            int maxAttemptsConfigured,
            long lastValidationSuccessAtMillis
    ) {
    }

    record ViewModel(
            PollingCellPresenter.PollingCellViewModel base,
            ValidationLifecycleState lifecycleState,
            String countdownText,
            String attemptLabel,
            boolean showRefreshNowAffordance
    ) {
    }
}
