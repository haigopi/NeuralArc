package com.neuralarc.model;

import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record StrategyRecommendation(
        String symbol,
        RecommendationType recommendationType,
        BigDecimal baseBuyPrice,
        BigDecimal originalCalculatedBasePrice,
        BigDecimal effectiveMarketPrice,
        BigDecimal lastClosePrice,
        BigDecimal currentPrice,
        BigDecimal twoWeekLow,
        BigDecimal twoWeekHigh,
        BigDecimal expectedDipPct,
        BigDecimal behaviorAdjustedBasePrice,
        BigDecimal adjustedBaseBuyPrice,
        MarketMode marketMode,
        ShortTermMarketMode shortTermMarketMode,
        String baseAdjustmentReason,
        BigDecimal avgGapPct,
        BigDecimal negativeGapPctAverage,
        BigDecimal gapVolatility,
        BigDecimal averageIntradayDipPct,
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
        originalCalculatedBasePrice = Monetary.round(originalCalculatedBasePrice);
        effectiveMarketPrice = Monetary.round(effectiveMarketPrice);
        lastClosePrice = Monetary.round(lastClosePrice);
        currentPrice = Monetary.round(currentPrice);
        twoWeekLow = Monetary.round(twoWeekLow);
        twoWeekHigh = Monetary.round(twoWeekHigh);
        expectedDipPct = normalizePercent(expectedDipPct);
        behaviorAdjustedBasePrice = Monetary.round(behaviorAdjustedBasePrice);
        adjustedBaseBuyPrice = Monetary.round(adjustedBaseBuyPrice);
        marketMode = marketMode == null ? MarketMode.ACCUMULATION : marketMode;
        shortTermMarketMode = shortTermMarketMode == null ? ShortTermMarketMode.RANGE_ENTRY : shortTermMarketMode;
        baseAdjustmentReason = baseAdjustmentReason == null ? "" : baseAdjustmentReason;
        avgGapPct = normalizePercent(avgGapPct);
        negativeGapPctAverage = normalizePercent(negativeGapPctAverage);
        gapVolatility = normalizePercent(gapVolatility);
        averageIntradayDipPct = normalizePercent(averageIntradayDipPct);
        buy1Price = Monetary.round(buy1Price);
        buy2Price = Monetary.round(buy2Price);
        stopLossPrice = Monetary.round(stopLossPrice);
        sellPrice = Monetary.round(sellPrice);
        target1Price = Monetary.round(target1Price);
        target2Price = Monetary.round(target2Price);
        trendStatus = trendStatus == null ? "-" : trendStatus;
        volumeStatus = volumeStatus == null ? "-" : volumeStatus;
        riskRewardRatio = Monetary.round(riskRewardRatio);
        warningMessage = warningMessage == null ? "" : warningMessage;
    }

    public boolean isApplicable() {
        return baseBuyPrice.compareTo(BigDecimal.ZERO) > 0
                && adjustedBaseBuyPrice.compareTo(BigDecimal.ZERO) > 0
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
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                MarketMode.WEAK_AVOID,
                ShortTermMarketMode.WEAK_AVOID,
                "",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
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

    private static BigDecimal normalizePercent(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return value.setScale(4, RoundingMode.HALF_UP);
    }
}
