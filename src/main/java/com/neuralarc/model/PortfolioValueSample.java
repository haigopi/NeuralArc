package com.neuralarc.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The portfolio's value at one minute of the trading day: the market value of held positions and the
 * capital invested in them, so the P&L at that minute is their difference.
 */
public record PortfolioValueSample(Instant minute, BigDecimal marketValue, BigDecimal investedValue) {
    public BigDecimal unrealizedPnl() {
        return marketValue.subtract(investedValue);
    }
}
