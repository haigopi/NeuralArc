package com.neuralarc.service;

import com.neuralarc.model.AiRecommendationRequest;
import com.neuralarc.model.AiRecommendationResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class AiRecommendationService {
    private static final Logger LOGGER = Logger.getLogger(AiRecommendationService.class.getName());
    private static final Duration MIN_REQUEST_INTERVAL = Duration.ofSeconds(60);

    private final Map<String, AiRecommendationResponse> latestBySymbol = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastRequestBySymbol = new ConcurrentHashMap<>();

    public AiRecommendationResponse requestRecommendation(
            AiRecommendationProvider provider,
            AiRecommendationRequest request
    ) throws AiRecommendationException {
        if (provider == null) {
            throw new AiRecommendationException("AI recommendation provider is not configured.");
        }
        String symbol = request.symbol().toUpperCase(Locale.ROOT);
        Instant now = Instant.now();
        Instant lastRequest = lastRequestBySymbol.get(symbol);
        if (lastRequest != null && Duration.between(lastRequest, now).compareTo(MIN_REQUEST_INTERVAL) < 0) {
            return Optional.ofNullable(latestBySymbol.get(symbol))
                    .orElseThrow(() -> new AiRecommendationException("Please wait before requesting another AI recommendation for " + symbol + "."));
        }
        lastRequestBySymbol.put(symbol, now);
        LOGGER.info(() -> "AI recommendation request started: provider=" + provider.getProviderType() + " symbol=" + symbol);
        AiRecommendationResponse response = provider.analyzeStock(request);
        latestBySymbol.put(symbol, response);
        LOGGER.info(() -> "AI recommendation request completed: provider=" + provider.getProviderType() + " symbol=" + symbol);
        return response;
    }

    public Optional<AiRecommendationResponse> latest(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(latestBySymbol.get(symbol.trim().toUpperCase(Locale.ROOT)));
    }
}
