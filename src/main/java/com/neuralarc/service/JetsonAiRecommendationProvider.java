package com.neuralarc.service;

import com.neuralarc.model.AiProviderHealthStatus;
import com.neuralarc.model.AiProviderType;
import com.neuralarc.model.AiRecommendationRequest;
import com.neuralarc.model.AiRecommendationResponse;
import com.neuralarc.model.AiRecommendationSettings;
import org.json.JSONObject;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class JetsonAiRecommendationProvider implements AiRecommendationProvider {
    private final AiRecommendationSettings settings;
    private final HttpClient httpClient;

    public JetsonAiRecommendationProvider(AiRecommendationSettings settings) {
        this(settings, HttpClient.newBuilder()
                .connectTimeout((settings == null ? AiRecommendationSettings.defaults() : settings).jetsonConnectionTimeout())
                .build());
    }

    JetsonAiRecommendationProvider(AiRecommendationSettings settings, HttpClient httpClient) {
        this.settings = settings == null ? AiRecommendationSettings.defaults() : settings;
        this.httpClient = httpClient;
    }

    @Override
    public AiRecommendationResponse analyzeStock(AiRecommendationRequest request) throws AiRecommendationException {
        URI uri = endpointUri();
        ensureLocalEndpoint(uri);
        JSONObject payload = AiRecommendationJsonMapper.requestToJson(request);
        HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                .timeout(settings.jetsonReadTimeout())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AiRecommendationException("Jetson AI returned HTTP " + response.statusCode());
            }
            return AiRecommendationJsonMapper.responseFromJson(new JSONObject(response.body()), getProviderType());
        } catch (AiRecommendationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AiRecommendationException("Jetson AI request failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public AiProviderHealthStatus healthCheck() {
        try {
            URI uri = endpointUri();
            ensureLocalEndpoint(uri);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(settings.jetsonConnectionTimeout().plusSeconds(2))
                    .header("Content-Type", "application/json")
                    .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            boolean healthy = response.statusCode() < 500;
            return new AiProviderHealthStatus(getProviderType(), healthy,
                    healthy ? "Jetson: Connected" : "Jetson: Unreachable",
                    "HTTP " + response.statusCode());
        } catch (Exception ex) {
            return new AiProviderHealthStatus(getProviderType(), false, "Jetson: Unreachable", ex.getMessage());
        }
    }

    @Override
    public AiProviderType getProviderType() {
        return AiProviderType.JETSON_LOCAL;
    }

    private URI endpointUri() {
        String host = settings.jetsonHost();
        if (!host.startsWith("http://") && !host.startsWith("https://")) {
            host = "http://" + host;
        }
        URI base = URI.create(host);
        String scheme = base.getScheme() == null ? "http" : base.getScheme();
        String path = settings.jetsonApiPath();
        return URI.create(scheme + "://" + base.getHost() + ":" + settings.jetsonPort() + path);
    }

    private void ensureLocalEndpoint(URI uri) throws AiRecommendationException {
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new AiRecommendationException("Jetson host is missing.");
        }
        if (isAllowedLocalHostName(host)) {
            return;
        }
        try {
            InetAddress address = InetAddress.getByName(host);
            if (address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress()) {
                return;
            }
        } catch (Exception ex) {
            throw new AiRecommendationException("Jetson host could not be resolved locally.");
        }
        throw new AiRecommendationException("Jetson endpoint must be localhost, .local, a single-label LAN host, or a private network address.");
    }

    private boolean isAllowedLocalHostName(String host) {
        String lower = host.toLowerCase();
        return "localhost".equals(lower) || lower.endsWith(".local") || !lower.contains(".");
    }
}
