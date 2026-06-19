package com.neuralarc.service;

import java.math.BigDecimal;

/**
 * The outcome of a single after-close Auto Adjust Risk &amp; Stop Loss evaluation for one strategy:
 * the new stop-loss and loss buy-level prices, the advanced day count, the reference price to compare
 * against next time, the market date this adjustment belongs to, and the direction taken.
 *
 * <p>Pure value type produced by {@link AutoRiskAdjustmentEngine}; the service applies it to the
 * {@link com.neuralarc.model.Strategy} and persists.
 */
public record AutoRiskAdjustment(
        BigDecimal newStopLossPrice,
        BigDecimal newBuyLimit1Price,
        BigDecimal newBuyLimit2Price,
        int newDayCount,
        String marketDate,
        BigDecimal newReferencePrice,
        Direction direction,
        String description
) {
    public enum Direction { DECREASE, INCREASE, NONE }

    public boolean changedValues() {
        return direction != Direction.NONE;
    }
}
