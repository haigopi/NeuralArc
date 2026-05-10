package com.neuralarc.model;

import java.math.BigDecimal;
import java.time.Instant;

public record AiRecommendationRequest(
        String symbol,
        BigDecimal currentPrice,
        Instant analysisTimestamp,
        String strategyName,
        CurrentAnalysis currentAnalysis,
        String instruction
) {
    public static final String DEFAULT_INSTRUCTION = "Analyze recent web articles, news, earnings commentary, "
            + "analyst notes, and market sentiment for the given stock symbol. Combine that external context "
            + "with the current technical analysis provided by the application. Return a practical recommendation: "
            + "BUY, HOLD, WAIT, SELL, or AVOID. Include confidence, key reasons, risks, and sources analyzed.";

    public AiRecommendationRequest {
        symbol = symbol == null ? "" : symbol.trim().toUpperCase();
        currentPrice = currentPrice == null ? BigDecimal.ZERO : currentPrice;
        analysisTimestamp = analysisTimestamp == null ? Instant.now() : analysisTimestamp;
        strategyName = strategyName == null ? "" : strategyName.trim();
        currentAnalysis = currentAnalysis == null ? CurrentAnalysis.empty() : currentAnalysis;
        instruction = instruction == null || instruction.isBlank() ? DEFAULT_INSTRUCTION : instruction.trim();
    }

    public record CurrentAnalysis(
            String technicalSummary,
            BigDecimal support,
            BigDecimal resistance,
            String volumeSignal,
            String riskLevel,
            String recommendationContext
    ) {
        public CurrentAnalysis {
            technicalSummary = technicalSummary == null ? "" : technicalSummary.trim();
            support = support == null ? BigDecimal.ZERO : support;
            resistance = resistance == null ? BigDecimal.ZERO : resistance;
            volumeSignal = volumeSignal == null ? "" : volumeSignal.trim();
            riskLevel = riskLevel == null ? "" : riskLevel.trim();
            recommendationContext = recommendationContext == null ? "" : recommendationContext.trim();
        }

        public static CurrentAnalysis empty() {
            return new CurrentAnalysis("", BigDecimal.ZERO, BigDecimal.ZERO, "", "", "");
        }
    }
}
