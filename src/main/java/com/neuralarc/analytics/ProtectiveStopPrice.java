package com.neuralarc.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Where a strategy's stop loss may sit relative to the price it buys at.
 *
 * <p>A stop must leave the trade room to breathe. Percentage stops computed off a low-priced entry
 * collapse onto the entry itself — a 0.5% stop under a $1.00 entry rounds to $1.00 — which either
 * stops the position out on the first tick of noise or is rejected outright for not being below the
 * base buy. Every stop is therefore held at least ten cents under the entry, and rounded <em>down</em>
 * to the cent so rounding can only widen that gap, never close it.
 *
 * <p>The one case the rule cannot serve is an entry below ten cents, where the gap will not fit. Such
 * a stop is kept strictly below the entry instead, and reported honestly rather than forced.
 */
public final class ProtectiveStopPrice {
    /** The closest a stop may sit under the entry it protects. */
    public static final BigDecimal MINIMUM_GAP = new BigDecimal("0.10");
    /** Fallback gap for entries too small to hold {@link #MINIMUM_GAP}. */
    private static final BigDecimal SUB_GAP_FALLBACK = new BigDecimal("0.90");
    private static final int CENTS = 2;

    private ProtectiveStopPrice() {
    }

    /**
     * Clamps an already-planned stop so it sits at least {@link #MINIMUM_GAP} under the entry.
     *
     * @param entryPrice  the price the strategy plans to buy at
     * @param plannedStop the stop as calculated; widened when it crowds the entry
     */
    public static BigDecimal belowEntry(BigDecimal entryPrice, BigDecimal plannedStop) {
        if (!positive(entryPrice)) {
            return floor(BigDecimal.ZERO);
        }
        BigDecimal furthestAllowed = entryPrice.subtract(MINIMUM_GAP);
        BigDecimal stop = positive(plannedStop) ? plannedStop.min(furthestAllowed) : furthestAllowed;
        BigDecimal floored = floor(stop);
        return positive(floored) ? floored : belowSubDimeEntry(entryPrice);
    }

    /**
     * The stop a percentage rule asks for, held to the same minimum gap. A zero or missing percentage
     * would otherwise plan a stop at the entry itself.
     */
    public static BigDecimal percentBelowEntry(BigDecimal entryPrice, BigDecimal stopLossPercent) {
        if (!positive(entryPrice)) {
            return floor(BigDecimal.ZERO);
        }
        BigDecimal fraction = stopLossPercent == null || stopLossPercent.signum() <= 0
                ? BigDecimal.ZERO
                : stopLossPercent.movePointLeft(2);
        return belowEntry(entryPrice, entryPrice.multiply(BigDecimal.ONE.subtract(fraction)));
    }

    /** A sub-dime entry cannot hold a ten-cent gap, so keep the stop proportionally below it. */
    private static BigDecimal belowSubDimeEntry(BigDecimal entryPrice) {
        BigDecimal proportional = floor(entryPrice.multiply(SUB_GAP_FALLBACK));
        return positive(proportional) ? proportional : floor(BigDecimal.ZERO);
    }

    private static BigDecimal floor(BigDecimal value) {
        return value.setScale(CENTS, RoundingMode.FLOOR);
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }
}
