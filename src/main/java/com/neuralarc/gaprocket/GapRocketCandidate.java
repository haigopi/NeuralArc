package com.neuralarc.gaprocket;

import java.math.BigDecimal;

public record GapRocketCandidate(
        String symbol, String companyName, BigDecimal gapPercent, long premarketVolume,
        BigDecimal relativeVolume, BigDecimal currentPrice, BigDecimal previousClose,
        BigDecimal premarketHigh, BigDecimal premarketLow, GapRocketConfig.CatalystType catalystType,
        String catalystSummary, boolean spyGreen, boolean qqqGreen, BigDecimal spreadPercent,
        boolean volumeStrong, BigDecimal vwap
) {}
