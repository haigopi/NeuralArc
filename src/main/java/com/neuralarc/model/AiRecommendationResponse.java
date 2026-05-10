package com.neuralarc.model;

import java.time.Instant;
import java.util.List;

public record AiRecommendationResponse(
        String symbol,
        AiProviderType provider,
        AiRecommendationValue recommendation,
        double confidence,
        String summary,
        List<String> keyReasons,
        List<String> risks,
        List<AiSourceAnalyzed> sourcesAnalyzed,
        Instant generatedAt
) {
    public AiRecommendationResponse {
        symbol = symbol == null ? "" : symbol.trim().toUpperCase();
        recommendation = recommendation == null ? AiRecommendationValue.WAIT : recommendation;
        confidence = Math.max(0.0d, Math.min(1.0d, confidence));
        summary = summary == null ? "" : summary.trim();
        keyReasons = keyReasons == null ? List.of() : List.copyOf(keyReasons);
        risks = risks == null ? List.of() : List.copyOf(risks);
        sourcesAnalyzed = sourcesAnalyzed == null ? List.of() : List.copyOf(sourcesAnalyzed);
        generatedAt = generatedAt == null ? Instant.now() : generatedAt;
    }
}
