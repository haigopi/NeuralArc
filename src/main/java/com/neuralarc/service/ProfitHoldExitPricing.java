package com.neuralarc.service;

import com.neuralarc.model.ProfitHoldType;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Where a profit hold's resting sell order sits.
 *
 * <p>A profit hold used to be watched: the app polled, and when a price came back under the trail it
 * sold. A stock that spikes and falls between two polls is never seen, so the exit never happens —
 * which is exactly how a hold armed at $385 watched TSLA print $386.67 and slide to $372.75 without
 * selling. The hold now rests a real limit sell at the broker instead, so the fill does not depend
 * on the app looking at the right second.
 *
 * <p>The limit trails the highest price seen since arming, and never drops: an order may be raised
 * as the stock climbs, never lowered as it falls. It is also floored at the position's average cost
 * — a profit hold that exits below what the shares cost would not be holding a profit at all. When
 * the floor bites, the order rests above the trail and simply waits.
 */
public final class ProfitHoldExitPricing {
    /** The smallest move worth cancelling and replacing a resting order for. */
    public static final BigDecimal MIN_RAISE = new BigDecimal("0.01");

    /**
     * @param limitPrice   where the resting sell belongs
     * @param trailedPrice what the trail alone would have asked for, before the profit floor
     * @param flooredToCost true when the trail sat below average cost and the floor lifted it
     */
    public record Plan(BigDecimal limitPrice, BigDecimal trailedPrice, boolean flooredToCost) {
        public String describe(BigDecimal peak) {
            String base = "peak $" + Monetary.round(peak).toPlainString()
                    + " → limit $" + limitPrice.toPlainString();
            return flooredToCost
                    ? base + " (trail wanted $" + trailedPrice.toPlainString()
                            + ", held at average cost so the exit stays in profit)"
                    : base;
        }
    }

    private ProfitHoldExitPricing() {
    }

    /** The trail alone: {@code peak - amount}, or {@code peak - percent%}. */
    public static BigDecimal trailedPrice(BigDecimal peak, ProfitHoldType type, BigDecimal amount, BigDecimal percent) {
        BigDecimal safePeak = peak == null ? BigDecimal.ZERO : peak;
        if (safePeak.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        if (type == ProfitHoldType.FIXED_AMOUNT_TRAILING) {
            BigDecimal drop = amount == null ? BigDecimal.ZERO : amount;
            return Monetary.round(safePeak.subtract(drop));
        }
        BigDecimal pct = percent == null ? BigDecimal.ZERO : percent;
        return Monetary.round(safePeak.multiply(BigDecimal.ONE.subtract(pct.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP))));
    }

    /** Where the resting sell goes for this peak, with the average-cost floor applied. */
    public static Plan plan(BigDecimal peak, ProfitHoldType type, BigDecimal amount, BigDecimal percent, BigDecimal averageCost) {
        BigDecimal trailed = trailedPrice(peak, type, amount, percent);
        if (trailed.signum() <= 0) {
            // No peak yet means nothing to trail. Without this the average-cost floor below would
            // invent a resting sell at cost for a hold that has never armed.
            return new Plan(BigDecimal.ZERO, BigDecimal.ZERO, false);
        }
        BigDecimal floor = averageCost == null ? BigDecimal.ZERO : Monetary.round(averageCost);
        if (floor.signum() > 0 && trailed.compareTo(floor) < 0) {
            return new Plan(floor, trailed, true);
        }
        return new Plan(trailed, trailed, false);
    }

    /**
     * Whether a resting order at {@code restingLimit} should be cancelled and re-placed at
     * {@code newLimit}. Only a raise of at least {@link #MIN_RAISE} qualifies: the trail follows the
     * stock up, never down, and a cent of drift is not worth a cancel/replace round trip against the
     * broker's rate limit.
     */
    public static boolean shouldRaise(BigDecimal restingLimit, BigDecimal newLimit) {
        if (newLimit == null || newLimit.signum() <= 0) {
            return false;
        }
        if (restingLimit == null || restingLimit.signum() <= 0) {
            return true;
        }
        return newLimit.subtract(restingLimit).compareTo(MIN_RAISE) >= 0;
    }
}
