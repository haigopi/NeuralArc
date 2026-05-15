package com.neuralarc.ui;

import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

record PortfolioCaptureSnapshot(
        BigDecimal totalInvestment,
        BigDecimal marketValue,
        BigDecimal unrealizedPnl,
        BigDecimal profitLossPercent,
        BigDecimal targetProgressPercent,
        int eligibleCount,
        List<Row> rows,
        Instant calculatedAt
) {
    static PortfolioCaptureSnapshot empty() {
        return new PortfolioCaptureSnapshot(
                Monetary.zero(),
                Monetary.zero(),
                Monetary.zero(),
                Monetary.zero(),
                Monetary.zero(),
                0,
                List.of(),
                Instant.now()
        );
    }

    record Row(
            String strategyId,
            String symbol,
            int quantity,
            BigDecimal averageCost,
            BigDecimal marketPrice,
            BigDecimal investment,
            BigDecimal marketValue,
            BigDecimal estimatedPnl
    ) {
    }

    static BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return Monetary.zero();
        }
        return numerator.multiply(BigDecimal.valueOf(100))
                .divide(denominator, 4, RoundingMode.HALF_UP);
    }
}
