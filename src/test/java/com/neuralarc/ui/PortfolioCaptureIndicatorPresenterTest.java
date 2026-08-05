package com.neuralarc.ui;

import com.neuralarc.model.RecommendationType;
import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioCaptureIndicatorPresenterTest {
    @Test
    void targetMonitoringTextExplainsTriggerAndEligiblePositions() {
        PortfolioCaptureSnapshot snapshot = new PortfolioCaptureSnapshot(
                new BigDecimal("1000.00"),
                new BigDecimal("1075.00"),
                new BigDecimal("75.00"),
                new BigDecimal("7.50"),
                new BigDecimal("75.00"),
                3,
                List.of(),
                Instant.parse("2026-08-05T14:00:00Z")
        );
        PortfolioCaptureConfig config = config(PortfolioCaptureTargetType.PROFIT_AMOUNT, new BigDecimal("100.00"));

        String text = PortfolioCaptureIndicatorPresenter.targetMonitoringText(
                snapshot,
                config,
                new BigDecimal("75.00")
        );

        assertEquals("Armed | P&L $75.00 | Target $100.00 | 75.00%", text);
    }

    @Test
    void targetMonitoringTextUsesPercentTriggerCopy() {
        PortfolioCaptureSnapshot snapshot = new PortfolioCaptureSnapshot(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("40.00"),
                2,
                List.of(),
                Instant.parse("2026-08-05T14:00:00Z")
        );
        PortfolioCaptureConfig config = config(PortfolioCaptureTargetType.PROFIT_PERCENT, new BigDecimal("5.00"));

        String text = PortfolioCaptureIndicatorPresenter.targetMonitoringText(
                snapshot,
                config,
                new BigDecimal("42.50")
        );

        assertTrue(text.contains("Target 5.00%"));
        assertTrue(text.endsWith("40.00%"));
    }

    private PortfolioCaptureConfig config(PortfolioCaptureTargetType targetType, BigDecimal targetValue) {
        return new PortfolioCaptureConfig(
                PortfolioCaptureMode.TARGET_MONITORING,
                targetType,
                targetValue,
                true,
                45,
                true,
                true,
                PortfolioCaptureExecutionFlow.EXECUTE_ONCE_AND_STOP,
                StrategyMode.PAPER,
                1,
                RecommendationType.SHORT_TERM,
                PortfolioCaptureSmartPicksStrategy.VOLATILE,
                false
        );
    }
}
