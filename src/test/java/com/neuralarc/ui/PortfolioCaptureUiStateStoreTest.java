package com.neuralarc.ui;

import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PortfolioCaptureUiStateStoreTest {
    @Test
    void scopesStateByModeAndStrategyTab() {
        PortfolioCaptureUiStateStore store = new PortfolioCaptureUiStateStore();
        PortfolioCaptureUiStateStore.Key paperStrategyA = store.key(StrategyMode.PAPER, "workspace:strategy-a");
        PortfolioCaptureUiStateStore.Key liveStrategyA = store.key(StrategyMode.LIVE, "workspace:strategy-a");
        PortfolioCaptureUiStateStore.Key paperStrategyB = store.key(StrategyMode.PAPER, "workspace:strategy-b");

        store.update(paperStrategyA, store.state(paperStrategyA).withBusy(true));

        assertEquals("PAPER:workspace:strategy-a", paperStrategyA.stableId());
        assertFalse(store.state(paperStrategyA).buttonEnabled());
        assertTrue(store.state(liveStrategyA).buttonEnabled());
        assertTrue(store.state(paperStrategyB).buttonEnabled());
    }

    @Test
    void pausedMonitoringKeepsIndicatorWithoutReenablingPulse() {
        PortfolioCaptureUiStateStore store = new PortfolioCaptureUiStateStore();
        PortfolioCaptureUiStateStore.Key key = store.key(StrategyMode.PAPER, "workspace:strategy-a");

        store.update(key, store.state(key).withIndicator("Monitoring Active", true).withPulse(true));
        store.update(key, store.state(key)
                .withButton("Liquidate Portfolio:Auto Paused [Closed Market]", true)
                .withPulse(false));
        store.update(key, store.state(key).withIndicator("Monitoring Active | Loop 2", true));

        assertTrue(store.state(key).monitoringActive());
        assertFalse(store.state(key).pulseActive());
    }
}
