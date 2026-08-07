package com.neuralarc.ui;

import com.neuralarc.api.AlpacaOrderData;
import com.neuralarc.api.AlpacaTradeUpdateEvent;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.math.BigDecimal;
import java.util.Optional;

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
        assertEquals("strategy-1", gateway.lastRefreshedStrategyId);
        assertTrue(gateway.tradeUpdateForwarded);
        assertTrue(gateway.uiRefreshTriggered);
        assertEquals(1, gateway.syncStrategiesCalls);
        assertEquals(1, gateway.refreshTableCalls);
        assertEquals(1, gateway.toasts.size(), "a fill should surface a toast");
        assertTrue(gateway.toasts.get(0).text().contains("AAPL"), gateway.toasts.get(0).text());
    }

    @Test
    void routineOrderAcknowledgementDoesNotRaiseAToast() {
        FakeGateway gateway = new FakeGateway();
        TradeStreamLifecycleCoordinator coordinator = new TradeStreamLifecycleCoordinator(
                gateway, (ignoredUrl, ignoredKey, ignoredSecret) -> gateway.client);
        AlpacaTradeUpdateEvent event = new AlpacaTradeUpdateEvent(
                "new",
                new AlpacaOrderData("oid", "cid", "AAPL", "buy", "limit", new BigDecimal("10"),
                        BigDecimal.ZERO, BigDecimal.ZERO, "new", "{}")
        );

        coordinator.onTradeEvent(event);

        assertTrue(gateway.toasts.isEmpty(), "acknowledgements must not toast, but must still update the UI");
        assertTrue(gateway.tradeUpdateForwarded);
        assertEquals(1, gateway.refreshTableCalls);
    }

    @Test
    void tradeEventWithoutMatchedStrategyStillSyncsAndRefreshesTable() {
        FakeGateway gateway = new FakeGateway();
        gateway.onTradeUpdateResult = Optional.empty();
        TradeStreamLifecycleCoordinator coordinator = new TradeStreamLifecycleCoordinator(gateway, (ignoredUrl, ignoredKey, ignoredSecret) -> gateway.client);
        AlpacaTradeUpdateEvent event = new AlpacaTradeUpdateEvent(
                "fill",
                new AlpacaOrderData("oid", "cid", "AAPL", "buy", "limit", new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, "filled", "{}")
        );

        coordinator.onTradeEvent(event);

        assertTrue(gateway.lastRefreshedStrategyId == null);
        assertTrue(gateway.tradeUpdateForwarded);
        assertEquals(1, gateway.syncStrategiesCalls);
        assertEquals(1, gateway.refreshTableCalls);
    }

    @Test
    void mapStatusRecognizesConnectedState() {
        TradeStreamLifecycleCoordinator.StreamStatusPresentation presentation = TradeStreamLifecycleCoordinator.mapStatus("WebSocket connected");
        assertEquals("connected", presentation.label());
        assertEquals(new Color(180, 100, 0), presentation.color());
    }

    @Test
    void mapStatusDoesNotMisclassifyUnauthorizedAsAuthorized() {
        TradeStreamLifecycleCoordinator.StreamStatusPresentation presentation =
                TradeStreamLifecycleCoordinator.mapStatus("Authorization stream: status=unauthorized action=authenticate");

        assertEquals("error", presentation.label());
        assertEquals(new Color(180, 30, 30), presentation.color());
    }

    @Test
    void tradeEventWithoutMatchedStrategyDoesNotRequestTargetedPositionRefresh() {
        FakeGateway gateway = new FakeGateway();
        gateway.onTradeUpdateResult = Optional.empty();
        TradeStreamLifecycleCoordinator coordinator = new TradeStreamLifecycleCoordinator(gateway, (ignoredUrl, ignoredKey, ignoredSecret) -> gateway.client);
        AlpacaTradeUpdateEvent event = new AlpacaTradeUpdateEvent(
                "fill",
                new AlpacaOrderData("oid", "cid", "AAPL", "buy", "limit", new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, "filled", "{}")
        );

        coordinator.onTradeEvent(event);

        assertTrue(gateway.lastRefreshedStrategyId == null);
        assertTrue(gateway.tradeUpdateForwarded);
    }

    private static final class FakeGateway implements TradeStreamLifecycleCoordinator.Gateway {
        boolean webSocketEnabled = true;
        String lastStatus;
        Color lastColor;
        String lastRefreshedStrategyId;
        boolean tradeUpdateForwarded;
        boolean uiRefreshTriggered;
        int syncStrategiesCalls;
        int refreshTableCalls;
        Optional<String> onTradeUpdateResult = Optional.of("strategy-1");
        final java.util.List<TradeEventToastFormatter.ToastMessage> toasts = new java.util.ArrayList<>();
        final FakeStreamClient client = new FakeStreamClient();

        @Override public boolean webSocketEnabled() { return webSocketEnabled; }
        @Override public String streamUrl(boolean liveMode) { return "wss://example"; }
        @Override public void updateStreamStatus(String status, Color color) { this.lastStatus = status; this.lastColor = color; }
        @Override public void onStreamError(String message) { }
        @Override public void log(String message) { }
        @Override public boolean canProcessTradeUpdates() { return true; }
        @Override public Optional<String> onTradeUpdate(AlpacaTradeUpdateEvent event) { tradeUpdateForwarded = true; return onTradeUpdateResult; }
        @Override public void refreshDisplayedPositionFromStream(String strategyId) { lastRefreshedStrategyId = strategyId; }
        @Override public void showTradeEventToast(TradeEventToastFormatter.ToastMessage message) { toasts.add(message); }
        @Override public void invokeLater(Runnable runnable) { runnable.run(); }
        @Override public void syncStrategiesFromRepository() { uiRefreshTriggered = true; syncStrategiesCalls++; }
        @Override public void refreshStrategyTableContent() { refreshTableCalls++; }
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
