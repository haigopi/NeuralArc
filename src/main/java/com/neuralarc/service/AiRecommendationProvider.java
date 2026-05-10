package com.neuralarc.service;

import com.neuralarc.model.AiProviderHealthStatus;
import com.neuralarc.model.AiProviderType;
import com.neuralarc.model.AiRecommendationRequest;
import com.neuralarc.model.AiRecommendationResponse;

public interface AiRecommendationProvider {
    AiRecommendationResponse analyzeStock(AiRecommendationRequest request) throws AiRecommendationException;

    AiProviderHealthStatus healthCheck();

    AiProviderType getProviderType();
}
