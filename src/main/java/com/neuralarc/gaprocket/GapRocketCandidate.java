package com.neuralarc.gaprocket;

import java.math.BigDecimal;

public record GapRocketCandidate(
        String symbol, String companyName, BigDecimal gapPercent, long premarketVolume,
        BigDecimal relativeVolume, BigDecimal currentPrice, BigDecimal previousClose,
        BigDecimal premarketHigh, BigDecimal premarketLow, GapRocketConfig.CatalystType catalystType,
        String catalystSummary, boolean spyGreen, boolean qqqGreen, BigDecimal spreadPercent,
        boolean volumeStrong, BigDecimal vwap, BigDecimal todayOpen
) {
    /** A candidate whose today's open is not known; the entry is then measured from the low and current price. */
    public GapRocketCandidate(
            String symbol, String companyName, BigDecimal gapPercent, long premarketVolume,
            BigDecimal relativeVolume, BigDecimal currentPrice, BigDecimal previousClose,
            BigDecimal premarketHigh, BigDecimal premarketLow, GapRocketConfig.CatalystType catalystType,
            String catalystSummary, boolean spyGreen, boolean qqqGreen, BigDecimal spreadPercent,
            boolean volumeStrong, BigDecimal vwap
    ) {
        this(symbol, companyName, gapPercent, premarketVolume, relativeVolume, currentPrice, previousClose,
                premarketHigh, premarketLow, catalystType, catalystSummary, spyGreen, qqqGreen, spreadPercent,
                volumeStrong, vwap, BigDecimal.ZERO);
    }
}
