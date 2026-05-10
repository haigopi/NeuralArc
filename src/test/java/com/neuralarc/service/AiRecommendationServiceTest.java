package com.neuralarc.service;

import com.neuralarc.model.AiProviderHealthStatus;
import com.neuralarc.model.AiProviderType;
import com.neuralarc.model.AiRecommendationRequest;
import com.neuralarc.model.AiRecommendationResponse;
import com.neuralarc.model.AiRecommendationValue;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiRecommendationServiceTest {
    @Test
    void cachesLatestRecommendationWhenSymbolIsRequestedTooSoon() throws Exception {
        AiRecommendationService service = new AiRecommendationService();
        CountingProvider provider = new CountingProvider();
        AiRecommendationRequest request = request("NVDA");

        AiRecommendationResponse first = service.requestRecommendation(provider, request);
        AiRecommendationResponse second = service.requestRecommendation(provider, request);

        assertEquals(first, second);
        assertEquals(1, provider.calls.get());
        assertEquals(first, service.latest("nvda").orElseThrow());
    }

    private AiRecommendationRequest request(String symbol) {
        return new AiRecommendationRequest(
                symbol,
                new BigDecimal("125.42"),
                Instant.parse("2026-05-10T15:30:00Z"),
                "Short Term Momentum Strategy",
                new AiRecommendationRequest.CurrentAnalysis(
                        "Stock is trending above 20-day moving average.",
                        new BigDecimal("121.50"),
                        new BigDecimal("129.00"),
                        "HIGH",
                        "MEDIUM",
                        "User is evaluating whether to buy, hold, or wait."
                ),
                AiRecommendationRequest.DEFAULT_INSTRUCTION
        );
    }

    private static final class CountingProvider implements AiRecommendationProvider {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public AiRecommendationResponse analyzeStock(AiRecommendationRequest request) {
            calls.incrementAndGet();
            return new AiRecommendationResponse(
                    request.symbol(),
                    getProviderType(),
                    AiRecommendationValue.HOLD,
                    0.78,
                    "Recent articles suggest continued demand, but valuation remains elevated.",
                    List.of("Strong demand", "Positive sentiment"),
                    List.of("Valuation risk"),
                    List.of(),
                    Instant.parse("2026-05-10T15:31:00Z")
            );
        }

        @Override
        public AiProviderHealthStatus healthCheck() {
            return new AiProviderHealthStatus(getProviderType(), true, "OpenAI: Configured", "");
        }

        @Override
        public AiProviderType getProviderType() {
            return AiProviderType.OPENAI;
        }
    }
}
