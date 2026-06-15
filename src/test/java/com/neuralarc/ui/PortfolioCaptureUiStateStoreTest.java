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
}
