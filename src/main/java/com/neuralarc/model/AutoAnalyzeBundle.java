package com.neuralarc.model;

public record AutoAnalyzeBundle(
        AutoAnalyzeResult result,
        StrategyRecommendation shortTermRecommendation,
        StrategyRecommendation highRiskShortTermRecommendation,
        StrategyRecommendation longTermRecommendation
) {}
