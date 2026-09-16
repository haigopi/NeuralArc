package com.neuralarc.ui;

import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyStatus;

/**
 * Whether the current-strategies grid will draw a row at all.
 *
 * <p>Deliberately narrow: it answers only for the states a row is <em>always</em> hidden in, never
 * for the ones whose visibility depends on live broker exposure or pause reasons. A caller can rely
 * on {@code true} meaning the row is genuinely off-screen; {@code false} means "not known to be
 * hidden", not "definitely visible".
 *
 * <p>It exists because a broker position handed to a hidden row disappears from the grid while still
 * counting in portfolio totals — the symbol looks untracked even though the app knows about it.
 */
final class StrategyRowVisibility {
    private StrategyRowVisibility() {
    }

    /** True when the current-strategies tab never shows this row, whatever the broker reports. */
    static boolean hiddenFromCurrentTab(Strategy strategy) {
        if (strategy == null) {
            return true;
        }
        StrategyStatus status = strategy.status();
        if (status == StrategyStatus.ARCHIVED || status == StrategyStatus.STOPPED) {
            return true;
        }
        // A completed strategy that restarts after exit is hidden until it starts its next cycle.
        return status == StrategyStatus.COMPLETED && strategy.restartAfterExitEnabled();
    }
}
