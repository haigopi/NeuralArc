package com.neuralarc.diphunter;

import java.math.BigDecimal;

/**
 * A single Dip Hunter candidate built from live market data — a stock that has pulled back from a
 * recent high while remaining in an uptrend. No hardcoded tickers or canned prices ever populate this.
 */
public record DipHunterCandidate(
        String symbol, String companyName, BigDecimal pullbackPercent, BigDecimal dayChangePercent,
        long averageVolume, BigDecimal relativeVolume, BigDecimal currentPrice, BigDecimal previousClose,
        BigDecimal recentHigh, BigDecimal movingAverage20, BigDecimal movingAverage50,
        boolean aboveMa20, boolean aboveMa50, boolean intradayReversal,
        /** Today's high-to-low range as a percentage of price - a volatility measure, not a bid/ask spread. */
        BigDecimal intradayRangePercent, BigDecimal vwap,
        /** Lowest price actually traded in the last week — the floor for a patient entry. */
        BigDecimal weekLow
) {
    /** A candidate whose week's low is not known; the entry then aims only under the market. */
    public DipHunterCandidate(
            String symbol, String companyName, BigDecimal pullbackPercent, BigDecimal dayChangePercent,
            long averageVolume, BigDecimal relativeVolume, BigDecimal currentPrice, BigDecimal previousClose,
            BigDecimal recentHigh, BigDecimal movingAverage20, BigDecimal movingAverage50,
            boolean aboveMa20, boolean aboveMa50, boolean intradayReversal,
            BigDecimal intradayRangePercent, BigDecimal vwap
    ) {
        this(symbol, companyName, pullbackPercent, dayChangePercent, averageVolume, relativeVolume, currentPrice,
                previousClose, recentHigh, movingAverage20, movingAverage50, aboveMa20, aboveMa50, intradayReversal,
                intradayRangePercent, vwap, BigDecimal.ZERO);
    }
}
