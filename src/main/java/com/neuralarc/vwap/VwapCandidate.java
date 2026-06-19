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
        BigDecimal spreadPercent
) {}
