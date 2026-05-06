package com.neuralarc.ui;

import java.awt.Color;

public final class StrategyActionsPresenter {
    private static final Color DISABLED = new Color(88, 106, 118);
    private static final Color RESUME = new Color(46, 125, 50);
    private static final Color CANCEL = new Color(198, 40, 40);
    private static final Color SELL = new Color(230, 81, 0);
    private static final Color PROMOTE_ENABLED = new Color(25, 118, 210);

    public StrategyActionsViewModel present(StrategyActionsState state) {
        boolean archived = state.archived();
        boolean busy = state.busy();
        boolean paused = state.paused();
        boolean canPromote = state.paperMode() && !archived;
        boolean canSell = state.hasPosition() && !archived && !busy;

        String toggleText = archived
                ? "Archived"
                : busy
                ? state.busyText()
                : paused
                ? state.manuallyCanceled() ? "Place Limit Buy Again" : "Resume"
                : "Cancel";

        Color toggleColor = archived || busy
                ? DISABLED
                : paused
                ? RESUME
                : CANCEL;
        Color sellColor = canSell ? SELL : DISABLED;
        Color promoteColor = canPromote ? PROMOTE_ENABLED : DISABLED;

        return new StrategyActionsViewModel(
                toggleText,
                toggleColor,
                !archived,
                canSell,
                sellColor,
                canPromote,
                promoteColor
        );
    }

    public record StrategyActionsState(
            boolean archived,
            boolean paused,
            boolean manuallyCanceled,
            boolean busy,
            String busyText,
            boolean paperMode,
            boolean hasPosition
    ) {
        public StrategyActionsState {
            busyText = busyText == null || busyText.isBlank() ? "Working..." : busyText;
        }
    }

    public record StrategyActionsViewModel(
            String toggleText,
            Color toggleColor,
            boolean toggleEnabled,
            boolean sellEnabled,
            Color sellColor,
            boolean promoteEnabled,
            Color promoteColor
    ) {
    }
}
