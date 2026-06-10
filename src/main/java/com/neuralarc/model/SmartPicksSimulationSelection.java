package com.neuralarc.model;

public record SmartPicksSimulationSelection(
        TrendingStock stock,
        AutoAnalyzeBundle analysis,
        RecommendationType selectedRecommendationType,
        int buyQuantity
) {}
