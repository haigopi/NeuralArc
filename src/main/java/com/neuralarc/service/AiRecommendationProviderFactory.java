package com.neuralarc.service;

import com.neuralarc.model.AiProviderType;
import com.neuralarc.model.AiRecommendationSettings;

public final class AiRecommendationProviderFactory {
    private AiRecommendationProviderFactory() {
    }

    public static AiRecommendationProvider create(AiRecommendationSettings settings) {
        AiRecommendationSettings safe = settings == null ? AiRecommendationSettings.defaults() : settings;
        return safe.providerType() == AiProviderType.OPENAI
                ? new OpenAiRecommendationProvider(safe)
                : new JetsonAiRecommendationProvider(safe);
    }
}
