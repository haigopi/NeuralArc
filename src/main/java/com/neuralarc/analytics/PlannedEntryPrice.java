package com.neuralarc.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Where a buy-the-dip strategy may plan its entry.
 *
 * <p>A planned buy must never sit above the market. Base buys are also never closer to it than {@link #MIN_BASE_BUY_DISCOUNT_PERCENT}. A limit above the current price fills at once at
 * whatever the market asks, so the plan's stop and target are measured from a price the operator never
 * agreed to — the trade starts worse than it was designed to be. These plans therefore aim a little
 * under the market (at least {@link #MIN_BASE_BUY_DISCOUNT_PERCENT}), and are rounded <em>down</em> to the cent so rounding itself can never cross the market.
 */
public final class PlannedEntryPrice {
    /** How far under the market a patient entry aims, in percent. */
    public static final BigDecimal DEFAULT_DISCOUNT_PERCENT = new BigDecimal("0.25");
    /** The least a base buy may sit under the market, in percent. See {@link #baseBuy}. */
    public static final BigDecimal MIN_BASE_BUY_DISCOUNT_PERCENT = new BigDecimal("8");
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

    /**
     * A base buy for a scanner recommendation: {@link #MIN_BASE_BUY_DISCOUNT_PERCENT} under the lowest of
     * the reference prices (today's open, today's low and the current price). The week's low is not a
     * floor here; the minimum discount wins.
     */
    public static BigDecimal baseBuy(BigDecimal... referencePrices) {
        BigDecimal reference = lowestPositive(referencePrices);
        return reference == null ? floor(BigDecimal.ZERO) : ceiling(reference);
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

    /**
     * Clamps a base buy to at least the minimum base-buy discount under the market. The market is the
     * <em>lowest</em> of the reference prices passed in (typically today's open, today's low and the
     * current price): a stock that opened at 5.98, dipped to 5.95 and trades at 5.96 is measured from
     * 5.95, so the base buy is never closer than 8% to any price it has traded at today. A plan already
     * under that level is kept. Used where a stored or recommended base buy is shown or placed, so a
     * stale trigger (a premarket high, a breakout level) can never sit above what the stock trades at.
     */
    public static BigDecimal belowMarket(BigDecimal plannedPrice, BigDecimal... referencePrices) {
        BigDecimal planned = plannedPrice == null ? BigDecimal.ZERO : plannedPrice;
        BigDecimal reference = lowestPositive(referencePrices);
        if (reference == null) {
            return floor(planned);
        }
        BigDecimal ceiling = ceiling(reference);
        BigDecimal clamped = floor(planned.min(ceiling));
        return positive(clamped) ? clamped : ceiling;
    }

    private static BigDecimal lowestPositive(BigDecimal... prices) {
        BigDecimal lowest = null;
        if (prices != null) {
            for (BigDecimal price : prices) {
                if (positive(price) && (lowest == null || price.compareTo(lowest) < 0)) {
                    lowest = price;
                }
            }
        }
        return lowest;
    }

    /** The highest price a plan may take: the minimum discount under the market, rounded down to the cent. */
    private static BigDecimal ceiling(BigDecimal currentPrice) {
        return floor(currentPrice.multiply(BigDecimal.ONE.subtract(discountFraction(MIN_BASE_BUY_DISCOUNT_PERCENT))));
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
