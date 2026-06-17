package com.neuralarc.orb;

import com.neuralarc.model.AiProviderHealthStatus;
import com.neuralarc.model.AiProviderType;
import com.neuralarc.model.AiRecommendationRequest;
import com.neuralarc.model.AiRecommendationResponse;
import com.neuralarc.model.AiRecommendationValue;
import com.neuralarc.service.AiRecommendationException;
import com.neuralarc.service.AiRecommendationProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrbAiInsightServiceTest {
    private static final OrbCandidate AAPL = new OrbCandidate(
            "AAPL", new BigDecimal("185.50"), null, new BigDecimal("2.3"), null, null, "top mover gainer");
    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-06-15T14:00:00Z"), ZoneOffset.UTC);

    @Test
    void enrichReturnsCandidateWithSummaryWhenProviderSucceeds() {
        OrbAiInsightService service = new OrbAiInsightService(new StubProvider("Earnings beat expected", 0.85), FIXED, null);
        OrbCandidate enriched = service.enrich(AAPL);
        assertEquals("Earnings beat expected", enriched.aiSummary());
        assertEquals(0.85, enriched.aiConfidence(), 0.001);
    }

    @Test
    void enrichFallsBackToKeyReasonsWhenSummaryBlank() {
        OrbAiInsightService service = new OrbAiInsightService(
                new StubProvider("", 0.70, List.of("Strong volume", "Analyst upgrade")), FIXED, null);
        OrbCandidate enriched = service.enrich(AAPL);
        assertEquals("Strong volume; Analyst upgrade", enriched.aiSummary());
    }

    @Test
    void enrichReturnsOriginalWhenProviderThrows() {
        List<String> log = new ArrayList<>();
        OrbAiInsightService service = new OrbAiInsightService(new ThrowingProvider(), FIXED, log::add);
        OrbCandidate enriched = service.enrich(AAPL);
        assertSame(AAPL, enriched);
        assertTrue(log.stream().anyMatch(line -> line.contains("AI analysis unavailable")));
    }

    @Test
    void enrichReturnsOriginalWhenSummaryAndKeyReasonsEmpty() {
        List<String> log = new ArrayList<>();
        OrbAiInsightService service = new OrbAiInsightService(new StubProvider("", 0.0), FIXED, log::add);
        OrbCandidate enriched = service.enrich(AAPL);
        assertSame(AAPL, enriched);
        assertTrue(log.stream().anyMatch(line -> line.contains("no fresh context")));
    }

    @Test
    void enrichReturnsNullWhenCandidateIsNull() {
        OrbAiInsightService service = new OrbAiInsightService(new StubProvider("Some summary", 0.9), FIXED, null);
        assertNull(service.enrich(null));
    }

    @Test
    void enrichReturnsOriginalWhenProviderIsNull() {
        OrbAiInsightService service = new OrbAiInsightService(null, FIXED, null);
        assertSame(AAPL, service.enrich(AAPL));
    }

    private static AiRecommendationResponse response(String summary, double confidence, List<String> keyReasons) {
        return new AiRecommendationResponse("AAPL", AiProviderType.OPENAI, AiRecommendationValue.BUY,
                confidence, summary, keyReasons, List.of(), List.of(), Instant.now());
    }

    private static final class StubProvider implements AiRecommendationProvider {
        private final String summary;
        private final double confidence;
        private final List<String> keyReasons;

        StubProvider(String summary, double confidence) { this(summary, confidence, List.of()); }
        StubProvider(String summary, double confidence, List<String> keyReasons) {
            this.summary = summary; this.confidence = confidence; this.keyReasons = keyReasons;
        }

        @Override public AiRecommendationResponse analyzeStock(AiRecommendationRequest request) {
            return response(summary, confidence, keyReasons);
        }
        @Override public AiProviderHealthStatus healthCheck() { return new AiProviderHealthStatus(AiProviderType.OPENAI, true, "OK", ""); }
        @Override public AiProviderType getProviderType() { return AiProviderType.OPENAI; }
    }

    private static final class ThrowingProvider implements AiRecommendationProvider {
        @Override public AiRecommendationResponse analyzeStock(AiRecommendationRequest request) throws AiRecommendationException {
            throw new AiRecommendationException("network error");
        }
        @Override public AiProviderHealthStatus healthCheck() { return new AiProviderHealthStatus(AiProviderType.OPENAI, true, "OK", ""); }
        @Override public AiProviderType getProviderType() { return AiProviderType.OPENAI; }
    }
}
