package com.neuralarc.service;

import com.neuralarc.model.StrategyRecommendation;

import java.math.BigDecimal;

public class StrategyApplyService {
    public AppliedStrategyValues applyRecommendationToCurrentStrategy(StrategyRecommendation recommendation) {
        if (recommendation == null || !recommendation.isApplicable()) {
            throw new IllegalArgumentException("Recommendation is not applicable.");
        }
        return new AppliedStrategyValues(
                recommendation.baseBuyPrice(),
                recommendation.buy1Price(),
                recommendation.buy2Price(),
                recommendation.stopLossPrice(),
                recommendation.sellPrice(),
                true
        );
    }

    public record AppliedStrategyValues(
            BigDecimal buyRulePrice,
            BigDecimal lossBuy1Price,
            BigDecimal lossBuy2Price,
            BigDecimal stopLossPrice,
            BigDecimal sellRulePrice,
            boolean enableLossBuyLevels
    ) {}
}
