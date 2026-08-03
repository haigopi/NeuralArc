package com.neuralarc.ui;

import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class PortfolioCapturePullbackEvaluator {
    Evaluation evaluate(
            PortfolioCaptureSnapshot snapshot,
            PortfolioCaptureConfig config,
            boolean armed,
            BigDecimal previousPeak
    ) {
        BigDecimal peak = Monetary.round(previousPeak == null ? BigDecimal.ZERO : previousPeak);
        if (snapshot == null || config == null || config.mode() != PortfolioCaptureMode.PULLBACK_MONITORING) {
            return new Evaluation(false, peak, false);
        }
        BigDecimal currentProfit = Monetary.round(snapshot.unrealizedPnl());
        boolean nowArmed = armed || minimumReached(snapshot, config);
        if (!nowArmed) {
            return new Evaluation(false, peak, false);
        }
        peak = peak.max(currentProfit);
        BigDecimal pullback = config.pullbackValue() == null ? BigDecimal.ZERO : config.pullbackValue();
        if (pullback.compareTo(BigDecimal.ZERO) <= 0 || peak.compareTo(BigDecimal.ZERO) <= 0) {
            return new Evaluation(true, peak, false);
        }
        BigDecimal liquidationThreshold = config.pullbackType() == PortfolioCapturePullbackType.AMOUNT_FROM_PEAK
                ? peak.subtract(pullback)
                : peak.subtract(peak.multiply(pullback)
                        .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP));
        return new Evaluation(true, peak, currentProfit.compareTo(liquidationThreshold) <= 0);
    }

    private boolean minimumReached(PortfolioCaptureSnapshot snapshot, PortfolioCaptureConfig config) {
        if (config.targetValue() == null) {
            return false;
        }
        BigDecimal current = config.targetType() == PortfolioCaptureTargetType.PROFIT_PERCENT
                ? snapshot.profitLossPercent()
                : snapshot.unrealizedPnl();
        return current.compareTo(config.targetValue()) >= 0;
    }

    record Evaluation(boolean armed, BigDecimal peakProfit, boolean liquidate) {
    }
}
