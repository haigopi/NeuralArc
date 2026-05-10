package com.neuralarc.model;

public record LuckyStockAnalysis(
        TrendingStock stock,
        AutoAnalyzeBundle analysis,
        String errorMessage
) {
    public boolean successful() {
        return analysis != null && (errorMessage == null || errorMessage.isBlank());
    }
}
