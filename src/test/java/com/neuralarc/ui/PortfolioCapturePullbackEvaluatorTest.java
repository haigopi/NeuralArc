package com.neuralarc.ui;

import com.neuralarc.model.RecommendationType;
import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PortfolioCapturePullbackEvaluatorTest {
    private final PortfolioCapturePullbackEvaluator evaluator = new PortfolioCapturePullbackEvaluator();

    @Test
    void waitsForMinimumThenLiquidatesAfterPercentagePullbackFromPeak() {
        PortfolioCaptureConfig config = config(PortfolioCaptureTargetType.PROFIT_AMOUNT, "500",
                PortfolioCapturePullbackType.PERCENT_FROM_PEAK, "10");

        PortfolioCapturePullbackEvaluator.Evaluation waiting = evaluator.evaluate(snapshot("450", "4.5"), config,
                false, BigDecimal.ZERO);
        PortfolioCapturePullbackEvaluator.Evaluation armed = evaluator.evaluate(snapshot("600", "6"), config,
                waiting.armed(), waiting.peakProfit());
        PortfolioCapturePullbackEvaluator.Evaluation newPeak = evaluator.evaluate(snapshot("800", "8"), config,
                armed.armed(), armed.peakProfit());
        PortfolioCapturePullbackEvaluator.Evaluation aboveThreshold = evaluator.evaluate(snapshot("721", "7.21"), config,
                newPeak.armed(), newPeak.peakProfit());
        PortfolioCapturePullbackEvaluator.Evaluation triggered = evaluator.evaluate(snapshot("720", "7.2"), config,
                aboveThreshold.armed(), aboveThreshold.peakProfit());

        assertFalse(waiting.armed());
        assertTrue(armed.armed());
        assertEquals(0, new BigDecimal("800").compareTo(newPeak.peakProfit()));
        assertFalse(aboveThreshold.liquidate());
        assertTrue(triggered.liquidate());
    }

    @Test
    void supportsProfitPercentMinimumAndFixedAmountPullback() {
        PortfolioCaptureConfig config = config(PortfolioCaptureTargetType.PROFIT_PERCENT, "5",
                PortfolioCapturePullbackType.AMOUNT_FROM_PEAK, "100");

        PortfolioCapturePullbackEvaluator.Evaluation armed = evaluator.evaluate(snapshot("600", "6"), config,
                false, BigDecimal.ZERO);
        PortfolioCapturePullbackEvaluator.Evaluation triggered = evaluator.evaluate(snapshot("500", "5"), config,
                armed.armed(), armed.peakProfit());

        assertTrue(armed.armed());
        assertTrue(triggered.liquidate());
    }

    private PortfolioCaptureConfig config(
            PortfolioCaptureTargetType targetType,
            String target,
            PortfolioCapturePullbackType pullbackType,
            String pullback
    ) {
        return new PortfolioCaptureConfig(
                PortfolioCaptureMode.PULLBACK_MONITORING,
                targetType,
                new BigDecimal(target),
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
                pullbackType,
                new BigDecimal(pullback)
        );
    }

    private PortfolioCaptureSnapshot snapshot(String pnl, String percent) {
        return new PortfolioCaptureSnapshot(BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal(pnl),
                new BigDecimal(percent), BigDecimal.ZERO, 0, List.of(), Instant.now());
    }
}
