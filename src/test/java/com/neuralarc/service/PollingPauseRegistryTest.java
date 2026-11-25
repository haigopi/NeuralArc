package com.neuralarc.service;

import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PollingPauseRegistryTest {
    @Test
    void nothingIsPausedUntilTheOperatorPauses() {
        PollingPauseRegistry registry = new PollingPauseRegistry();

        assertFalse(registry.isPaused(StrategyMode.PAPER, "ws-1"));
        assertFalse(registry.isPaused(StrategyMode.PAPER, null));
        assertFalse(registry.isAnyPaused());
    }

    @Test
    void pausingOneWorkspaceLeavesTheOthersAndTheOtherModeRunning() {
        PollingPauseRegistry registry = new PollingPauseRegistry();

        registry.pause(StrategyMode.PAPER, "ws-1");

        assertTrue(registry.isPaused(StrategyMode.PAPER, "ws-1"));
        assertFalse(registry.isPaused(StrategyMode.PAPER, "ws-2"));
        assertFalse(registry.isPaused(StrategyMode.LIVE, "ws-1"));
        assertFalse(registry.isPaused(StrategyMode.PAPER, null), "All Stocks is not paused by one workspace");
    }

    @Test
    void pausingAllStocksPausesEveryWorkspaceAndUnassignedRowsInThatMode() {
        PollingPauseRegistry registry = new PollingPauseRegistry();

        registry.pause(StrategyMode.LIVE, null);

        assertTrue(registry.isPaused(StrategyMode.LIVE, "ws-1"));
        assertTrue(registry.isPaused(StrategyMode.LIVE, null));
        assertFalse(registry.isPaused(StrategyMode.PAPER, "ws-1"));
        assertTrue(registry.isPaused(StrategyMode.LIVE, "ws-1"));
        assertFalse(registry.isPausedDirectly(StrategyMode.LIVE, "ws-1"), "inherited, not paused on its own");
    }

    @Test
    void resumingAWorkspaceDoesNotLiftAnAllStocksPause() {
        PollingPauseRegistry registry = new PollingPauseRegistry();
        registry.pause(StrategyMode.PAPER, null);

        registry.resume(StrategyMode.PAPER, "ws-1");

        assertTrue(registry.isPaused(StrategyMode.PAPER, "ws-1"));
    }

    @Test
    void resumingAllStocksClearsEveryPauseInThatModeButNotTheOther() {
        PollingPauseRegistry registry = new PollingPauseRegistry();
        registry.pause(StrategyMode.PAPER, "ws-1");
        registry.pause(StrategyMode.PAPER, null);
        registry.pause(StrategyMode.LIVE, "ws-9");

        registry.resume(StrategyMode.PAPER, null);

        assertFalse(registry.isPaused(StrategyMode.PAPER, "ws-1"));
        assertFalse(registry.isPaused(StrategyMode.PAPER, null));
        assertTrue(registry.isPaused(StrategyMode.LIVE, "ws-9"));
    }

    @Test
    void aBlankWorkspaceIdMeansAllStocksAndPausingTwiceIsHarmless() {
        PollingPauseRegistry registry = new PollingPauseRegistry();

        registry.pause(StrategyMode.PAPER, " ");
        registry.pause(StrategyMode.PAPER, null);
        registry.resume(StrategyMode.PAPER, "");

        assertFalse(registry.isPaused(StrategyMode.PAPER, "ws-1"));
        assertFalse(registry.isAnyPaused());
    }

    @Test
    void aStrategyIsPausedThroughItsModeAndWorkspace() {
        PollingPauseRegistry registry = new PollingPauseRegistry();
        registry.pause(StrategyMode.PAPER, "ws-1");

        assertFalse(registry.isPaused((Strategy) null));
    }
}
