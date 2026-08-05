package com.neuralarc.ui;

import com.neuralarc.api.AlpacaTradeUpdateEvent;
import com.neuralarc.api.AlpacaTradingWebSocketClient;

import java.awt.Color;
import java.util.Optional;
import java.util.function.Consumer;

public final class TradeStreamLifecycleCoordinator {
    private static final Color STREAM_IDLE = new Color(150, 150, 160);
    private static final Color STREAM_WARN = new Color(180, 100, 0);
    private static final Color STREAM_OK = new Color(34, 139, 34);
    private static final Color STREAM_TRADE_UPDATE = new Color(46, 125, 50);
    private static final Color STREAM_ERR = new Color(180, 30, 30);

    private final Gateway gateway;
    private final StreamClientFactory streamClientFactory;
    private StreamClient streamClient;

    public TradeStreamLifecycleCoordinator(Gateway gateway) {
        this(gateway, AlpacaStreamClientAdapter::new);
    }

    TradeStreamLifecycleCoordinator(Gateway gateway, StreamClientFactory streamClientFactory) {
        this.gateway = gateway;
        this.streamClientFactory = streamClientFactory;
    }

    public void start(String apiKey, String apiSecret, boolean liveMode) {
        stop();
        if (!gateway.webSocketEnabled()) {
            gateway.updateStreamStatus("disabled", STREAM_IDLE);
            gateway.log("[STREAM] Trading WebSocket disabled by app metadata.");
            return;
        }
        String streamUrl = gateway.streamUrl(liveMode);
        gateway.log("[STREAM] Starting trading WebSocket mode=" + (liveMode ? "LIVE" : "PAPER")
                + " url=" + safeStreamUrl(streamUrl)
                + " credentialsConfigured=" + credentialsConfigured(apiKey, apiSecret));
        streamClient = streamClientFactory.create(streamUrl, apiKey, apiSecret);
        if (streamClient == null || !streamClient.isConfigured()) {
            gateway.updateStreamStatus("not configured", STREAM_WARN);
            gateway.log("[STREAM] Trading WebSocket not configured. urlPresent="
                    + (streamUrl != null && !streamUrl.isBlank())
                    + " credentialsConfigured=" + credentialsConfigured(apiKey, apiSecret));
            return;
        }

        gateway.updateStreamStatus("connecting", STREAM_WARN);
        streamClient.start(
                this::onTradeEvent,
                status -> {
                    gateway.log("[STREAM] " + status);
                    StreamStatusPresentation statusPresentation = mapStatus(status);
                    gateway.updateStreamStatus(statusPresentation.label(), statusPresentation.color());
                },
                ex -> {
                    String message = ex == null || ex.getMessage() == null || ex.getMessage().isBlank()
                            ? "Unknown stream error"
                            : ex.getMessage();
                    gateway.log("[STREAM] Trade event stream error: " + message);
                    gateway.updateStreamStatus("error", STREAM_ERR);
                    gateway.onStreamError(message);
                }
        );
        gateway.log("[STREAM] Trading WebSocket worker started; awaiting authorization and listen acknowledgement.");
    }

    private String safeStreamUrl(String streamUrl) {
        if (streamUrl == null || streamUrl.isBlank()) {
            return "-";
        }
        return streamUrl.trim();
    }

    private boolean credentialsConfigured(String apiKey, String apiSecret) {
        return apiKey != null && !apiKey.isBlank() && apiSecret != null && !apiSecret.isBlank();
    }

    public void stop() {
        if (streamClient == null) {
            return;
        }
        streamClient.stop();
        streamClient = null;
        gateway.updateStreamStatus("idle", STREAM_IDLE);
    }

    public void onTradeEvent(AlpacaTradeUpdateEvent event) {
        if (event == null || !gateway.canProcessTradeUpdates()) {
            return;
        }
        gateway.updateStreamStatus("trade update", STREAM_TRADE_UPDATE);
        String orderId = event.orderData() == null ? "" : event.orderData().orderId();
        String clientOrderId = event.orderData() == null ? "" : event.orderData().clientOrderId();
        gateway.log("[STREAM] Trade update received: event=" + event.eventType()
                + " orderId=" + orderId
                + " clientOrderId=" + clientOrderId);
        Optional<String> strategyId = gateway.onTradeUpdate(event);
        if (strategyId.isPresent()) {
            // Loads the updated position for the matched strategy (background HTTP) and
            // schedules a targeted UI refresh only when the strategy's mode matches the
            // current view — avoids cross-mode repaints from live stream events while
            // the user is on the paper view.
            gateway.refreshDisplayedPositionFromStream(strategyId.get());
        }
        gateway.invokeLater(() -> {
            // syncStrategiesFromRepository can add or remove entries from the shared
            // strategies list. refreshStrategyTableContent must always follow so the
            // table model fires fireTableDataChanged and keeps the TableRowSorter's
            // cached row-count consistent with the underlying list.
            gateway.syncStrategiesFromRepository();
            gateway.refreshStrategyTableContent();
            gateway.refreshPanels();
            gateway.updateStatusBar();
        });
    }

    static StreamStatusPresentation mapStatus(String status) {
        String normalized = status == null ? "" : status.toLowerCase();
        if (normalized.contains("unauthorized") || normalized.contains("error") || normalized.contains("failed")) {
            return new StreamStatusPresentation("error", STREAM_ERR);
        }
        if (normalized.contains("authorized")) {
            return new StreamStatusPresentation("authorized", STREAM_OK);
        }
        if (normalized.contains("listening")) {
            return new StreamStatusPresentation("listening", STREAM_OK);
        }
        if (normalized.contains("connected")) {
            return new StreamStatusPresentation("connected", STREAM_WARN);
        }
        return new StreamStatusPresentation(status, STREAM_IDLE);
    }

    public interface Gateway {
        boolean webSocketEnabled();
        String streamUrl(boolean liveMode);
        void updateStreamStatus(String status, Color color);
        void onStreamError(String message);
        void log(String message);

        boolean canProcessTradeUpdates();
        Optional<String> onTradeUpdate(AlpacaTradeUpdateEvent event);
        void refreshDisplayedPositionFromStream(String strategyId);

        void invokeLater(Runnable runnable);
        void syncStrategiesFromRepository();
        void refreshStrategyTableContent();
        void refreshPanels();
        void updateStatusBar();
    }

    @FunctionalInterface
    interface StreamClientFactory {
        StreamClient create(String streamUrl, String apiKey, String apiSecret);
    }

    interface StreamClient {
        boolean isConfigured();
        void start(Consumer<AlpacaTradeUpdateEvent> onEvent, Consumer<String> onStatus, Consumer<Throwable> onError);
        void stop();
    }

    record StreamStatusPresentation(String label, Color color) {
    }

    private static final class AlpacaStreamClientAdapter implements StreamClient {
        private final AlpacaTradingWebSocketClient delegate;

        private AlpacaStreamClientAdapter(String streamUrl, String apiKey, String apiSecret) {
            this.delegate = new AlpacaTradingWebSocketClient(streamUrl, apiKey, apiSecret);
        }

        @Override
        public boolean isConfigured() {
            return delegate.isConfigured();
        }

        @Override
        public void start(Consumer<AlpacaTradeUpdateEvent> onEvent, Consumer<String> onStatus, Consumer<Throwable> onError) {
            delegate.start(onEvent, onStatus, onError);
        }

        @Override
        public void stop() {
            delegate.stop();
        }
    }
}
