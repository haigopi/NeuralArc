package com.neuralarc.model;

import com.neuralarc.util.Monetary;

import java.math.BigDecimal;

public record StrategyRecommendation(
        String symbol,
        RecommendationType recommendationType,
        BigDecimal baseBuyPrice,
        BigDecimal buy1Price,
        BigDecimal buy2Price,
        BigDecimal stopLossPrice,
        BigDecimal sellPrice,
        BigDecimal target1Price,
        BigDecimal target2Price,
        String trendStatus,
        String volumeStatus,
        BigDecimal riskRewardRatio,
        int confidenceScore,
        RecommendationAction recommendationAction,
        String warningMessage,
        boolean breakdownMode
) {
    public StrategyRecommendation {
        baseBuyPrice = Monetary.round(baseBuyPrice);
        buy1Price = Monetary.round(buy1Price);
        buy2Price = Monetary.round(buy2Price);
        stopLossPrice = Monetary.round(stopLossPrice);
        sellPrice = Monetary.round(sellPrice);
        target1Price = Monetary.round(target1Price);
        target2Price = Monetary.round(target2Price);
        riskRewardRatio = Monetary.round(riskRewardRatio);
        trendStatus = trendStatus == null ? "-" : trendStatus;
        volumeStatus = volumeStatus == null ? "-" : volumeStatus;
        warningMessage = warningMessage == null ? "" : warningMessage;
    }

    public boolean isApplicable() {
        return baseBuyPrice.compareTo(BigDecimal.ZERO) > 0
                && buy1Price.compareTo(BigDecimal.ZERO) > 0
                && buy2Price.compareTo(BigDecimal.ZERO) > 0
                && stopLossPrice.compareTo(BigDecimal.ZERO) > 0
                && sellPrice.compareTo(BigDecimal.ZERO) > 0;
    }

    public static StrategyRecommendation unavailable(String symbol, RecommendationType type, String warningMessage) {
        return new StrategyRecommendation(
                symbol == null ? "" : symbol,
                type,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "Insufficient data",
                "Insufficient data",
                BigDecimal.ZERO,
                0,
                RecommendationAction.AVOID,
                warningMessage,
                false
        );
    }
}
