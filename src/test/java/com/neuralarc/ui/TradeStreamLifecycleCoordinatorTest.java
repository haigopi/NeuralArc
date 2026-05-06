package com.neuralarc.ui;

import com.neuralarc.api.AlpacaOrderData;
import com.neuralarc.api.AlpacaTradeUpdateEvent;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeStreamLifecycleCoordinatorTest {
    @Test
    void startReportsDisabledWhenWebSocketFeatureOff() {
        FakeGateway gateway = new FakeGateway();
        gateway.webSocketEnabled = false;
        TradeStreamLifecycleCoordinator coordinator = new TradeStreamLifecycleCoordinator(gateway, (ignoredUrl, ignoredKey, ignoredSecret) -> gateway.client);

        coordinator.start("key", "secret", false);

        assertEquals("disabled", gateway.lastStatus);
    }

    @Test
    void startWithUnconfiguredClientReportsNotConfigured() {
        FakeGateway gateway = new FakeGateway();
        gateway.client.configured = false;
        TradeStreamLifecycleCoordinator coordinator = new TradeStreamLifecycleCoordinator(gateway, (ignoredUrl, ignoredKey, ignoredSecret) -> gateway.client);

        coordinator.start("key", "secret", false);

        assertEquals("not configured", gateway.lastStatus);
        assertFalse(gateway.client.started);
    }

    @Test
    void stopShutsDownActiveClientAndMarksIdle() {
        FakeGateway gateway = new FakeGateway();
        TradeStreamLifecycleCoordinator coordinator = new TradeStreamLifecycleCoordinator(gateway, (ignoredUrl, ignoredKey, ignoredSecret) -> gateway.client);
        coordinator.start("key", "secret", false);

        coordinator.stop();

        assertTrue(gateway.client.stopped);
        assertEquals("idle", gateway.lastStatus);
    }

    @Test
    void tradeEventProcessesAndRefreshesUiWhenPollingAvailable() {
        FakeGateway gateway = new FakeGateway();
        TradeStreamLifecycleCoordinator coordinator = new TradeStreamLifecycleCoordinator(gateway, (ignoredUrl, ignoredKey, ignoredSecret) -> gateway.client);
        AlpacaTradeUpdateEvent event = new AlpacaTradeUpdateEvent(
                "fill",
                new AlpacaOrderData("oid", "cid", "AAPL", "buy", "limit", new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, "filled", "{}")
        );

        coordinator.onTradeEvent(event);

        assertEquals("trade update", gateway.lastStatus);
        assertEquals("AAPL", gateway.lastRefreshedSymbol);
        assertTrue(gateway.tradeUpdateForwarded);
        assertTrue(gateway.uiRefreshTriggered);
    }

    @Test
    void mapStatusRecognizesConnectedState() {
        TradeStreamLifecycleCoordinator.StreamStatusPresentation presentation = TradeStreamLifecycleCoordinator.mapStatus("WebSocket connected");
        assertEquals("connected", presentation.label());
        assertEquals(new Color(180, 100, 0), presentation.color());
    }

    private static final class FakeGateway implements TradeStreamLifecycleCoordinator.Gateway {
        boolean webSocketEnabled = true;
        String lastStatus;
        Color lastColor;
        String lastRefreshedSymbol;
        boolean tradeUpdateForwarded;
        boolean uiRefreshTriggered;
        final FakeStreamClient client = new FakeStreamClient();

        @Override public boolean webSocketEnabled() { return webSocketEnabled; }
        @Override public String streamUrl(boolean liveMode) { return "wss://example"; }
        @Override public void updateStreamStatus(String status, Color color) { this.lastStatus = status; this.lastColor = color; }
        @Override public void onStreamError(String message) { }
        @Override public void log(String message) { }
        @Override public boolean canProcessTradeUpdates() { return true; }
        @Override public void onTradeUpdate(AlpacaTradeUpdateEvent event) { tradeUpdateForwarded = true; }
        @Override public void refreshDisplayedPositionFromStream(String symbol) { lastRefreshedSymbol = symbol; }
        @Override public void invokeLater(Runnable runnable) { runnable.run(); }
        @Override public void syncStrategiesFromRepository() { uiRefreshTriggered = true; }
        @Override public void refreshStrategyTableContent() { }
        @Override public void refreshPanels() { }
        @Override public void updateStatusBar() { }
    }

    private static final class FakeStreamClient implements TradeStreamLifecycleCoordinator.StreamClient {
        boolean configured = true;
        boolean started;
        boolean stopped;

        @Override public boolean isConfigured() { return configured; }
        @Override
        public void start(java.util.function.Consumer<AlpacaTradeUpdateEvent> onEvent,
                          java.util.function.Consumer<String> onStatus,
                          java.util.function.Consumer<Throwable> onError) {
            started = true;
        }
        @Override public void stop() { stopped = true; }
    }
}

