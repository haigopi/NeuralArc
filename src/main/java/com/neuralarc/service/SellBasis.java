package com.neuralarc.service;

import com.neuralarc.model.StrategyOrder;
import org.json.JSONObject;

import java.math.BigDecimal;

/**
 * What one sell actually booked, measured against what the shares actually cost.
 *
 * <p>A strategy's own order history is not always the whole position. Where two strategies run the
 * same symbol, or shares were bought outside the app, a sell can close more shares than the strategy
 * ever recorded buying. Replaying only the strategy's own fills then prices the sale against a
 * fraction of it: eleven shares sold, one share's cost basis, a tenth of the real profit or loss.
 *
 * <p>So when a sell closes more than the strategy tracked and the broker's average entry for the whole
 * position was recorded at the time, that entry prices the whole sale. Otherwise the tracked cost
 * prices the tracked shares, exactly as before — a sell from before this was recorded, or one the
 * broker gave no cost for, keeps the number it has always shown rather than gaining an invented one.
 */
public final class SellBasis {
    private SellBasis() {
    }

    /**
     * @param fillPrice        what the sell filled at
     * @param filledQuantity   shares the sell actually closed
     * @param trackedQuantity  shares this strategy's own fills say it held
     * @param trackedAverageCost blended cost of those tracked shares
     * @param brokerAverageEntry broker's average entry for the whole position, or zero when unknown
     */
    public static Result of(
            BigDecimal fillPrice,
            BigDecimal filledQuantity,
            BigDecimal trackedQuantity,
            BigDecimal trackedAverageCost,
            BigDecimal brokerAverageEntry
    ) {
        BigDecimal quantity = orZero(filledQuantity);
        BigDecimal tracked = orZero(trackedQuantity).max(BigDecimal.ZERO);
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return new Result(BigDecimal.ZERO, orZero(trackedAverageCost), BigDecimal.ZERO, false, BigDecimal.ZERO);
        }

        BigDecimal untracked = quantity.subtract(tracked).max(BigDecimal.ZERO);
        boolean exceedsTracked = untracked.compareTo(BigDecimal.ZERO) > 0;
        if (exceedsTracked && positive(brokerAverageEntry)) {
            // The broker's entry covers every share sold, so it prices the whole sale.
            return new Result(
                    quantity,
                    brokerAverageEntry,
                    orZero(fillPrice).subtract(brokerAverageEntry).multiply(quantity),
                    true,
                    untracked
            );
        }

        BigDecimal basisQuantity = quantity.min(tracked);
        BigDecimal cost = orZero(trackedAverageCost);
        return new Result(
                basisQuantity,
                cost,
                orZero(fillPrice).subtract(cost).multiply(basisQuantity),
                false,
                untracked
        );
    }

    /** The broker's recorded average entry for a sell, or zero when none was stored. */
    public static BigDecimal brokerAverageEntry(StrategyOrder order) {
        if (order == null || order.rawResponseJson() == null || order.rawResponseJson().isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            String raw = new JSONObject(order.rawResponseJson())
                    .optString(StrategyService.BROKER_AVERAGE_ENTRY_JSON_KEY, "");
            return raw.isBlank() ? BigDecimal.ZERO : new BigDecimal(raw);
        } catch (Exception ignored) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * @param quantity        shares the realized figure covers
     * @param averageCost     the cost each of those shares is measured against
     * @param realized        profit or loss booked by this sell
     * @param brokerReconciled whether the broker's entry priced the sale rather than the tracked cost
     * @param untrackedShares shares sold that this strategy never recorded buying, reconciled or not
     */
    public record Result(
            BigDecimal quantity,
            BigDecimal averageCost,
            BigDecimal realized,
            boolean brokerReconciled,
            BigDecimal untrackedShares
    ) {
        /** True when shares were sold that the strategy's own history cannot account for. */
        public boolean hasUntrackedShares() {
            return untrackedShares != null && untrackedShares.compareTo(BigDecimal.ZERO) > 0;
        }

        /** True when this sell booked nothing, because no tracked shares remained to price it. */
        public boolean isEmpty() {
            return quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0;
        }
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }
}
