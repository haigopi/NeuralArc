package com.neuralarc.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.neuralarc.model.RecommendationType;
import com.neuralarc.model.StrategyMode;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioCaptureStateStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void persistsAndRestoresMonitoringState() {
        PortfolioCaptureStateStore store = new PortfolioCaptureStateStore(tempDir.resolve("capture-state.json"));
        PortfolioCaptureConfig config = new PortfolioCaptureConfig(
                PortfolioCaptureMode.TARGET_MONITORING,
                PortfolioCaptureTargetType.PROFIT_PERCENT,
                new BigDecimal("7.5"),
                false,
                2,
                true,
                true,
                PortfolioCaptureExecutionFlow.CONTINUOUS_AUTOMATED_LOOP,
                StrategyMode.LIVE,
                3,
                RecommendationType.HIGH_RISK_SHORT_TERM,
                PortfolioCaptureSmartPicksStrategy.DIVERSIFIED_TOP_20,
                true
        );
        Instant timestamp = Instant.parse("2026-05-15T14:30:00Z");

        store.save(new PortfolioCaptureStateStore.State(true, config, timestamp, new BigDecimal("12345.67"),
                StrategyMode.LIVE, "workspace-1"));

        Optional<PortfolioCaptureStateStore.State> restored = store.load();
        assertTrue(restored.isPresent());
        assertTrue(restored.get().enabled());
        assertEquals(PortfolioCaptureTargetType.PROFIT_PERCENT, restored.get().config().targetType());
        assertEquals(0, new BigDecimal("7.50").compareTo(restored.get().config().targetValue()));
        assertFalse(restored.get().config().includeLosses());
        assertEquals(2, restored.get().config().monitoringIntervalSeconds());
        assertEquals(PortfolioCaptureExecutionFlow.CONTINUOUS_AUTOMATED_LOOP, restored.get().config().executionFlow());
        assertEquals(StrategyMode.LIVE, restored.get().config().reentryMode());
        assertEquals(3, restored.get().config().reentryQuantity());
        assertEquals(RecommendationType.HIGH_RISK_SHORT_TERM, restored.get().config().reentryRecommendationType());
        assertEquals(PortfolioCaptureSmartPicksStrategy.DIVERSIFIED_TOP_20, restored.get().config().reentrySmartPicksStrategy());
        assertTrue(restored.get().config().autoCleanPendingBeforeCycle());
        assertEquals(timestamp, restored.get().lastMonitoringTimestamp());
        assertEquals(new BigDecimal("12345.67"), restored.get().lastCalculatedPortfolioValue());
        assertEquals(StrategyMode.LIVE, restored.get().mode());
        assertEquals("workspace-1", restored.get().workspaceId());
    }

    @Test
    void loadsSmartPicksStrategyFromLegacyLuckyKey() throws Exception {
        Path stateFile = tempDir.resolve("capture-state.json");
        java.nio.file.Files.writeString(stateFile, """
                {
                  "enabled": true,
                  "targetType": "PROFIT_AMOUNT",
                  "targetValue": "100",
                  "reentryLuckyStrategy": "DIVERSIFIED_TOP_20"
                }
                """);
        PortfolioCaptureStateStore store = new PortfolioCaptureStateStore(stateFile);

        Optional<PortfolioCaptureStateStore.State> restored = store.load();
        assertTrue(restored.isPresent());
        assertEquals(PortfolioCaptureSmartPicksStrategy.DIVERSIFIED_TOP_20, restored.get().config().reentrySmartPicksStrategy());
        assertEquals(45, restored.get().config().monitoringIntervalSeconds());
    }

    @Test
    void clearDisablesStoredMonitoring() {
        PortfolioCaptureStateStore store = new PortfolioCaptureStateStore(tempDir.resolve("capture-state.json"));
        store.clear();

        Optional<PortfolioCaptureStateStore.State> restored = store.load();
        assertTrue(restored.isPresent());
        assertFalse(restored.get().enabled());
    }
}
