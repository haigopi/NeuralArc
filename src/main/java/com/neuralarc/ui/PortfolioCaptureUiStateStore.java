package com.neuralarc.ui;

import com.neuralarc.model.StrategyMode;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Maintains Liquidate Portfolio chrome state independently per mode and strategy tab. */
final class PortfolioCaptureUiStateStore {
    static final String ALL_STOCKS_TAB_ID = "all-stocks";

    private final Map<Key, State> states = new ConcurrentHashMap<>();

    Key key(StrategyMode mode, String strategyTabId) {
        StrategyMode scopedMode = mode == null ? StrategyMode.PAPER : mode;
        String scopedTab = strategyTabId == null || strategyTabId.isBlank() ? ALL_STOCKS_TAB_ID : strategyTabId;
        return new Key(scopedMode, scopedTab);
    }

    State state(Key key) {
        return states.computeIfAbsent(key, ignored -> State.idle());
    }

    void update(Key key, State state) {
        if (key != null && state != null) {
            states.put(key, state);
        }
    }

    record Key(StrategyMode mode, String strategyTabId) {
        String stableId() {
            return mode.name() + ":" + strategyTabId;
        }
    }

    record State(String buttonText, boolean buttonEnabled, String indicatorText, boolean monitoringActive, boolean pulseActive, boolean busy) {
        static State idle() {
            return new State("Liquidate Portfolio", true, "", false, false, false);
        }

        State withButton(String text, boolean enabled) {
            return new State(text, enabled, indicatorText, monitoringActive, pulseActive, busy);
        }

        State withIndicator(String text, boolean active) {
            return new State(buttonText, buttonEnabled, text == null ? "" : text, active, pulseActive, busy);
        }

        State withPulse(boolean active) {
            return new State(buttonText, buttonEnabled, indicatorText, monitoringActive, active, busy);
        }

        State withBusy(boolean nextBusy) {
            return new State(nextBusy ? "Liquidating..." : "Liquidate Portfolio", !nextBusy, indicatorText, monitoringActive, pulseActive, nextBusy);
        }
    }
}
