package com.neuralarc.ui;

import com.neuralarc.api.TradingApi;
import com.neuralarc.model.ApplicationMode;
import com.neuralarc.model.BrokerType;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionLifecycleCoordinatorTest {
    @Test
    void autoInitializeConnectionReturnsFalseWhenCredentialsMissing() {
        FakeGateway gateway = new FakeGateway();
        gateway.savedApiKey = "";
        gateway.savedApiSecret = "";
        ConnectionLifecycleCoordinator coordinator = new ConnectionLifecycleCoordinator(gateway);

        boolean connected = coordinator.autoInitializeConnection();

        assertFalse(connected);
        assertEquals(0, gateway.connectionAttempts);
    }

    @Test
    void runConnectionTestConnectedWithoutRuntimeChangesReturnsSuccessMessage() {
        FakeGateway gateway = new FakeGateway();
        gateway.connectionResult = new TradingRuntimeSupport.ConnectionAttemptResult(
                true,
                null,
                "Connected",
                false,
                false
        );
        ConnectionLifecycleCoordinator coordinator = new ConnectionLifecycleCoordinator(gateway);

        SettingsDialog.ConnectionResult result = coordinator.runConnectionTest(
                BrokerType.ALPACA,
                ApplicationMode.PAPER,
                "key",
                "secret",
                true,
                false
        );

        assertTrue(result.connected());
        assertEquals("Connected to ALPACA (PAPER)", result.message());
        assertEquals("Connected to ALPACA (PAPER)", gateway.markedConnectionMessage);
        assertFalse(gateway.appliedSuccessfulRuntimeConnection);
    }

    @Test
    void retryBrokerConnectionIfConfiguredClearsPendingWhenSettingsMissing() {
        FakeGateway gateway = new FakeGateway();
        gateway.connectionRetryPending = true;
        gateway.appliedBrokerType = null;
        ConnectionLifecycleCoordinator coordinator = new ConnectionLifecycleCoordinator(gateway);

        coordinator.retryBrokerConnectionIfConfigured();

        assertFalse(gateway.connectionRetryPending);
    }

    private static final class FakeGateway implements ConnectionLifecycleCoordinator.Gateway {
        TradingRuntimeSupport.ConnectionAttemptResult connectionResult = new TradingRuntimeSupport.ConnectionAttemptResult(false, null, "Connection failed", false, false);
        int connectionAttempts;
        boolean connectionRetryPending;
        boolean retryTimerRunning;
        BrokerType appliedBrokerType = BrokerType.ALPACA;
        ApplicationMode appliedApplicationMode = ApplicationMode.PAPER;
        String savedApiKey = "key";
        String savedApiSecret = "secret";
        String markedConnectionMessage;
        boolean appliedSuccessfulRuntimeConnection;

        @Override
        public TradingRuntimeSupport.ConnectionAttemptResult attemptConnection(BrokerType brokerType, ApplicationMode mode, String apiKey, String apiSecret) {
            connectionAttempts++;
            return connectionResult;
        }

        @Override public void log(String message) { }
        @Override public void updateHeaderModeStatus(BrokerType brokerType) { }
        @Override public void setHeaderStatusText(String text) { }
        @Override public void markConnectionStatus(boolean connected, String message) { this.markedConnectionMessage = message; }

        @Override
        public void applySuccessfulRuntimeConnection(BrokerType brokerType, TradingApi candidateApi, String apiKey, String apiSecret, ApplicationMode mode) {
            appliedSuccessfulRuntimeConnection = true;
        }

        @Override public void applyFailedRuntimeConnection(BrokerType brokerType) { }
        @Override public void stopConnectionRetryTimer() { retryTimerRunning = false; }
        @Override public boolean isConnectionRetryTimerRunning() { return retryTimerRunning; }
        @Override public void restartConnectionRetryTimer() { retryTimerRunning = true; }
        @Override public void setConnectionRetryPending(boolean pending) { connectionRetryPending = pending; }
        @Override public boolean isConnectionRetryPending() { return connectionRetryPending; }
        @Override public BrokerType appliedBrokerType() { return appliedBrokerType; }
        @Override public ApplicationMode appliedApplicationMode() { return appliedApplicationMode; }
        @Override public String savedApiKey(ApplicationMode mode) { return savedApiKey; }
        @Override public String savedApiSecret(ApplicationMode mode) { return savedApiSecret; }
        @Override public Color statusErrorColor() { return Color.RED; }
        @Override public void setStatus(String message, Color tone) { }
    }
}

