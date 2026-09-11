package com.neuralarc.service;

import com.neuralarc.api.AlpacaPositionData;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyOrderSide;
import com.neuralarc.model.StrategyOrderStatus;
import com.neuralarc.model.StrategyStage;
import com.neuralarc.model.TimeInForce;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Prices a target sell that replaces or restores an earlier one.
 *
 * <p>A replacement is never priced at the market. Doing so turned a resting profit target into a
 * marketable order: a sell restored after it went missing at the broker filled at the opening price,
 * below the position's cost, and a sell resized after an average-down would have done the same to
 * the whole enlarged position. The replacement starts from the earlier order's own limit (else the
 * strategy's target) and keeps that order's time in force, so a GTC target is not quietly turned
 * into a DAY order that lapses at the close.
 *
 * <p>When the strategy's own buys filled after that order was placed — an average-down — the limit
 * is rescaled by new average ÷ old average, keeping the same profit margin over the blended cost.
 * A change the strategy's buys do not explain (a transfer, a manual broker trade) leaves the limit
 * unchanged, because there is no basis for moving it.
 */
final class TargetSellPricing {
    private static final int RATIO_SCALE = 8;

    private TargetSellPricing() {
    }

    /** A replacement target sell's limit, time in force, and the factor applied to the earlier limit. */
    record Replacement(BigDecimal limitPrice, TimeInForce timeInForce, BigDecimal rescaleFactor) {
        boolean repriced() {
            return rescaleFactor.compareTo(BigDecimal.ONE) != 0;
        }
    }

    /**
     * @param strategy the strategy whose target sell is being replaced or restored.
     * @param previous the order being replaced, or the most recent one lost at the broker; null if none.
     * @param position the broker position now.
     * @param orders   the strategy's orders, searched for its own buys filled since {@code previous}.
     */
    static Replacement forReplacement(
            Strategy strategy,
            StrategyOrder previous,
            AlpacaPositionData position,
            List<StrategyOrder> orders
    ) {
        BigDecimal base = previous != null && positive(previous.limitPrice())
                ? previous.limitPrice()
                : strategy.targetSellPrice();
        TimeInForce timeInForce = previous == null || previous.timeInForce() == null
                ? TimeInForce.DAY
                : previous.timeInForce();
        Optional<Averages> averages = averagesAroundAverageDown(previous, position, orders);
        // Rescale a profit target only. A limit at or below the old cost is a deliberate loss exit.
        if (averages.isPresent() && base.compareTo(averages.get().before()) > 0) {
            BigDecimal factor = averages.get().after().divide(averages.get().before(), RATIO_SCALE, RoundingMode.HALF_UP);
            return new Replacement(Monetary.round(base.multiply(factor)), timeInForce, factor);
        }
        return new Replacement(Monetary.round(base), timeInForce, BigDecimal.ONE);
    }

    /**
     * The target sell to price a replacement from: the most recent one that did not fill — the one
     * just cancelled for a resize, or the one lost at the broker. Profit exits are excluded: they are
     * placed near the market on a pullback, and reusing that price for a target would sell low. Null
     * when there is none, in which case the strategy's target is used.
     */
    static StrategyOrder latestUnfilledTargetSell(List<StrategyOrder> orders) {
        if (orders == null) {
            return null;
        }
        return orders.stream()
                .filter(order -> order.side() == StrategyOrderSide.SELL)
                .filter(order -> order.stage() == StrategyStage.TARGET_SELL)
                .filter(order -> order.status() != StrategyOrderStatus.FILLED
                        && order.status() != StrategyOrderStatus.PARTIALLY_FILLED)
                .max(Comparator.comparing(StrategyOrder::submittedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(StrategyOrder::id))
                .orElse(null);
    }

    private record Averages(BigDecimal before, BigDecimal after) {
    }

    /**
     * The average cost before and after the strategy's own buys that filled since {@code previous}
     * was placed. The "after" figure is the broker's; "before" backs those fills out of it, so it
     * works even for a position whose earlier buys were never recorded locally.
     */
    private static Optional<Averages> averagesAroundAverageDown(
            StrategyOrder previous,
            AlpacaPositionData position,
            List<StrategyOrder> orders
    ) {
        if (previous == null || previous.submittedAt() == null || orders == null
                || position == null || !position.exists()
                || !positive(position.quantity()) || !positive(position.avgEntryPrice())) {
            return Optional.empty();
        }
        Instant placedAt = previous.submittedAt();
        BigDecimal addedQuantity = BigDecimal.ZERO;
        BigDecimal addedCost = BigDecimal.ZERO;
        for (StrategyOrder order : orders) {
            if (order.side() != StrategyOrderSide.BUY) {
                continue;
            }
            if (order.status() != StrategyOrderStatus.FILLED && order.status() != StrategyOrderStatus.PARTIALLY_FILLED) {
                continue;
            }
            Instant filledAt = order.filledAt() != null ? order.filledAt() : order.submittedAt();
            if (filledAt == null || !filledAt.isAfter(placedAt)) {
                continue;
            }
            BigDecimal quantity = filledQuantity(order);
            BigDecimal price = positive(order.filledAveragePrice()) ? order.filledAveragePrice() : order.limitPrice();
            if (!positive(quantity) || !positive(price)) {
                continue;
            }
            addedQuantity = addedQuantity.add(quantity);
            addedCost = addedCost.add(quantity.multiply(price));
        }
        BigDecimal quantityNow = position.quantity();
        BigDecimal quantityBefore = quantityNow.subtract(addedQuantity);
        if (!positive(addedQuantity) || !positive(quantityBefore)) {
            return Optional.empty();
        }
        BigDecimal averageNow = position.avgEntryPrice();
        BigDecimal averageBefore = quantityNow.multiply(averageNow).subtract(addedCost)
                .divide(quantityBefore, RATIO_SCALE, RoundingMode.HALF_UP);
        return positive(averageBefore) ? Optional.of(new Averages(averageBefore, averageNow)) : Optional.empty();
    }

    private static BigDecimal filledQuantity(StrategyOrder order) {
        if (positive(order.filledQuantity())) {
            return order.filledQuantity();
        }
        return order.status() == StrategyOrderStatus.FILLED && positive(order.requestedQuantity())
                ? order.requestedQuantity()
                : BigDecimal.ZERO;
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }
}
