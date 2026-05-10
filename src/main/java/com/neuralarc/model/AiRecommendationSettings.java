package com.neuralarc.model;

import java.time.Duration;

public record AiRecommendationSettings(
        AiProviderType providerType,
        String jetsonHost,
        int jetsonPort,
        String jetsonApiPath,
        Duration jetsonConnectionTimeout,
        Duration jetsonReadTimeout,
        String openAiApiKey,
        String openAiModel,
        Duration openAiTimeout
) {
    public static AiRecommendationSettings defaults() {
        return new AiRecommendationSettings(
                AiProviderType.JETSON_LOCAL,
                "192.168.1.55",
                8080,
                "/api/analyze-stock",
                Duration.ofSeconds(5),
                Duration.ofSeconds(30),
                "",
                "gpt-5",
                Duration.ofSeconds(45)
        );
    }

    public AiRecommendationSettings {
        providerType = providerType == null ? AiProviderType.JETSON_LOCAL : providerType;
        jetsonHost = jetsonHost == null ? "" : jetsonHost.trim();
        jetsonPort = jetsonPort <= 0 ? 8080 : jetsonPort;
        jetsonApiPath = normalizePath(jetsonApiPath);
        jetsonConnectionTimeout = validDuration(jetsonConnectionTimeout, Duration.ofSeconds(5));
        jetsonReadTimeout = validDuration(jetsonReadTimeout, Duration.ofSeconds(30));
        openAiApiKey = openAiApiKey == null ? "" : openAiApiKey.trim();
        openAiModel = openAiModel == null || openAiModel.isBlank() ? "gpt-5" : openAiModel.trim();
        openAiTimeout = validDuration(openAiTimeout, Duration.ofSeconds(45));
    }

    private static String normalizePath(String value) {
        String path = value == null || value.isBlank() ? "/api/analyze-stock" : value.trim();
        return path.startsWith("/") ? path : "/" + path;
    }

    private static Duration validDuration(Duration value, Duration fallback) {
        if (value == null || value.isZero() || value.isNegative()) {
            return fallback;
        }
        return value;
    }
}
