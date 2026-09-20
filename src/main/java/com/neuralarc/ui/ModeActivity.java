package com.neuralarc.ui;

import com.neuralarc.model.StrategyMode;

/**
 * Which trading modes background work runs for. Live always runs — its stop-losses and fills must never
 * go unwatched, which is why a companion Live poller keeps going while Paper is viewed. Paper runs only
 * while it is the selected mode: with Live on screen, nothing polls, samples or audits the Paper account.
 */
final class ModeActivity {
    private ModeActivity() {
    }

    static boolean runsFor(StrategyMode mode, StrategyMode selectedViewMode) {
        return mode == StrategyMode.LIVE || mode == selectedViewMode;
    }
}
