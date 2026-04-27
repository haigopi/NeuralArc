package com.neuralarc.api;

import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class AlpacaTradingWebSocketClient {
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String streamUrl;
    private final String apiKey;
    private final String apiSecret;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger retryDelaySeconds = new AtomicInteger(2);
    private volatile WebSocket webSocket;
    private Thread worker;

    public AlpacaTradingWebSocketClient(String streamUrl, String apiKey, String apiSecret) {
        this.streamUrl = streamUrl == null ? "" : streamUrl.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.apiSecret = apiSecret == null ? "" : apiSecret.trim();
    }

    public boolean isConfigured() {
        return !streamUrl.isBlank() && !apiKey.isBlank() && !apiSecret.isBlank();
    }

    public synchronized void start(Consumer<AlpacaTradeUpdateEvent> onEvent, Consumer<Throwable> onError) {
        if (running.get() || !isConfigured()) {
            return;
        }
        running.set(true);
        worker = new Thread(() -> runLoop(onEvent, onError), "alpaca-trading-websocket");
        worker.setDaemon(true);
        worker.start();
    }

    public synchronized void stop() {
        running.set(false);
        WebSocket socket = webSocket;
        webSocket = null;
        if (socket != null) {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
        }
        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
    }

    private void runLoop(Consumer<AlpacaTradeUpdateEvent> onEvent, Consumer<Throwable> onError) {
        while (running.get()) {
            try {
                connectOnce(onEvent);
                retryDelaySeconds.set(2);
                waitForSocketClose();
            } catch (Exception ex) {
                onError.accept(ex);
            }
            if (!running.get()) {
                return;
            }
            int retrySeconds = retryDelaySeconds.get();
            sleepQuietly(retrySeconds);
            retryDelaySeconds.set(Math.min(30, Math.max(1, retrySeconds) * 2));
        }
    }

    private void connectOnce(Consumer<AlpacaTradeUpdateEvent> onEvent) {
        TradingListener listener = new TradingListener(onEvent);
        WebSocket socket = httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .buildAsync(URI.create(streamUrl), listener)
                .join();
        webSocket = socket;
        socket.sendText(authMessage().toString(), true).join();
        socket.sendText(listenMessage().toString(), true).join();
    }

    private void waitForSocketClose() throws InterruptedException {
        while (running.get() && webSocket != null && !Thread.currentThread().isInterrupted()) {
            Thread.sleep(250L);
        }
    }

    private JSONObject authMessage() {
        return new JSONObject()
                .put("action", "auth")
                .put("key", apiKey)
                .put("secret", apiSecret);
    }

    private JSONObject listenMessage() {
        return new JSONObject()
                .put("action", "listen")
                .put("data", new JSONObject().put("streams", List.of("trade_updates")));
    }

    private void sleepQuietly(int seconds) {
        try {
            Thread.sleep(Math.max(1, seconds) * 1000L);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private final class TradingListener implements WebSocket.Listener {
        private final Consumer<AlpacaTradeUpdateEvent> onEvent;
        private final StringBuilder textBuffer = new StringBuilder();
        private final List<byte[]> binaryChunks = new ArrayList<>();
        private int binarySize;

        private TradingListener(Consumer<AlpacaTradeUpdateEvent> onEvent) {
            this.onEvent = onEvent;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textBuffer.append(data);
            if (last) {
                publishPayload(textBuffer.toString());
                textBuffer.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            binaryChunks.add(bytes);
            binarySize += bytes.length;
            if (last) {
                byte[] merged = new byte[binarySize];
                int offset = 0;
                for (byte[] chunk : binaryChunks) {
                    System.arraycopy(chunk, 0, merged, offset, chunk.length);
                    offset += chunk.length;
                }
                publishPayload(new String(merged, StandardCharsets.UTF_8));
                binaryChunks.clear();
                binarySize = 0;
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            AlpacaTradingWebSocketClient.this.webSocket = null;
            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            AlpacaTradingWebSocketClient.this.webSocket = null;
            WebSocket.Listener.super.onError(webSocket, error);
        }

        private void publishPayload(String payload) {
            for (AlpacaTradeUpdateEvent event : AlpacaTradeUpdateEventParser.parseAll(payload)) {
                onEvent.accept(event);
            }
        }
    }
}
