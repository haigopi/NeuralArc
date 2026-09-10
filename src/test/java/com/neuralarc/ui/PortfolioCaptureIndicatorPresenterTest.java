package com.neuralarc.ui;

import com.neuralarc.model.RecommendationType;
import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioCaptureIndicatorPresenterTest {
    @Test
    void percentTargetRestatesTargetAsExpectedProfitAmount() {
        String text = PortfolioCaptureIndicatorPresenter.targetMonitoringText(
                config(PortfolioCaptureTargetType.PROFIT_PERCENT, new BigDecimal("5.00")),
                new BigDecimal("-34.43"),
                new BigDecimal("243.01")
        );

        assertEquals(
                "Armed | Open P&L -$34.43 (-14.17%) | Target +5.00% = +$12.15 | Progress 0.00% | Need +$46.58",
                text);
    }

    @Test
    void amountTargetRestatesTargetAsPercentOfCapital() {
        String text = PortfolioCaptureIndicatorPresenter.targetMonitoringText(
                config(PortfolioCaptureTargetType.PROFIT_AMOUNT, new BigDecimal("100.00")),
                new BigDecimal("75.00"),
                new BigDecimal("1000.00")
        );

        assertEquals(
                "Armed | Open P&L +$75.00 (+7.50%) | Target +$100.00 = +10.00% | Progress 75.00% | Need +$25.00",
                text);
    }

    @Test
    void progressStaysAtZeroWhileThePortfolioIsAtALoss() {
        String text = PortfolioCaptureIndicatorPresenter.targetMonitoringText(
                config(PortfolioCaptureTargetType.PROFIT_PERCENT, new BigDecimal("5.00")),
                new BigDecimal("-500.00"),
                new BigDecimal("1000.00")
        );

        assertTrue(text.contains("Progress 0.00%"), text);
        assertTrue(text.contains("Need +$550.00"), text);
    }

    @Test
    void progressIsCappedAtOneHundredAndReportsTargetMetOnceReached() {
        String text = PortfolioCaptureIndicatorPresenter.targetMonitoringText(
                config(PortfolioCaptureTargetType.PROFIT_AMOUNT, new BigDecimal("100.00")),
                new BigDecimal("250.00"),
                new BigDecimal("1000.00")
        );

        assertTrue(text.contains("Progress 100.00%"), text);
        assertTrue(text.endsWith("| Target met"), text);
    }

    @Test
    void percentTargetWithoutAllocatedCapitalSaysWhyProgressIsMissing() {
        String text = PortfolioCaptureIndicatorPresenter.targetMonitoringText(
                config(PortfolioCaptureTargetType.PROFIT_PERCENT, new BigDecimal("5.00")),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        assertEquals("Armed | Open P&L $0.00 | Target +5.00% | Waiting for allocated capital", text);
    }

    @Test
    void amountTargetWithoutAllocatedCapitalStillTracksProgress() {
        String text = PortfolioCaptureIndicatorPresenter.targetMonitoringText(
                config(PortfolioCaptureTargetType.PROFIT_AMOUNT, new BigDecimal("100.00")),
                new BigDecimal("40.00"),
                BigDecimal.ZERO
        );

        assertEquals("Armed | Open P&L +$40.00 | Target +$100.00 | Progress 40.00% | Need +$60.00", text);
    }

    @Test
    void unsetTargetIsReportedInsteadOfAMeaninglessProgressFigure() {
        String text = PortfolioCaptureIndicatorPresenter.targetMonitoringText(
                config(PortfolioCaptureTargetType.PROFIT_PERCENT, BigDecimal.ZERO),
                new BigDecimal("75.00"),
                new BigDecimal("1000.00")
        );

        assertTrue(text.endsWith("| No profit target configured"), text);
    }

    @Test
    void nonTargetModesRenderNoIndicator() {
        assertEquals("", PortfolioCaptureIndicatorPresenter.targetMonitoringText(
                PortfolioCaptureConfig.captureNow(), new BigDecimal("10.00"), new BigDecimal("100.00")));
        assertEquals("", PortfolioCaptureIndicatorPresenter.targetMonitoringText(
                null, new BigDecimal("10.00"), new BigDecimal("100.00")));
    }

    @Test
    void explanationDerivesTheExpectedProfitFromCapital() {
        String explanation = PortfolioCaptureIndicatorPresenter.targetMonitoringExplanation(
                config(PortfolioCaptureTargetType.PROFIT_PERCENT, new BigDecimal("5.00")),
                new BigDecimal("-34.43"),
                new BigDecimal("243.01")
        );

        assertTrue(explanation.contains("Capital still at risk in those positions is $243.01."), explanation);
        assertTrue(explanation.contains("Target +5.00% of that capital is an expected P&L of +$12.15."), explanation);
        assertTrue(explanation.contains("clamped to 0-100%"), explanation);
        assertTrue(explanation.contains("Need is the additional P&L required"), explanation);
    }

    @Test
    void explanationForNonTargetModeIsEmpty() {
        assertEquals("", PortfolioCaptureIndicatorPresenter.targetMonitoringExplanation(
                PortfolioCaptureConfig.captureNow(), BigDecimal.ZERO, BigDecimal.ZERO));
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
