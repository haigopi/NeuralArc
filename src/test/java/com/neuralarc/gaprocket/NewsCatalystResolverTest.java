package com.neuralarc.gaprocket;

import com.neuralarc.model.AiProviderType;
import com.neuralarc.model.AiRecommendationRequest;
import com.neuralarc.model.AiRecommendationResponse;
import com.neuralarc.model.AiRecommendationValue;
import com.neuralarc.model.AiProviderHealthStatus;
import com.neuralarc.model.AiSourceAnalyzed;
import com.neuralarc.model.NewsArticle;
import com.neuralarc.service.AiRecommendationException;
import com.neuralarc.service.AiRecommendationProvider;
import com.neuralarc.service.AlpacaNewsClient;
import com.neuralarc.service.AlpacaNewsException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NewsCatalystResolverTest {

    @Test
    void mapsEarningsKeywordsToEarningsCatalyst() {
        GapRocketCandidate enriched = resolver(response("Company reported a strong earnings beat with revenue above guidance.",
                List.of(), source("Q2 earnings beat"))).enrich(candidate("NVDA"));

        assertEquals(GapRocketConfig.CatalystType.EARNINGS, enriched.catalystType());
        assertTrue(enriched.catalystSummary().toLowerCase().contains("earnings"));
    }

    @Test
    void mapsFdaKeywordsToBiotechCatalyst() {
        GapRocketCandidate enriched = resolver(response("FDA approval granted for the lead drug after phase 3 trial.",
                List.of(), source("FDA approval"))).enrich(candidate("BIO"));

        assertEquals(GapRocketConfig.CatalystType.FDA_BIOTECH, enriched.catalystType());
    }

    @Test
    void mapsAnalystKeywordsToUpgradeCatalyst() {
        GapRocketCandidate enriched = resolver(response("Analyst upgrade with a raised price target.",
                List.of("Upgraded to overweight"), source("Analyst note"))).enrich(candidate("AAPL"));

        assertEquals(GapRocketConfig.CatalystType.ANALYST_UPGRADE, enriched.catalystType());
    }

    @Test
    void mapsContractKeywordsToPartnershipCatalyst() {
        GapRocketCandidate enriched = resolver(response("Announced a major government contract and partnership.",
                List.of(), source("New contract"))).enrich(candidate("PLTR"));

        assertEquals(GapRocketConfig.CatalystType.CONTRACT_PARTNERSHIP, enriched.catalystType());
    }

    @Test
    void usesGeneralBreakingNewsWhenSourcesExistButNoKeywordMatch() {
        GapRocketCandidate enriched = resolver(response("Shares are moving on heavy interest today.",
                List.of(), source("Market wrap"))).enrich(candidate("AMD"));

        assertEquals(GapRocketConfig.CatalystType.GENERAL_BREAKING_NEWS, enriched.catalystType());
    }

    @Test
    void leavesCatalystNullWhenNoKeywordsAndNoSources() {
        GapRocketCandidate original = candidate("XYZ");
        GapRocketCandidate enriched = resolver(response("Nothing notable found.", List.of()))
                .enrich(original);

        assertNull(enriched.catalystType());
        assertSame(original, enriched);
    }

    @Test
    void returnsCandidateUnchangedWhenProviderMissing() {
        GapRocketCandidate original = candidate("XYZ");
        GapRocketCandidate enriched = new NewsCatalystResolver(null, Clock.systemUTC(), null).enrich(original);

        assertSame(original, enriched);
    }

    @Test
    void returnsCandidateUnchangedWhenProviderFails() {
        AiRecommendationProvider failing = new AiRecommendationProvider() {
            @Override
            public AiRecommendationResponse analyzeStock(AiRecommendationRequest request) throws AiRecommendationException {
                throw new AiRecommendationException("offline");
            }

            @Override
            public AiProviderHealthStatus healthCheck() {
                return null;
            }

            @Override
            public AiProviderType getProviderType() {
                return AiProviderType.OPENAI;
            }
        };
        GapRocketCandidate original = candidate("XYZ");

        assertSame(original, new NewsCatalystResolver(failing, Clock.systemUTC(), null).enrich(original));
    }

    @Test
    void classifyReturnsEmptyForNullResponse() {
        assertTrue(NewsCatalystResolver.classify(null).isEmpty());
    }

    @Test
    void doesNotMutateCatalystFieldsOfTheOriginalCandidate() {
        GapRocketCandidate original = candidate("NVDA");
        resolver(response("Earnings beat.", List.of(), source("x"))).enrich(original);

        assertNull(original.catalystType());
        assertFalse("Earnings beat.".equals(original.catalystSummary()));
    }

    @Test
    void skipsAiAnalysisWhenNoRecentNews() {
        AtomicInteger aiCalls = new AtomicInteger();
        NewsCatalystResolver resolver = new NewsCatalystResolver(
                countingProvider(aiCalls, response("Earnings beat.", List.of(), source("x"))),
                newsClient(List.of()), Clock.systemUTC(), null);
        GapRocketCandidate original = candidate("NVDA");

        GapRocketCandidate enriched = resolver.enrich(original);

        assertSame(original, enriched);
        assertNull(enriched.catalystType());
        assertEquals(0, aiCalls.get(), "AI must not be called when there is no recent news");
    }

    @Test
    void skipsAiAnalysisWhenNewsIsStale() {
        AtomicInteger aiCalls = new AtomicInteger();
        NewsArticle stale = new NewsArticle("Old news", "", "benzinga", "https://x",
                Instant.now().minus(Duration.ofDays(10)), List.of("NVDA"));
        NewsCatalystResolver resolver = new NewsCatalystResolver(
                countingProvider(aiCalls, response("Earnings beat.", List.of(), source("x"))),
                newsClient(List.of(stale)), Clock.systemUTC(), null);

        resolver.enrich(candidate("NVDA"));

        assertEquals(0, aiCalls.get(), "Stale news outside the recency window must not trigger AI");
    }

    @Test
    void runsAiAnalysisWhenRecentNewsExists() {
        AtomicInteger aiCalls = new AtomicInteger();
        NewsArticle fresh = new NewsArticle("Fresh earnings", "", "benzinga", "https://x",
                Instant.now().minus(Duration.ofHours(2)), List.of("NVDA"));
        NewsCatalystResolver resolver = new NewsCatalystResolver(
                countingProvider(aiCalls, response("Strong earnings beat.", List.of(), source("Q2"))),
                newsClient(List.of(fresh)), Clock.systemUTC(), null);

        GapRocketCandidate enriched = resolver.enrich(candidate("NVDA"));

        assertEquals(1, aiCalls.get());
        assertEquals(GapRocketConfig.CatalystType.EARNINGS, enriched.catalystType());
    }

    @Test
    void failsOpenToAiWhenNewsLookupThrows() {
        AtomicInteger aiCalls = new AtomicInteger();
        AlpacaNewsClient failing = (symbol, limit) -> {
            throw new AlpacaNewsException("offline");
        };
        NewsCatalystResolver resolver = new NewsCatalystResolver(
                countingProvider(aiCalls, response("Earnings beat.", List.of(), source("x"))),
                failing, Clock.systemUTC(), null);

        resolver.enrich(candidate("NVDA"));

        assertEquals(1, aiCalls.get(), "A failed news pre-filter must fall open to AI analysis");
    }

    private static AlpacaNewsClient newsClient(List<NewsArticle> articles) {
        return (symbol, limit) -> articles;
    }

    private static AiRecommendationProvider countingProvider(AtomicInteger calls, AiRecommendationResponse response) {
        return new AiRecommendationProvider() {
            @Override
            public AiRecommendationResponse analyzeStock(AiRecommendationRequest request) {
                calls.incrementAndGet();
                return response;
            }

            @Override
            public AiProviderHealthStatus healthCheck() {
                return null;
            }

            @Override
            public AiProviderType getProviderType() {
                return AiProviderType.OPENAI;
            }
        };
    }

    private static NewsCatalystResolver resolver(AiRecommendationResponse response) {
        AiRecommendationProvider provider = new AiRecommendationProvider() {
            @Override
            public AiRecommendationResponse analyzeStock(AiRecommendationRequest request) {
                return response;
            }

            @Override
            public AiProviderHealthStatus healthCheck() {
                return null;
            }

            @Override
            public AiProviderType getProviderType() {
                return AiProviderType.OPENAI;
            }
        };
        return new NewsCatalystResolver(provider, Clock.systemUTC(), null);
    }

    private static AiRecommendationResponse response(String summary, List<String> reasons, AiSourceAnalyzed... sources) {
        return new AiRecommendationResponse("SYM", AiProviderType.OPENAI, AiRecommendationValue.BUY, 0.8d,
                summary, reasons, List.of(), List.of(sources), Instant.now());
    }

    private static AiSourceAnalyzed source(String title) {
        return new AiSourceAnalyzed(title, "https://example.com", Instant.now());
    }

    private static GapRocketCandidate candidate(String symbol) {
        return new GapRocketCandidate(symbol, symbol + " Inc", new BigDecimal("8"), 3_000_000L, new BigDecimal("4"),
                new BigDecimal("50"), new BigDecimal("46"), new BigDecimal("51"), new BigDecimal("48"),
                null, null, true, false, new BigDecimal("0.5"), true, new BigDecimal("49"));
    }
}
