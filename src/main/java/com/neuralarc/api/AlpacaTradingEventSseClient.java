package com.neuralarc.api;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class AlpacaTradingEventSseClient {
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String streamUrl;
    private final String apiKey;
    private final String apiSecret;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger retryDelaySeconds = new AtomicInteger(2);
    private volatile String lastEventId;
    private Thread worker;

    public AlpacaTradingEventSseClient(String streamUrl, String apiKey, String apiSecret) {
        this.streamUrl = streamUrl == null ? "" : streamUrl.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.apiSecret = apiSecret == null ? "" : apiSecret.trim();
    }

    public boolean isConfigured() {
        return !streamUrl.isBlank();
    }

    public synchronized void start(Consumer<AlpacaTradeUpdateEvent> onEvent, Consumer<Throwable> onError) {
        if (running.get()) {
            return;
        }
        if (!isConfigured()) {
            return;
        }
        running.set(true);
        worker = new Thread(() -> runLoop(onEvent, onError), "alpaca-trading-sse");
        worker.setDaemon(true);
        worker.start();
    }

    public synchronized void stop() {
        running.set(false);
        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
    }

    private void runLoop(Consumer<AlpacaTradeUpdateEvent> onEvent, Consumer<Throwable> onError) {
        while (running.get()) {
            try {
                streamOnce(onEvent);
                retryDelaySeconds.set(2);
            } catch (Exception ex) {
                onError.accept(ex);
                if (!running.get()) {
                    return;
                }
                int retrySeconds = retryDelaySeconds.get();
                sleepQuietly(retrySeconds);
                retryDelaySeconds.set(Math.min(30, Math.max(1, retrySeconds) * 2));
            }
        }
    }

    private void streamOnce(Consumer<AlpacaTradeUpdateEvent> onEvent) throws Exception {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(streamUrl))
                .timeout(Duration.ofMinutes(30))
                .header("Accept", "text/event-stream")
                .header("APCA-API-KEY-ID", apiKey)
                .header("APCA-API-SECRET-KEY", apiSecret)
                .GET();
        if (lastEventId != null && !lastEventId.isBlank()) {
            requestBuilder.header("Last-Event-ID", lastEventId);
        }
        HttpRequest request = requestBuilder.build();

        HttpResponse<java.io.InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("SSE stream failed with status " + response.statusCode());
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String currentEvent = "";
            String currentId = "";
            StringBuilder data = new StringBuilder();
            StringBuilder raw = new StringBuilder();
            String line;
            while (running.get() && (line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    emitFrame(currentEvent, currentId, data.toString(), raw.toString(), onEvent);
                    currentEvent = "";
                    currentId = "";
                    data.setLength(0);
                    raw.setLength(0);
                    continue;
                }
                if (line.startsWith(":")) {
                    // SSE heartbeat/comment; keep connection alive and continue.
                    continue;
                }

                int separator = line.indexOf(':');
                if (separator > -1) {
                    String field = line.substring(0, separator).trim();
                    String value = line.substring(separator + 1).trim();
                    if ("event".equals(field)) {
                        currentEvent = value;
                        continue;
                    }
                    if ("id".equals(field)) {
                        currentId = value;
                        continue;
                    }
                    if ("retry".equals(field)) {
                        updateRetryDelay(value);
                        continue;
                    }
                    if (!"data".equals(field)) {
                        continue;
                    }
                    if (!data.isEmpty()) {
                        data.append('\n');
                    }
                    data.append(value);
                    continue;
                }

                // Some endpoints/examples emit plain JSON lines separated by blank lines.
                if (!raw.isEmpty()) {
                    raw.append('\n');
                }
                raw.append(line.trim());
            }
        }
    }

    private void emitFrame(String eventType, String eventId, String dataPayload, String rawPayload,
                           Consumer<AlpacaTradeUpdateEvent> onEvent) {
        if (eventId != null && !eventId.isBlank()) {
            lastEventId = eventId;
        }
        String payload = dataPayload == null || dataPayload.isBlank() ? rawPayload : dataPayload;
        if (payload == null || payload.isBlank()) {
            return;
        }
        AlpacaTradeUpdateEventParser.parse(payload).ifPresent(onEvent);
    }

    private void updateRetryDelay(String retryValue) {
        if (retryValue == null || retryValue.isBlank()) {
            return;
        }
        try {
            int millis = Integer.parseInt(retryValue.trim());
            int seconds = Math.max(1, millis / 1000);
            retryDelaySeconds.set(Math.min(30, seconds));
        } catch (NumberFormatException ignored) {
            // Ignore malformed retry hints and continue with exponential backoff.
        }
    }

    private void sleepQuietly(int seconds) {
        try {
            Thread.sleep(Math.max(1, seconds) * 1000L);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}

