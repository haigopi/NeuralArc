package com.neuralarc.analytics;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HttpAnalyticsPublisher implements AnalyticsPublisher {
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
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(config.endpointUrl()))
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(event.toJson()));
                if (config.authorizationHeader() != null && !config.authorizationHeader().isBlank()) {
                    builder.header("Authorization", config.authorizationHeader());
                }
                HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return;
                }
            } catch (IOException | InterruptedException ex) {
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

    @Override
    public void shutdown() {
        running = false;
        worker.shutdownNow();
    }
}
