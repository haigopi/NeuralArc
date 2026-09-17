package com.neuralarc.api;

import com.neuralarc.util.Monetary;

import java.math.BigDecimal;

public record AlpacaPositionData(
        String symbol,
        BigDecimal quantity,
        BigDecimal avgEntryPrice,
        BigDecimal marketPrice,
        String rawJson
) {
    public AlpacaPositionData {
        symbol = symbol == null ? "" : symbol;
        quantity = Monetary.round(quantity);
        avgEntryPrice = Monetary.round(avgEntryPrice);
        marketPrice = Monetary.round(marketPrice);
        rawJson = rawJson == null ? "{}" : rawJson;
    }

    /**
     * A long position this account holds. Deliberately false for a short: the strategy engine is
     * long-only, and a short read as "has a position" would have it sell more of something already
     * sold short. Reading a short as flat makes the engine buy instead, which covers it.
     *
     * <p>For "is there any exposure at all" - display, totals, reconciliation - use
     * {@link #hasExposure()}, which counts shorts.
     */
    public boolean exists() {
        return !symbol.isBlank() && quantity.compareTo(BigDecimal.ZERO) > 0;
    }

    /** Any open exposure, long or short. What the account actually holds. */
    public boolean hasExposure() {
        return !symbol.isBlank() && quantity.signum() != 0;
    }

    /** True when the account is short this symbol: shares owed rather than owned. */
    public boolean isShort() {
        return !symbol.isBlank() && quantity.signum() < 0;
    }
}

