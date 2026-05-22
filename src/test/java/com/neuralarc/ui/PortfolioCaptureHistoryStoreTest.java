package com.neuralarc.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PortfolioCaptureHistoryStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void appendsCaptureEntriesAndSummarizesTotalPnl() {
        PortfolioCaptureHistoryStore store = new PortfolioCaptureHistoryStore(tempDir.resolve("capture-history.json"));

        store.append(entry("1", 1, "20.25", "18.50", "200.00", 2));
        PortfolioCaptureHistoryStore.Summary summary = store.append(entry("2", 2, "-5.00", "-7.25", "150.00", 1));

        assertEquals(2, summary.captureCount());
        assertEquals(3, summary.capturedStocks());
        assertEquals(new BigDecimal("15.25"), summary.estimatedPnl());
        assertEquals(new BigDecimal("11.25"), summary.actualPnl());
        assertEquals(new BigDecimal("350.00"), summary.actualBrokerExecutionValue());
        assertEquals(Instant.parse("2026-05-21T14:02:00Z"), summary.lastTimestamp());
    }

    @Test
    void restoresSummaryFromDisk() {
        Path historyFile = tempDir.resolve("capture-history.json");
        PortfolioCaptureHistoryStore store = new PortfolioCaptureHistoryStore(historyFile);
        store.append(entry("1", 1, "20.00", "18.00", "200.00", 2));

        PortfolioCaptureHistoryStore.Summary restored = new PortfolioCaptureHistoryStore(historyFile).summary();

        assertEquals(1, restored.captureCount());
        assertEquals(2, restored.capturedStocks());
        assertEquals(new BigDecimal("18.00"), restored.actualPnl());
    }

    private PortfolioCaptureHistoryStore.Entry entry(
            String id,
            int loopNumber,
            String estimatedPnl,
            String actualPnl,
            String actualValue,
            int capturedCount
    ) {
        return new PortfolioCaptureHistoryStore.Entry(
                id,
                Instant.parse("2026-05-21T14:0" + loopNumber + ":00Z"),
                loopNumber,
                "AUTO_CAPTURE_TARGET_REACHED",
                PortfolioCaptureExecutionFlow.CONTINUOUS_AUTOMATED_LOOP.name(),
                capturedCount,
                new BigDecimal("100.00"),
                new BigDecimal("120.00"),
                new BigDecimal(actualValue),
                new BigDecimal(estimatedPnl),
                new BigDecimal(actualPnl),
                new BigDecimal("0.00")
        );
    }
}
