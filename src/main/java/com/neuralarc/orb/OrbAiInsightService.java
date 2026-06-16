package com.neuralarc.orb;

import com.neuralarc.model.AiRecommendationRequest;
import com.neuralarc.model.AiRecommendationResponse;
import com.neuralarc.service.AiRecommendationException;
import com.neuralarc.service.AiRecommendationProvider;

import java.time.Clock;
import java.time.Instant;
import java.util.function.Consumer;

public final class OrbAiInsightService {
    private static final String INSTRUCTION = "Analyze recent web articles, news, earnings commentary, analyst notes, "
            + "and market sentiment for the given stock symbol. Focus on whether fresh news supports or undermines "
            + "a same-day long Opening Range Breakout setup. Return concise reasons, risks, and sources.";

    private final AiRecommendationProvider provider;
    private final Clock clock;
    private final Consumer<String> log;

    public OrbAiInsightService(AiRecommendationProvider provider, Clock clock, Consumer<String> log) {
        this.provider = provider;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.log = log == null ? ignored -> { } : log;
    }

    public OrbCandidate enrich(OrbCandidate candidate) {
        if (candidate == null || provider == null) {
            return candidate;
        }
        try {
            AiRecommendationResponse response = provider.analyzeStock(buildRequest(candidate));
            String summary = response.summary().isBlank() && !response.keyReasons().isEmpty()
                    ? String.join("; ", response.keyReasons())
                    : response.summary();
            if (summary.isBlank()) {
                log.accept("[ORB] AI analysis returned no fresh context for " + candidate.symbol() + ".");
                return candidate;
            }
            log.accept("[ORB] AI catalyst context loaded for " + candidate.symbol() + ".");
            return candidate.withAiInsight(summary, response.confidence());
        } catch (AiRecommendationException ex) {
            log.accept("[ORB] AI analysis unavailable for " + candidate.symbol() + ": " + ex.getMessage());
            return candidate;
        }
    }

    private AiRecommendationRequest buildRequest(OrbCandidate candidate) {
        AiRecommendationRequest.CurrentAnalysis analysis = new AiRecommendationRequest.CurrentAnalysis(
                "Opening Range Breakout candidate from " + candidate.discoverySource() + ".",
                java.math.BigDecimal.ZERO,
                candidate.latestPrice(),
                candidate.relativeVolume() == null ? "relative volume unavailable" : "relative volume " + candidate.relativeVolume().toPlainString() + "x",
                "intraday breakout",
                "Confirm whether current live news supports a long ORB setup today.");
        return new AiRecommendationRequest(candidate.symbol(), candidate.latestPrice(), Instant.now(clock),
                "ORB Engine", analysis, INSTRUCTION);
    }
}
