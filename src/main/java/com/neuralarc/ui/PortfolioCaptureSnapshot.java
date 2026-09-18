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
 *
 * <p>{@code rows} and their totals are what a liquidation <em>sells</em>; {@link #targetBasis()} is what
 * the target is <em>measured on</em>: the net open P&amp;L of every eligible position, losers included.
 * The two differ when losses are excluded — then only the winners are sold, but the target still has
 * to be met by the portfolio as a whole. Measuring it on the winners alone fired a $454 target while
 * the portfolio's net P&amp;L was about $250, and sold every profitable position.
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
        Instant calculatedAt,
        TargetBasis targetBasis
) {
    /** A snapshot whose target is measured on exactly the rows it sells. */
    PortfolioCaptureSnapshot(
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
        this(totalInvestment, marketValue, realizedPnl, unrealizedPnl, profitLossPercent, targetProgressPercent,
                eligibleCount, rows, calculatedAt, new TargetBasis(totalInvestment, marketValue, unrealizedPnl));
    }

    /** The net open position of every eligible row, winners and losers alike: what the target is measured on. */
    record TargetBasis(BigDecimal investment, BigDecimal marketValue, BigDecimal pnl) {
        BigDecimal pnlPercent() {
            return Monetary.round(percent(pnl, investment));
        }
    }

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
