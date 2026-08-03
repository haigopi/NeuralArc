package com.neuralarc.ui;

import com.neuralarc.model.RecommendationType;
import com.neuralarc.model.StrategyMode;

import java.math.BigDecimal;

record PortfolioCaptureConfig(
        PortfolioCaptureMode mode,
        PortfolioCaptureTargetType targetType,
        BigDecimal targetValue,
        boolean includeLosses,
        int monitoringIntervalSeconds,
        boolean autoStopAfterExecution,
        boolean includeOnlyActiveStrategies,
        PortfolioCaptureExecutionFlow executionFlow,
        StrategyMode reentryMode,
        int reentryQuantity,
        RecommendationType reentryRecommendationType,
        PortfolioCaptureSmartPicksStrategy reentrySmartPicksStrategy,
        boolean autoCleanPendingBeforeCycle,
        PortfolioCapturePullbackType pullbackType,
        BigDecimal pullbackValue
) {
    PortfolioCaptureConfig(
            PortfolioCaptureMode mode,
            PortfolioCaptureTargetType targetType,
            BigDecimal targetValue,
            boolean includeLosses,
            int monitoringIntervalSeconds,
            boolean autoStopAfterExecution,
            boolean includeOnlyActiveStrategies,
            PortfolioCaptureExecutionFlow executionFlow,
            StrategyMode reentryMode,
            int reentryQuantity,
            RecommendationType reentryRecommendationType,
            PortfolioCaptureSmartPicksStrategy reentrySmartPicksStrategy,
            boolean autoCleanPendingBeforeCycle
    ) {
        this(mode, targetType, targetValue, includeLosses, monitoringIntervalSeconds, autoStopAfterExecution,
                includeOnlyActiveStrategies, executionFlow, reentryMode, reentryQuantity, reentryRecommendationType,
                reentrySmartPicksStrategy, autoCleanPendingBeforeCycle,
                PortfolioCapturePullbackType.PERCENT_FROM_PEAK, BigDecimal.ZERO);
    }

    static PortfolioCaptureConfig captureNow() {
        return new PortfolioCaptureConfig(
                PortfolioCaptureMode.CAPTURE_NOW,
                PortfolioCaptureTargetType.PROFIT_AMOUNT,
                BigDecimal.ZERO,
                true,
                45,
                true,
                true,
                PortfolioCaptureExecutionFlow.EXECUTE_ONCE_AND_STOP,
                StrategyMode.PAPER,
                1,
                RecommendationType.SHORT_TERM,
                PortfolioCaptureSmartPicksStrategy.VOLATILE,
                false,
                PortfolioCapturePullbackType.PERCENT_FROM_PEAK,
                BigDecimal.ZERO
        );
    }

    boolean reentryEnabled() {
        return executionFlow == PortfolioCaptureExecutionFlow.CAPTURE_THEN_REENTER_ONCE
                || executionFlow == PortfolioCaptureExecutionFlow.CONTINUOUS_AUTOMATED_LOOP;
    }

    boolean continuousLoop() {
        return executionFlow == PortfolioCaptureExecutionFlow.CONTINUOUS_AUTOMATED_LOOP;
    }
}
