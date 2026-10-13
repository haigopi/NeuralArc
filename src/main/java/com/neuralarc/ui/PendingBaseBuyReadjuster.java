package com.neuralarc.ui;

import com.neuralarc.analytics.PlannedEntryPrice;
import com.neuralarc.model.MarketBar;
import com.neuralarc.model.Position;
import com.neuralarc.model.Strategy;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Keeps the base buy of a scanner row that is still waiting to be placed under the live price.
 *
 * <p>The base buy is fixed when a scan runs, but the price keeps moving. When it drops under a waiting
 * row's base buy, that row would be placed above the market. Each refresh therefore lowers such a row
 * to at least {@link PlannedEntryPrice#MIN_BASE_BUY_DISCOUNT_PERCENT} under the lowest of today's open,
 * today's low and the cached price. It only
 * ever lowers: a price that recovers never raises a base buy, and no broker call is involved, so a row
 * already placed at the broker is left alone. Manually added rows are held to the same rule.
 */
final class PendingBaseBuyReadjuster {
    private PendingBaseBuyReadjuster() {
    }

    record Adjustment(BigDecimal from, BigDecimal to, BigDecimal reference) {
        String event() {
            return "Pending base buy readjusted from $" + from.toPlainString() + " to $" + to.toPlainString()
                    + " (lowest of today's open, low and current price is $" + reference.toPlainString()
                    + "; base buy kept at least " + PlannedEntryPrice.MIN_BASE_BUY_DISCOUNT_PERCENT.toPlainString()
                    + "% under it).";
        }
    }

    /**
     * @param dailyBar today's session bar from the grid's snapshot, or null when it has not loaded; the
     *                 current price alone is used then
     * @return the lowered base buy when {@code strategy} waits above its allowed level, else empty
     */
    static Optional<Adjustment> plan(Strategy strategy, Position position, MarketBar dailyBar) {
        if (strategy == null || position == null || !PendingBaseBuyPlacementSupport.isPendingBaseBuyPlacement(strategy)) {
            return Optional.empty();
        }
        BigDecimal current = Monetary.round(position.getLastPrice());
        BigDecimal open = dailyBar == null ? BigDecimal.ZERO : dailyBar.open();
        BigDecimal low = dailyBar == null ? BigDecimal.ZERO : dailyBar.low();
        BigDecimal base = Monetary.round(strategy.baseBuyLimitPrice());
        BigDecimal reference = lowestPositive(current, open, low);
        if (reference.signum() <= 0 || base.signum() <= 0) {
            return Optional.empty();
        }
        BigDecimal target = PlannedEntryPrice.belowMarket(base, current, open, low);
        return target.compareTo(base) < 0 ? Optional.of(new Adjustment(base, target, reference)) : Optional.empty();
    }

    private static BigDecimal lowestPositive(BigDecimal... prices) {
        BigDecimal lowest = BigDecimal.ZERO;
        for (BigDecimal price : prices) {
            if (price != null && price.signum() > 0 && (lowest.signum() == 0 || price.compareTo(lowest) < 0)) {
                lowest = price;
            }
        }
        return lowest;
    }
}
