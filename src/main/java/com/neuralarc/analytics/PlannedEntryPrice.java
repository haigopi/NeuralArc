package com.neuralarc.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Where a buy-the-dip strategy may plan its entry.
 *
 * <p>A planned buy must never sit above the market. A limit above the current price fills at once at
 * whatever the market asks, so the plan's stop and target are measured from a price the operator never
 * agreed to — the trade starts worse than it was designed to be. These plans therefore aim a little
 * under the market, never below the week's low (a limit under everything the stock has traded all week
 * rarely fills), and are rounded <em>down</em> to the cent so rounding itself can never cross the market.
 */
public final class PlannedEntryPrice {
    /** How far under the market a patient entry aims, in percent. */
    public static final BigDecimal DEFAULT_DISCOUNT_PERCENT = new BigDecimal("0.25");
    private static final BigDecimal MAX_DISCOUNT_PERCENT = new BigDecimal("50");
    private static final int CENTS = 2;

    private PlannedEntryPrice() {
    }

    /**
     * @param currentPrice   the latest traded price; nothing can be planned without it
     * @param weekLow        the lowest price actually traded in the last week, or zero when unknown
     * @param discountPercent how far under the market to aim before the week's low is considered
     */
    public static BigDecimal atOrBelowMarket(BigDecimal currentPrice, BigDecimal weekLow, BigDecimal discountPercent) {
        if (!positive(currentPrice)) {
            return floor(BigDecimal.ZERO);
        }
        BigDecimal discounted = currentPrice.multiply(BigDecimal.ONE.subtract(discountFraction(discountPercent)));
        // Below the week's low a limit rarely fills, so the week's low is the floor, not a target.
        BigDecimal planned = positive(weekLow) && weekLow.compareTo(currentPrice) < 0
                ? discounted.max(weekLow)
                : discounted;
        BigDecimal floored = floor(planned.min(currentPrice));
        return positive(floored) ? floored : floor(currentPrice);
    }

    /** Clamps an already-planned price to the market: the last guard before a plan is stored. */
    public static BigDecimal notAboveMarket(BigDecimal plannedPrice, BigDecimal currentPrice) {
        BigDecimal planned = plannedPrice == null ? BigDecimal.ZERO : plannedPrice;
        if (!positive(currentPrice)) {
            return floor(planned);
        }
        BigDecimal clamped = floor(planned.min(currentPrice));
        return positive(clamped) ? clamped : floor(currentPrice);
    }

    private static BigDecimal discountFraction(BigDecimal discountPercent) {
        if (discountPercent == null || discountPercent.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return discountPercent.min(MAX_DISCOUNT_PERCENT).movePointLeft(2);
    }

    private static BigDecimal floor(BigDecimal value) {
        return value.setScale(CENTS, RoundingMode.FLOOR);
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }
}
