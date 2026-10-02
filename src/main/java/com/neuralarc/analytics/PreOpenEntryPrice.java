package com.neuralarc.analytics;

import com.neuralarc.model.MarketBar;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.util.List;

/**
 * Where an expired day order should go back in, worked out before the bell from yesterday's session.
 *
 * <p>A DAY entry that expired unfilled is a plan whose price the market never came down to. Re-posting
 * the same limit the next morning repeats the same miss, so the limit is recomputed against the
 * session that just closed: a touch under where the stock actually finished, and never below the
 * week's low, which is the floor a limit can realistically fill at.
 *
 * <p>It is also never above yesterday's close. A buy limit above the market fills instantly at
 * whatever is asked, and the plan's stop and target would then be measured from a price nobody chose.
 */
public final class PreOpenEntryPrice {
    /** How far under yesterday's close a repositioned entry aims. */
    public static final BigDecimal DEFAULT_DISCOUNT_PERCENT = PlannedEntryPrice.DEFAULT_DISCOUNT_PERCENT;

    /**
     * @param price       where the entry belongs, or zero when yesterday's session cannot be read
     * @param previousClose what the stock closed at, for the log line
     * @param weekLow     the floor that kept the price fillable, or zero when unknown
     */
    public record Plan(BigDecimal price, BigDecimal previousClose, BigDecimal weekLow) {
        public static Plan unknown() {
            return new Plan(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        public boolean known() {
            return price.signum() > 0;
        }

        /** Why the price is what it is, for the event log an operator reads the next morning. */
        public String describe() {
            if (!known()) {
                return "yesterday's session could not be read";
            }
            String floor = weekLow.signum() > 0 ? ", floored at the week's low $" + weekLow.toPlainString() : "";
            return "$" + price.toPlainString() + " from yesterday's close $" + previousClose.toPlainString() + floor;
        }
    }

    private PreOpenEntryPrice() {
    }

    /** The new entry for {@code bars}, whose last entry is the session that just closed. */
    public static Plan fromPreviousSession(List<MarketBar> bars) {
        return fromPreviousSession(bars, DEFAULT_DISCOUNT_PERCENT);
    }

    public static Plan fromPreviousSession(List<MarketBar> bars, BigDecimal discountPercent) {
        if (bars == null || bars.isEmpty()) {
            return Plan.unknown();
        }
        MarketBar previous = bars.get(bars.size() - 1);
        BigDecimal close = previous == null ? null : previous.close();
        if (close == null || close.signum() <= 0) {
            return Plan.unknown();
        }
        BigDecimal weekLow = RecentLow.weekLow(bars, BigDecimal.ZERO);
        BigDecimal price = PlannedEntryPrice.atOrBelowMarket(close, weekLow, discountPercent);
        return price.signum() <= 0
                ? Plan.unknown()
                : new Plan(price, Monetary.round(close), Monetary.round(weekLow));
    }
}
