package com.neuralarc.service;

import com.neuralarc.model.AiProviderType;
import com.neuralarc.model.AiRecommendationRequest;
import com.neuralarc.model.AiRecommendationResponse;
import com.neuralarc.model.AiRecommendationValue;
import com.neuralarc.model.AiSourceAnalyzed;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class AiRecommendationJsonMapper {
    private AiRecommendationJsonMapper() {
    }

    static JSONObject requestToJson(AiRecommendationRequest request) {
        AiRecommendationRequest.CurrentAnalysis analysis = request.currentAnalysis();
        return new JSONObject()
                .put("symbol", request.symbol())
                .put("currentPrice", request.currentPrice())
                .put("analysisTimestamp", request.analysisTimestamp().toString())
                .put("strategyName", request.strategyName())
                .put("currentAnalysis", new JSONObject()
                        .put("technicalSummary", analysis.technicalSummary())
                        .put("support", analysis.support())
                        .put("resistance", analysis.resistance())
                        .put("volumeSignal", analysis.volumeSignal())
                        .put("riskLevel", analysis.riskLevel())
                        .put("recommendationContext", analysis.recommendationContext()))
                .put("instruction", request.instruction());
    }

    static AiRecommendationResponse responseFromJson(JSONObject json, AiProviderType fallbackProvider) {
        AiProviderType provider = parseProvider(json.optString("provider", ""), fallbackProvider);
        return new AiRecommendationResponse(
                json.optString("symbol", ""),
                provider,
                parseRecommendation(json.optString("recommendation", "WAIT")),
                json.optDouble("confidence", 0.0d),
                json.optString("summary", ""),
                stringList(json.optJSONArray("keyReasons")),
                stringList(json.optJSONArray("risks")),
                sources(json.optJSONArray("sourcesAnalyzed")),
                parseInstant(json.optString("generatedAt", ""), Instant.now())
        );
    }

    static JSONObject responseSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("additionalProperties", false)
                .put("required", new JSONArray(List.of(
                        "symbol", "provider", "recommendation", "confidence", "summary",
                        "keyReasons", "risks", "sourcesAnalyzed", "generatedAt"
                )))
                .put("properties", new JSONObject()
                        .put("symbol", new JSONObject().put("type", "string"))
                        .put("provider", new JSONObject().put("type", "string").put("enum", new JSONArray(List.of("JETSON_LOCAL", "OPENAI"))))
                        .put("recommendation", new JSONObject().put("type", "string").put("enum", new JSONArray(List.of("BUY", "HOLD", "WAIT", "SELL", "AVOID"))))
                        .put("confidence", new JSONObject().put("type", "number").put("minimum", 0).put("maximum", 1))
                        .put("summary", new JSONObject().put("type", "string"))
                        .put("keyReasons", stringArraySchema())
                        .put("risks", stringArraySchema())
                        .put("sourcesAnalyzed", new JSONObject()
                                .put("type", "array")
                                .put("items", new JSONObject()
                                        .put("type", "object")
                                        .put("additionalProperties", false)
                                        .put("required", new JSONArray(List.of("title", "url", "publishedAt")))
                                        .put("properties", new JSONObject()
                                                .put("title", new JSONObject().put("type", "string"))
                                                .put("url", new JSONObject().put("type", "string"))
                                                .put("publishedAt", new JSONObject().put("type", "string")))))
                        .put("generatedAt", new JSONObject().put("type", "string")));
    }

    private static JSONObject stringArraySchema() {
        return new JSONObject()
                .put("type", "array")
                .put("items", new JSONObject().put("type", "string"));
    }

    private static AiProviderType parseProvider(String value, AiProviderType fallback) {
        try {
            return AiProviderType.valueOf(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static AiRecommendationValue parseRecommendation(String value) {
        try {
            return AiRecommendationValue.valueOf(value.trim().toUpperCase());
        } catch (Exception ignored) {
            return AiRecommendationValue.WAIT;
        }
    }

    private static List<String> stringList(JSONArray array) {
        if (array == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            values.add(array.optString(i, "").trim());
        }
        return values.stream().filter(value -> !value.isBlank()).toList();
    }

    private static List<AiSourceAnalyzed> sources(JSONArray array) {
        if (array == null) {
            return List.of();
        }
        List<AiSourceAnalyzed> values = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject source = array.optJSONObject(i);
            if (source == null) {
                continue;
            }
            values.add(new AiSourceAnalyzed(
                    source.optString("title", ""),
                    source.optString("url", ""),
                    parseInstant(source.optString("publishedAt", ""), null)
            ));
        }
        return values;
    }

    private static Instant parseInstant(String value, Instant fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Instant.parse(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
