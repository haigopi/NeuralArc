package com.neuralarc.swing;

import java.math.BigDecimal;

/**
 * A single Swing Vault candidate built from live daily-bar market data — a strong, up-trending stock
 * that has pulled back from a recent swing high toward a rising moving-average support zone, a setup
 * for a multi-day swing back toward that high. No hardcoded tickers or canned prices ever populate this.
 */
public record SwingCandidate(
        String symbol, String companyName, BigDecimal currentPrice, BigDecimal recentHigh, BigDecimal pullbackPercent,
        BigDecimal previousClose, BigDecimal dayChangePercent, long averageVolume, BigDecimal relativeVolume,
        BigDecimal movingAverage20, BigDecimal movingAverage50, BigDecimal movingAverage200,
        boolean aboveMa20, boolean aboveMa50, boolean aboveMa200, boolean ma50AboveMa200,
        BigDecimal supportProximityPercent, BigDecimal atr,
        /** Lowest price actually traded in the last week — the floor for a patient entry. */
        BigDecimal weekLow
) {
    /** A candidate whose week's low is not known; the entry then aims only under the market. */
    public SwingCandidate(
            String symbol, String companyName, BigDecimal currentPrice, BigDecimal recentHigh, BigDecimal pullbackPercent,
            BigDecimal previousClose, BigDecimal dayChangePercent, long averageVolume, BigDecimal relativeVolume,
            BigDecimal movingAverage20, BigDecimal movingAverage50, BigDecimal movingAverage200,
            boolean aboveMa20, boolean aboveMa50, boolean aboveMa200, boolean ma50AboveMa200,
            BigDecimal supportProximityPercent, BigDecimal atr
    ) {
        this(symbol, companyName, currentPrice, recentHigh, pullbackPercent, previousClose, dayChangePercent,
                averageVolume, relativeVolume, movingAverage20, movingAverage50, movingAverage200,
                aboveMa20, aboveMa50, aboveMa200, ma50AboveMa200, supportProximityPercent, atr, BigDecimal.ZERO);
    }
}
