package com.neuralarc.service;

import com.neuralarc.util.Monetary;

import java.math.BigDecimal;

/**
 * Detects a stop loss that could never have been valid downside protection and computes a safe
 * replacement.
 *
 * <p>A downside stop must sit <em>below</em> what the position cost. A stop above the average
 * entry price is misconfigured — and because a stop fires when price falls to or below it, such a
 * stop fires the moment it is evaluated and liquidates a healthy position at an unintended price.
 *
 * <p>Both conditions are required before correcting. Reacting to "stop is above current price"
 * alone would match every legitimate stop-loss trigger too (that is exactly what a trigger looks
 * like) and would silently disable stop-loss protection entirely.
 */
final class StopLossSanityGuard {
    /** Replacement stop distance below the current price, as a fraction. */
    private static final BigDecimal CORRECTION_FACTOR = new BigDecimal("0.90");

    private StopLossSanityGuard() {
    }

    static boolean isMisconfigured(BigDecimal stopThreshold, BigDecimal latestPrice, BigDecimal averageEntryPrice) {
        if (!positive(stopThreshold) || !positive(latestPrice) || !positive(averageEntryPrice)) {
            return false;
        }
        // Would fire right now AND was never below the cost basis, so it is not a real stop.
        return stopThreshold.compareTo(latestPrice) >= 0
                && stopThreshold.compareTo(averageEntryPrice) >= 0;
    }

    /** 10% below the current price, which restores genuine downside protection. */
    static BigDecimal correctedStopPrice(BigDecimal latestPrice) {
        return Monetary.round(latestPrice.multiply(CORRECTION_FACTOR));
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }
}
