package com.neuralarc.analytics;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpAnalyticsPublisher implements AnalyticsPublisher {
    private static final Logger LOGGER = Logger.getLogger(HttpAnalyticsPublisher.class.getName());
    private final TelemetryConfig config;
    private final AnalyticsQueue queue;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean running = true;

    public HttpAnalyticsPublisher(TelemetryConfig config, AnalyticsQueue queue) {
        this.config = config;
        this.queue = queue;
        worker.submit(this::runLoop);
    }

    @Override
    public void publish(AnalyticsEvent event) {
        if (!config.enabled()) {
            return;
        }
        queue.enqueue(event);
    }

    private void runLoop() {
        while (running) {
            try {
                AnalyticsEvent event = queue.take();
                sendWithRetry(event);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void sendWithRetry(AnalyticsEvent event) {
        int attempts = 0;
        while (attempts < 4) {
            attempts++;
            try {
                String payload = event.toJson();
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(config.endpointUrl()))
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload));
                if (config.authorizationHeader() != null && !config.authorizationHeader().isBlank()) {
                    builder.header("Authorization", config.authorizationHeader());
                }
                logRequest(config.endpointUrl(), payload, attempts);
                HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                logResponse(config.endpointUrl(), response.statusCode(), response.body(), attempts);
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return;
                }
            } catch (IOException | InterruptedException ex) {
                LOGGER.log(Level.WARNING, "Analytics API failure on attempt " + attempts + " to " + config.endpointUrl(), ex);
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            try {
                Thread.sleep((long) Math.pow(2, attempts) * 500L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        queue.persist(event);
    }

    private void logRequest(String endpoint, String payload, int attempt) {
        if (!LOGGER.isLoggable(Level.INFO)) {
            return;
        }
        LOGGER.info(() -> "Analytics API request: POST " + endpoint + " attempt=" + attempt
                + " body=" + abbreviate(payload));
    }

    private void logResponse(String endpoint, int statusCode, String body, int attempt) {
        if (!LOGGER.isLoggable(Level.INFO)) {
            return;
        }
        LOGGER.info(() -> "Analytics API response: POST " + endpoint + " attempt=" + attempt
                + " status=" + statusCode + " body=" + abbreviate(body));
    }

    private String abbreviate(String body) {
        if (body == null || body.isBlank()) {
            return "<empty>";
        }
        String flattened = body.replaceAll("\\s+", " ").trim();
        return flattened.length() <= 300 ? flattened : flattened.substring(0, 300) + "...";
    }

    @Override
    public void shutdown() {
        running = false;
        worker.shutdownNow();
    }
}
