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
        // Measured on the net open P&L, like the target, so excluded losers cannot inflate the peak.
        BigDecimal currentProfit = Monetary.round(snapshot.targetBasis().pnl());
        boolean nowArmed = armed || minimumReached(snapshot, config);
        if (!nowArmed) {
            return new Evaluation(false, peak, false);
        }
        peak = peak.max(currentProfit);
        BigDecimal pullback = config.pullbackValue() == null ? BigDecimal.ZERO : config.pullbackValue();
        if (pullback.compareTo(BigDecimal.ZERO) <= 0 || peak.compareTo(BigDecimal.ZERO) <= 0) {
            return new Evaluation(true, peak, false);
        }
        BigDecimal liquidationThreshold = liquidationThreshold(config, peak);
        return new Evaluation(true, peak, currentProfit.compareTo(liquidationThreshold) <= 0);
    }

    /** The open profit at or below which an armed pullback liquidates, given the peak so far. */
    static BigDecimal liquidationThreshold(PortfolioCaptureConfig config, BigDecimal peak) {
        BigDecimal pullback = config.pullbackValue() == null ? BigDecimal.ZERO : config.pullbackValue();
        return config.pullbackType() == PortfolioCapturePullbackType.AMOUNT_FROM_PEAK
                ? peak.subtract(pullback)
                : peak.subtract(peak.multiply(pullback)
                        .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP));
    }

    /**
     * Whether the minimum profit that arms pullback tracking has been reached. Measured on open P&L
     * only — banked realized P&L cannot be captured again, so it must not arm a pullback exit — and a
     * non-positive target never arms, since there would be no profit to protect.
     */
    private boolean minimumReached(PortfolioCaptureSnapshot snapshot, PortfolioCaptureConfig config) {
        BigDecimal target = config.targetValue();
        if (target == null || target.compareTo(BigDecimal.ZERO) <= 0 || snapshot.rows().isEmpty()) {
            return false;
        }
        BigDecimal current = config.targetType() == PortfolioCaptureTargetType.PROFIT_PERCENT
                ? snapshot.targetBasis().pnlPercent()
                : snapshot.targetBasis().pnl();
        return current.compareTo(BigDecimal.ZERO) > 0 && current.compareTo(target) >= 0;
    }

    record Evaluation(boolean armed, BigDecimal peakProfit, boolean liquidate) {
    }
}
