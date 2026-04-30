package com.neuralarc.model;

public record AutoAnalyzeBundle(
        AutoAnalyzeResult result,
        StrategyRecommendation shortTermRecommendation,
        StrategyRecommendation longTermRecommendation
) {}
