package com.neuralarc.service;

import com.neuralarc.model.StrategyRecommendation;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;

public class StrategyApplyService {
    public AppliedStrategyValues applyRecommendationToCurrentStrategy(StrategyRecommendation recommendation) {
        if (recommendation == null || !recommendation.isApplicable()) {
            throw new IllegalArgumentException("Recommendation is not applicable.");
        }
        BigDecimal buyRulePrice = recommendation.recommendationType() == com.neuralarc.model.RecommendationType.LONG_TERM
                && recommendation.adjustedBaseBuyPrice().compareTo(BigDecimal.ZERO) > 0
                ? recommendation.adjustedBaseBuyPrice()
                : recommendation.baseBuyPrice();
        BigDecimal positionPrice = recommendation.currentPrice() != null
                && recommendation.currentPrice().compareTo(BigDecimal.ZERO) > 0
                ? recommendation.currentPrice()
                : buyRulePrice;
        TieredRiskProfile profile = profileForPositionPrice(positionPrice);
        return new AppliedStrategyValues(
                buyRulePrice,
                profile.lossBuy1Price(),
                profile.lossBuy2Price(),
                profile.stopLossPrice(),
                recommendation.sellPrice(),
                profile.stopLossEnabled(),
                true
        );
    }

    private TieredRiskProfile profileForPositionPrice(BigDecimal positionPrice) {
        BigDecimal entry = Monetary.round(positionPrice);
        if (entry.compareTo(BigDecimal.ZERO) <= 0) {
            return new TieredRiskProfile(true, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        if (entry.compareTo(new BigDecimal("10")) < 0) {
            return buildProfile(entry, false, "50", "80", null);
        }
        if (entry.compareTo(new BigDecimal("20")) < 0) {
            return buildProfile(entry, false, "45", "75", null);
        }
        if (entry.compareTo(new BigDecimal("50")) < 0) {
            return buildProfile(entry, true, "30", "45", "55");
        }
        if (entry.compareTo(new BigDecimal("100")) < 0) {
            return buildProfile(entry, true, "20", "32", "45");
        }
        if (entry.compareTo(new BigDecimal("200")) < 0) {
            return buildProfile(entry, true, "16", "26", "38");
        }
        if (entry.compareTo(new BigDecimal("500")) < 0) {
            return buildProfile(entry, true, "12", "20", "30");
        }
        return buildProfile(entry, true, "10", "16", "24");
    }

    private TieredRiskProfile buildProfile(
            BigDecimal entry,
            boolean stopLossEnabled,
            String lossBuy1DropPercent,
            String lossBuy2DropPercent,
            String stopLossDropPercent
    ) {
        BigDecimal buy1 = drop(entry, lossBuy1DropPercent);
        BigDecimal buy2 = drop(entry, lossBuy2DropPercent);
        BigDecimal stop = stopLossEnabled ? drop(entry, stopLossDropPercent) : BigDecimal.ZERO;
        return new TieredRiskProfile(stopLossEnabled, buy1, buy2, stop);
    }

    private BigDecimal drop(BigDecimal entry, String dropPercent) {
        BigDecimal factor = BigDecimal.ONE.subtract(new BigDecimal(dropPercent).movePointLeft(2));
        BigDecimal level = Monetary.round(entry.multiply(factor));
        return level.compareTo(new BigDecimal("0.01")) < 0 ? new BigDecimal("0.01") : level;
    }

    private record TieredRiskProfile(
            boolean stopLossEnabled,
            BigDecimal lossBuy1Price,
            BigDecimal lossBuy2Price,
            BigDecimal stopLossPrice
    ) {}

    public record AppliedStrategyValues(
            BigDecimal buyRulePrice,
            BigDecimal lossBuy1Price,
            BigDecimal lossBuy2Price,
            BigDecimal stopLossPrice,
            BigDecimal sellRulePrice,
            boolean enableStopLoss,
            boolean enableLossBuyLevels
    ) {}
}
