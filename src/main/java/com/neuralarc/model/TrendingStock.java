package com.neuralarc.model;

import java.math.BigDecimal;

public record TrendingStock(
        String symbol,
        String companyName,
        BigDecimal latestPrice,
        BigDecimal dailyChangePercent,
        BigDecimal volume,
        BigDecimal tradeCount,
        String reason,
        BigDecimal trendingScore
) {
    public TrendingStock {
        symbol = symbol == null ? "" : symbol.trim().toUpperCase();
        companyName = companyName == null ? "" : companyName.trim();
        latestPrice = latestPrice == null ? BigDecimal.ZERO : latestPrice;
        dailyChangePercent = dailyChangePercent == null ? BigDecimal.ZERO : dailyChangePercent;
        volume = volume == null ? BigDecimal.ZERO : volume;
        tradeCount = tradeCount == null ? BigDecimal.ZERO : tradeCount;
        reason = reason == null ? "" : reason.trim();
        trendingScore = trendingScore == null ? BigDecimal.ZERO : trendingScore;
    }
}
