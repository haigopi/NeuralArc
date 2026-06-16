package com.neuralarc.orb;

import java.math.BigDecimal;

public record OrbCandidate(
        String symbol,
        BigDecimal latestPrice,
        BigDecimal regularSessionOpen,
        BigDecimal relativeVolume,
        BigDecimal averageVolume,
        BigDecimal spreadPercent,
        String discoverySource,
        String aiSummary,
        double aiConfidence
) {
    public OrbCandidate(
            String symbol,
            BigDecimal latestPrice,
            BigDecimal regularSessionOpen,
            BigDecimal relativeVolume,
            BigDecimal averageVolume,
            BigDecimal spreadPercent,
            String discoverySource
    ) {
        this(symbol, latestPrice, regularSessionOpen, relativeVolume, averageVolume, spreadPercent, discoverySource, "", 0.0d);
    }

    public OrbCandidate {
        symbol = symbol == null ? "" : symbol.trim().toUpperCase();
        discoverySource = discoverySource == null || discoverySource.isBlank() ? "manual" : discoverySource;
        aiSummary = aiSummary == null ? "" : aiSummary.trim();
        aiConfidence = Math.max(0.0d, Math.min(1.0d, aiConfidence));
    }

    public OrbCandidate withAiInsight(String summary, double confidence) {
        return new OrbCandidate(symbol, latestPrice, regularSessionOpen, relativeVolume, averageVolume,
                spreadPercent, discoverySource, summary, confidence);
    }
}
