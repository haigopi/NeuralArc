package com.neuralarc.gaprocket;

import com.neuralarc.model.AiRecommendationRequest;
import com.neuralarc.model.AiRecommendationResponse;
import com.neuralarc.model.AiSourceAnalyzed;
import com.neuralarc.service.AiRecommendationException;
import com.neuralarc.service.AiRecommendationProvider;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Resolves a live news catalyst for a gap-and-go candidate using the configured AI provider's web
 * search (e.g. {@code OpenAiRecommendationProvider}, which already enables OpenAI's web_search tool
 * and returns the sources it analyzed). This is the "analyze the news automatically" capability —
 * no Google scraping and no separate news API.
 *
 * <p>Never fabricates a catalyst: when no AI provider is configured, the call fails, or no material
 * recent news is found, the candidate is returned unchanged with a {@code null} catalyst so the
 * analyzer can withhold the catalyst bonus (and reject it when a catalyst is required).
 */
public final class NewsCatalystResolver {
    private static final String INSTRUCTION = "Analyze recent (last 48 hours) web articles, news, "
            + "earnings commentary, analyst notes, and market sentiment for the given stock symbol. "
            + "Decide whether there is a fresh news catalyst that could drive a gap-and-go move today. "
            + "Classify the dominant catalyst and summarize it briefly with sources.";

    private final AiRecommendationProvider provider;
    private final Clock clock;
    private final Consumer<String> log;

    public NewsCatalystResolver(AiRecommendationProvider provider, Clock clock, Consumer<String> log) {
        this.provider = provider;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.log = log == null ? ignored -> { } : log;
    }

    /**
     * Return a copy of the candidate with its catalyst populated from live AI news analysis, or the
     * candidate unchanged when no provider is configured, the call fails, or no catalyst is found.
     */
    public GapRocketCandidate enrich(GapRocketCandidate candidate) {
        if (candidate == null) {
            return null;
        }
        if (provider == null) {
            return candidate;
        }
        try {
            AiRecommendationResponse response = provider.analyzeStock(buildRequest(candidate));
            Optional<GapRocketConfig.CatalystType> type = classify(response);
            if (type.isEmpty()) {
                log.accept("[Gap Rocket] No live news catalyst found for " + candidate.symbol() + ".");
                return candidate;
            }
            return withCatalyst(candidate, type.get(), buildSummary(response));
        } catch (AiRecommendationException ex) {
            log.accept("[Gap Rocket] News analysis unavailable for " + candidate.symbol() + ": " + ex.getMessage());
            return candidate;
        }
    }

    private AiRecommendationRequest buildRequest(GapRocketCandidate candidate) {
        AiRecommendationRequest.CurrentAnalysis analysis = new AiRecommendationRequest.CurrentAnalysis(
                "Gap-and-Go premarket scan: gap " + plain(candidate.gapPercent()) + "%, relative volume "
                        + plain(candidate.relativeVolume()) + "x.",
                candidate.premarketLow(),
                candidate.premarketHigh(),
                "relative volume " + plain(candidate.relativeVolume()) + "x",
                "intraday momentum",
                "Confirm whether a fresh news catalyst supports a gap-and-go long today.");
        return new AiRecommendationRequest(
                candidate.symbol(),
                candidate.currentPrice(),
                Instant.now(clock),
                "Gap-and-Go",
                analysis,
                INSTRUCTION);
    }

    /**
     * Infer the dominant catalyst type from the AI summary and key reasons. Returns empty when there
     * is no evidence of a material recent catalyst (no matching keywords and no sources analyzed).
     */
    static Optional<GapRocketConfig.CatalystType> classify(AiRecommendationResponse response) {
        if (response == null) {
            return Optional.empty();
        }
        StringBuilder builder = new StringBuilder();
        builder.append(' ').append(response.summary());
        for (String reason : response.keyReasons()) {
            builder.append(' ').append(reason);
        }
        String text = builder.toString().toLowerCase(Locale.ROOT);

        if (containsAny(text, "earnings", "eps", "revenue", "guidance", "quarterly", "beat", "results")) {
            return Optional.of(GapRocketConfig.CatalystType.EARNINGS);
        }
        if (containsAny(text, "fda", "approval", "clinical", "trial", "phase", "drug", "biotech")) {
            return Optional.of(GapRocketConfig.CatalystType.FDA_BIOTECH);
        }
        if (containsAny(text, "upgrade", "price target", "analyst", "rating", "initiated", "overweight")) {
            return Optional.of(GapRocketConfig.CatalystType.ANALYST_UPGRADE);
        }
        if (containsAny(text, "contract", "partnership", "deal", "agreement", "acquisition", "merger", "award")) {
            return Optional.of(GapRocketConfig.CatalystType.CONTRACT_PARTNERSHIP);
        }
        // Material recent news exists (the AI cited sources) but doesn't match a specific bucket.
        if (!response.sourcesAnalyzed().isEmpty()) {
            return Optional.of(GapRocketConfig.CatalystType.GENERAL_BREAKING_NEWS);
        }
        return Optional.empty();
    }

    private static String buildSummary(AiRecommendationResponse response) {
        String summary = response.summary() == null ? "" : response.summary().trim();
        if (summary.isBlank() && !response.keyReasons().isEmpty()) {
            summary = String.join("; ", response.keyReasons());
        }
        List<AiSourceAnalyzed> sources = response.sourcesAnalyzed();
        if (!sources.isEmpty()) {
            String title = sources.getFirst().title();
            if (title != null && !title.isBlank()) {
                summary = summary.isBlank() ? title : summary + " (source: " + title + ")";
            }
        }
        return summary.isBlank() ? "AI-confirmed news catalyst." : summary;
    }

    private static GapRocketCandidate withCatalyst(GapRocketCandidate c,
                                                   GapRocketConfig.CatalystType type,
                                                   String summary) {
        return new GapRocketCandidate(
                c.symbol(), c.companyName(), c.gapPercent(), c.premarketVolume(), c.relativeVolume(),
                c.currentPrice(), c.previousClose(), c.premarketHigh(), c.premarketLow(), type,
                summary, c.spyGreen(), c.qqqGreen(), c.spreadPercent(), c.volumeStrong(), c.vwap());
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String plain(BigDecimal value) {
        return value == null ? "0" : value.toPlainString();
    }
}
