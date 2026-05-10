package com.neuralarc.service;

import com.neuralarc.model.AiProviderHealthStatus;
import com.neuralarc.model.AiProviderType;
import com.neuralarc.model.AiRecommendationRequest;
import com.neuralarc.model.AiRecommendationResponse;
import com.neuralarc.model.AiRecommendationSettings;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class OpenAiRecommendationProvider implements AiRecommendationProvider {
    private static final URI RESPONSES_URI = URI.create("https://api.openai.com/v1/responses");
    private static final String MODELS_URL = "https://api.openai.com/v1/models/";

    private final AiRecommendationSettings settings;
    private final HttpClient httpClient;

    public OpenAiRecommendationProvider(AiRecommendationSettings settings) {
        this(settings, HttpClient.newBuilder()
                .connectTimeout((settings == null ? AiRecommendationSettings.defaults() : settings).openAiTimeout())
                .build());
    }

    OpenAiRecommendationProvider(AiRecommendationSettings settings, HttpClient httpClient) {
        this.settings = settings == null ? AiRecommendationSettings.defaults() : settings;
        this.httpClient = httpClient;
    }

    @Override
    public AiRecommendationResponse analyzeStock(AiRecommendationRequest request) throws AiRecommendationException {
        if (settings.openAiApiKey().isBlank()) {
            throw new AiRecommendationException("OpenAI API key is missing.");
        }
        JSONObject payload = new JSONObject()
                .put("model", settings.openAiModel())
                .put("tools", new JSONArray().put(new JSONObject().put("type", "web_search")))
                .put("tool_choice", "auto")
                .put("include", new JSONArray().put("web_search_call.action.sources"))
                .put("input", prompt(request))
                .put("text", new JSONObject()
                        .put("format", new JSONObject()
                                .put("type", "json_schema")
                                .put("name", "ai_stock_recommendation")
                                .put("strict", true)
                                .put("schema", AiRecommendationJsonMapper.responseSchema())));

        HttpRequest httpRequest = HttpRequest.newBuilder(RESPONSES_URI)
                .timeout(settings.openAiTimeout())
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + settings.openAiApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AiRecommendationException("OpenAI request failed with HTTP " + response.statusCode());
            }
            return parseOpenAiResponse(response.body());
        } catch (AiRecommendationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AiRecommendationException("OpenAI request failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public AiProviderHealthStatus healthCheck() {
        if (settings.openAiApiKey().isBlank()) {
            return new AiProviderHealthStatus(getProviderType(), false, "OpenAI: Missing API Key", "");
        }
        try {
            String encodedModel = URLEncoder.encode(settings.openAiModel(), StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(MODELS_URL + encodedModel))
                    .timeout(settings.openAiTimeout())
                    .header("Authorization", "Bearer " + settings.openAiApiKey())
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            boolean healthy = response.statusCode() >= 200 && response.statusCode() < 300;
            return new AiProviderHealthStatus(getProviderType(), healthy,
                    healthy ? "OpenAI: Configured" : "OpenAI: Check Failed",
                    "HTTP " + response.statusCode());
        } catch (Exception ex) {
            return new AiProviderHealthStatus(getProviderType(), false, "OpenAI: Check Failed", ex.getMessage());
        }
    }

    @Override
    public AiProviderType getProviderType() {
        return AiProviderType.OPENAI;
    }

    private AiRecommendationResponse parseOpenAiResponse(String body) throws AiRecommendationException {
        JSONObject root = new JSONObject(body);
        String text = root.optString("output_text", "");
        if (text.isBlank()) {
            JSONArray output = root.optJSONArray("output");
            if (output != null) {
                for (int i = 0; i < output.length() && text.isBlank(); i++) {
                    JSONObject item = output.optJSONObject(i);
                    if (item == null || !"message".equals(item.optString("type"))) {
                        continue;
                    }
                    JSONArray content = item.optJSONArray("content");
                    if (content == null) {
                        continue;
                    }
                    for (int j = 0; j < content.length(); j++) {
                        JSONObject contentItem = content.optJSONObject(j);
                        if (contentItem != null && "output_text".equals(contentItem.optString("type"))) {
                            text = contentItem.optString("text", "");
                            break;
                        }
                    }
                }
            }
        }
        if (text.isBlank()) {
            throw new AiRecommendationException("OpenAI response did not include recommendation text.");
        }
        return AiRecommendationJsonMapper.responseFromJson(new JSONObject(text), getProviderType());
    }

    private String prompt(AiRecommendationRequest request) {
        JSONObject context = AiRecommendationJsonMapper.requestToJson(request);
        return """
                You are an AI recommendation provider for a desktop trading decision-support app.
                Use recent web articles, news, earnings commentary, analyst notes, and market sentiment for the stock.
                Combine external context with the current technical analysis JSON below.
                Do not recommend automatic trading. Return only JSON matching the requested schema.

                Current analysis JSON:
                """ + context;
    }
}
