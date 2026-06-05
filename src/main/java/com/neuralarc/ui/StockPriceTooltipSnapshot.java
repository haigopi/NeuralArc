package com.neuralarc.ui;

import com.neuralarc.model.MarketBar;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

record StockPriceTooltipSnapshot(
        String symbol,
        BigDecimal open,
        BigDecimal highSoFar,
        BigDecimal lowSoFar,
        BigDecimal current,
        Instant loadedAt
) {
    private static final StockPriceTooltipSnapshot EMPTY = new StockPriceTooltipSnapshot(
            "", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, Instant.EPOCH
    );

    static StockPriceTooltipSnapshot empty() {
        return EMPTY;
    }

    static StockPriceTooltipSnapshot fromBars(String symbol, List<MarketBar> bars, BigDecimal fallbackCurrent) {
        if (bars == null || bars.isEmpty()) {
            BigDecimal current = positive(fallbackCurrent) ? Monetary.round(fallbackCurrent) : BigDecimal.ZERO;
            return new StockPriceTooltipSnapshot(symbol, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, current, Instant.now());
        }
        MarketBar first = bars.getFirst();
        BigDecimal open = first.open();
        BigDecimal high = BigDecimal.ZERO;
        BigDecimal low = BigDecimal.ZERO;
        BigDecimal current = BigDecimal.ZERO;
        for (MarketBar bar : bars) {
            if (bar == null) {
                continue;
            }
            if (positive(bar.high()) && (!positive(high) || bar.high().compareTo(high) > 0)) {
                high = bar.high();
            }
            if (positive(bar.low()) && (!positive(low) || bar.low().compareTo(low) < 0)) {
                low = bar.low();
            }
            if (positive(bar.close())) {
                current = bar.close();
            }
        }
        if (!positive(current) && positive(fallbackCurrent)) {
            current = fallbackCurrent;
        }
        return new StockPriceTooltipSnapshot(
                symbol,
                round(open),
                round(high),
                round(low),
                round(current),
                Instant.now()
        );
    }

    boolean stale(long ttlMillis) {
        return loadedAt == null || loadedAt.plusMillis(ttlMillis).isBefore(Instant.now());
    }

    String tooltipText() {
        return TooltipStyler.text("Open: " + money(open)
                + "\nHigh so far: " + money(highSoFar)
                + "\nLow so far: " + money(lowSoFar)
                + "\nCurrent: " + money(current));
    }

    private static String money(BigDecimal value) {
        return positive(value) ? "$" + Monetary.round(value).toPlainString() : "-";
    }

    private static BigDecimal round(BigDecimal value) {
        return positive(value) ? Monetary.round(value) : BigDecimal.ZERO;
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }
}
