package com.neuralarc.ui;

import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

/**
 * One evaluation of the portfolio a liquidation run would act on.
 *
 * <p>{@code realizedPnl} is already banked by earlier closed trades and can never be captured again;
 * {@code unrealizedPnl} is the open P&amp;L of {@code rows} — precisely what liquidating now would
 * realize. Profit targets are therefore evaluated against the unrealized figure only, and
 * {@code profitLossPercent} is that figure over {@code totalInvestment}, the capital still at risk.
 * Keeping the two apart matters: folding realized P&amp;L into the target basis previously let banked
 * profit from closed trades satisfy a percent target and liquidate open positions at a loss.
 */
record PortfolioCaptureSnapshot(
        BigDecimal totalInvestment,
        BigDecimal marketValue,
        BigDecimal realizedPnl,
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
                Monetary.zero(),
                0,
                List.of(),
                Instant.now()
        );
    }

    /** Banked plus open P&L — reporting only; never the basis for a liquidation trigger. */
    BigDecimal totalPnl() {
        return Monetary.round(realizedPnl.add(unrealizedPnl));
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
