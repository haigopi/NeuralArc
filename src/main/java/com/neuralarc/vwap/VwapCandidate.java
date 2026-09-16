package com.neuralarc.vwap;

import java.math.BigDecimal;

/**
 * A single VWAP Desk candidate built from live market data — a stock trading below its intraday VWAP
 * while remaining in a broader uptrend, a setup for mean-reversion back toward VWAP. No hardcoded
 * tickers or canned prices ever populate this.
 */
public record VwapCandidate(
        String symbol, String companyName, BigDecimal currentPrice, BigDecimal vwap, BigDecimal discountPercent,
        BigDecimal previousClose, BigDecimal dayChangePercent, long averageVolume, BigDecimal relativeVolume,
        BigDecimal movingAverage50, BigDecimal movingAverage200, boolean aboveMa50, boolean aboveMa200,
        /** Today's high-to-low range as a percentage of price - a volatility measure, not a bid/ask spread. */
        BigDecimal intradayRangePercent,
        /** Lowest price actually traded in the last week — the floor for a patient entry. */
        BigDecimal weekLow
) {
    /** A candidate whose week's low is not known; the entry then aims only under the market. */
    public VwapCandidate(
            String symbol, String companyName, BigDecimal currentPrice, BigDecimal vwap, BigDecimal discountPercent,
            BigDecimal previousClose, BigDecimal dayChangePercent, long averageVolume, BigDecimal relativeVolume,
            BigDecimal movingAverage50, BigDecimal movingAverage200, boolean aboveMa50, boolean aboveMa200,
            BigDecimal intradayRangePercent
    ) {
        this(symbol, companyName, currentPrice, vwap, discountPercent, previousClose, dayChangePercent, averageVolume,
                relativeVolume, movingAverage50, movingAverage200, aboveMa50, aboveMa200, intradayRangePercent,
                BigDecimal.ZERO);
    }
}
