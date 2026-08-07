package com.neuralarc.ui;

import com.neuralarc.model.StrategyStatus;

import java.awt.Color;

public final class PollingCellPresenter {
    public boolean shouldShowPollingIndicator(StrategyStatus status, boolean waitingForFill) {
        return status == StrategyStatus.ACTIVE || waitingForFill;
    }

    public int pollingProgressPercent(boolean showPollingIndicator, long pollIntervalMillis, long nextPollDueAtMillis, long nowMillis) {
        if (!showPollingIndicator || pollIntervalMillis <= 0L) {
            return 0;
        }
        long effectiveNextPollDueAt = nextPollDueAtMillis > 0L ? nextPollDueAtMillis : nowMillis + pollIntervalMillis;
        long remainingMillis = Math.max(0L, effectiveNextPollDueAt - nowMillis);
        long elapsedMillis = Math.max(0L, pollIntervalMillis - remainingMillis);
        int progress = (int) Math.min(100L, Math.round((elapsedMillis * 100.0d) / pollIntervalMillis));
        if (remainingMillis > 0L && progress == 100) {
            return 99;
        }
        if (elapsedMillis > 0L && progress == 0) {
            return 1;
        }
        return progress;
    }

    public long pollingSecondsRemaining(boolean showPollingIndicator, long pollIntervalMillis, long nextPollDueAtMillis, long nowMillis) {
        if (!showPollingIndicator || pollIntervalMillis <= 0L) {
            return 0L;
        }
        long effectiveNextPollDueAt = nextPollDueAtMillis > 0L ? nextPollDueAtMillis : nowMillis + pollIntervalMillis;
        long remainingMillis = Math.max(0L, effectiveNextPollDueAt - nowMillis);
        return (long) Math.ceil(remainingMillis / 1000.0d);
    }

    public PollingCellViewModel present(PollingCellState state, PollingCellPalette palette, long nowMillis) {
        boolean showPollingIndicator = shouldShowPollingIndicator(state.strategyStatus(), state.waitingForFill());
        // Only an actual in-flight poll animates. A countdown that has merely reached zero keeps
        // showing its real progress and rolls straight into the next cycle, so the bar never
        // detaches from the seconds label.
        boolean animatePoll = state.pollInFlight();
        int progress = animatePoll
                ? animatedPollingProgressPercent(nowMillis)
                : pollingProgressPercent(showPollingIndicator, state.pollIntervalMillis(), state.nextPollDueAtMillis(), nowMillis);
        long secondsRemaining = pollingSecondsRemaining(showPollingIndicator, state.pollIntervalMillis(), state.nextPollDueAtMillis(), nowMillis);
        long totalSeconds = Math.max(1L, Math.round(state.pollIntervalMillis() / 1000.0d));

        Color rowBackground = state.selected() ? palette.selectionBackground() : palette.tableBackground();
        Color trackBackground = state.selected()
                ? new Color(palette.selectionTrackBackground().getRed(), palette.selectionTrackBackground().getGreen(), palette.selectionTrackBackground().getBlue(), 60)
                : palette.normalTrackBackground();
        Color progressForeground = animatePoll
                ? palette.inFlightForeground()
                : !showPollingIndicator && state.paused()
                ? palette.pausedForeground()
                : state.selected()
                ? palette.selectedForeground()
                : palette.defaultForeground();

        String labelText = state.pollInFlight()
                ? "Polling..."
                : state.closedMarketPaused()
                ? "Market Closed"
                : state.strategyStatus() == StrategyStatus.FAILED
                ? "Position Closed"
                : state.strategyStatus() == StrategyStatus.COMPLETED
                ? "Completed"
                : state.strategyStatus() == StrategyStatus.STOPPED
                ? "Stopped"
                : state.strategyStatus() == StrategyStatus.ARCHIVED
                ? "Archived"
                : showPollingIndicator
                ? secondsRemaining + "s / " + totalSeconds + "s"
                : state.paused()
                ? state.pauseLabel()
                : "Idle";

        String tooltip = state.pollInFlight()
                ? "Polling broker data now. Countdown resumes after the current request/response cycle completes."
                : state.closedMarketPaused()
                ? "Polling is paused because the market is closed. Alpaca refresh calls are suppressed until the next trading session opens."
                : showPollingIndicator
                ? secondsRemaining + " seconds remaining out of " + totalSeconds + " seconds"
                : state.paused()
                ? state.pauseTooltip()
                : "Polling is idle for this strategy";

        if (state.closedMarketPaused()) {
            progress = 0;
            progressForeground = palette.pausedForeground();
        }

        return new PollingCellViewModel(
                rowBackground,
                trackBackground,
                progressForeground,
                state.selected() ? palette.selectionTextForeground() : palette.tableTextForeground(),
                progress,
                labelText,
                tooltip,
                showPollingIndicator
        );
    }

    private int animatedPollingProgressPercent(long nowMillis) {
        long phase = (nowMillis / 120L) % 100L;
        return (int) Math.max(8L, Math.min(92L, phase));
    }

    public record PollingCellState(
            StrategyStatus strategyStatus,
            boolean waitingForFill,
            boolean paused,
            String pauseLabel,
            String pauseTooltip,
            boolean pollInFlight,
            boolean closedMarketPaused,
            long pollIntervalMillis,
            long nextPollDueAtMillis,
            boolean selected
    ) {
    }

    public record PollingCellPalette(
            Color tableBackground,
            Color tableTextForeground,
            Color selectionBackground,
            Color selectionTextForeground,
            Color selectionTrackBackground,
            Color normalTrackBackground,
            Color inFlightForeground,
            Color pausedForeground,
            Color selectedForeground,
            Color defaultForeground
    ) {
    }

    public record PollingCellViewModel(
            Color rowBackground,
            Color trackBackground,
            Color progressForeground,
            Color labelForeground,
            int progress,
            String labelText,
            String tooltip,
            boolean showPollingIndicator
    ) {
    }
}
