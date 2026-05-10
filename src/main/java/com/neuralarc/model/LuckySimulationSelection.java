package com.neuralarc.model;

public record LuckySimulationSelection(
        TrendingStock stock,
        AutoAnalyzeBundle analysis,
        RecommendationType selectedRecommendationType
) {}
