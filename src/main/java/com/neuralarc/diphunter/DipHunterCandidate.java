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
        boolean aboveMa20, boolean aboveMa50, boolean intradayReversal, BigDecimal spreadPercent, BigDecimal vwap
) {}
