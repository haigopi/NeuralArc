package com.neuralarc.model;

import java.math.BigDecimal;

/**
 * Represents a single OHLCV bar returned by the Alpaca Market Data API.
 * Maps to the bar object fields in the /v2/stocks/{symbol}/bars response:
 *   t = timestamp, o = open, h = high, l = low, c = close, v = volume.
 */
public record MarketBar(
        String symbol,
        String timestamp,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume
) {
    public MarketBar {
        if (open == null) open = BigDecimal.ZERO;
        if (high == null) high = BigDecimal.ZERO;
        if (low == null) low = BigDecimal.ZERO;
        if (close == null) close = BigDecimal.ZERO;
        if (volume == null) volume = BigDecimal.ZERO;
    }
}

