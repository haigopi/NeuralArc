package com.neuralarc.ui;

import com.neuralarc.api.TradingApi;
import com.neuralarc.model.ApplicationMode;
import com.neuralarc.model.BrokerType;

import java.awt.Color;

public final class ConnectionLifecycleCoordinator {
    private final Gateway gateway;

    public ConnectionLifecycleCoordinator(Gateway gateway) {
        this.gateway = gateway;
    }

    public boolean autoInitializeConnection() {
        ApplicationMode mode = gateway.appliedApplicationMode();
        String apiKey = gateway.savedApiKey(mode);
        String apiSecret = gateway.savedApiSecret(mode);
        if (apiKey == null || apiSecret == null || apiKey.isBlank() || apiSecret.isBlank()) {
            return false;
        }
        SettingsDialog.ConnectionResult result = runConnectionTest(
                gateway.appliedBrokerType(),
                mode,
                apiKey,
                apiSecret,
                false,
                true
        );
        return result.connected();
    }

    public SettingsDialog.ConnectionResult runConnectionTest(
            BrokerType brokerType,
            ApplicationMode mode,
            String apiKey,
            String apiSecret,
            boolean manualTrigger,
            boolean applyRuntimeChanges
    ) {
        TradingRuntimeSupport.ConnectionAttemptResult connectionAttempt = gateway.attemptConnection(
                brokerType,
                mode,
                apiKey,
                apiSecret
        );
        if (connectionAttempt.brokerMissing()) {
            gateway.log("Connection test: FAILED (broker not set in Settings)");
            gateway.updateHeaderModeStatus(null);
            gateway.setHeaderStatusText("Status: broker not configured");
            return new SettingsDialog.ConnectionResult(false, "Broker not configured");
        }
        if (connectionAttempt.liveDisabled()) {
            String message = connectionAttempt.message();
            gateway.markConnectionStatus(false, message);
            gateway.setStatus(message, gateway.statusErrorColor());
            return new SettingsDialog.ConnectionResult(false, message);
        }

        TradingApi candidateApi = connectionAttempt.tradingApi();
        boolean connected = connectionAttempt.connected();
        gateway.log((manualTrigger ? "Connection test: " : "Auto connection test: ") + (connected ? "SUCCESS" : "FAILED"));
        if (connected) {
            gateway.setConnectionRetryPending(false);
            gateway.stopConnectionRetryTimer();
            gateway.markConnectionStatus(true, "Connected to " + brokerType.name() + " (" + mode.name() + ")");
            if (applyRuntimeChanges) {
                gateway.applySuccessfulRuntimeConnection(brokerType, candidateApi, apiKey, apiSecret, mode);
            }
            if (!applyRuntimeChanges) {
                return new SettingsDialog.ConnectionResult(true, "Connected to " + brokerType.name() + " (" + mode.name() + ")");
            }
            gateway.markConnectionStatus(true, "Connected to " + brokerType.name());
            return new SettingsDialog.ConnectionResult(true, "Connected to " + brokerType.name());
        }

        if (applyRuntimeChanges) {
            gateway.applyFailedRuntimeConnection(brokerType);
        }
        gateway.markConnectionStatus(false, "Connection failed");
        return new SettingsDialog.ConnectionResult(false, "Connection failed");
    }

    public void scheduleConnectionRetry() {
        if (gateway.isConnectionRetryTimerRunning()) {
            return;
        }
        gateway.restartConnectionRetryTimer();
    }

    public void retryBrokerConnectionIfConfigured() {
        if (!gateway.isConnectionRetryPending()) {
            return;
        }
        BrokerType brokerType = gateway.appliedBrokerType();
        ApplicationMode mode = gateway.appliedApplicationMode();
        String apiKey = gateway.savedApiKey(mode);
        String apiSecret = gateway.savedApiSecret(mode);
        if (brokerType == null || apiKey == null || apiSecret == null || apiKey.isBlank() || apiSecret.isBlank()) {
            gateway.setConnectionRetryPending(false);
            return;
        }
        runConnectionTest(brokerType, mode, apiKey, apiSecret, false, true);
    }

    public interface Gateway {
        TradingRuntimeSupport.ConnectionAttemptResult attemptConnection(BrokerType brokerType, ApplicationMode mode, String apiKey, String apiSecret);
        void log(String message);
        void updateHeaderModeStatus(BrokerType brokerType);
        void setHeaderStatusText(String text);
        void markConnectionStatus(boolean connected, String message);

        void applySuccessfulRuntimeConnection(BrokerType brokerType, TradingApi candidateApi, String apiKey, String apiSecret, ApplicationMode mode);
        void applyFailedRuntimeConnection(BrokerType brokerType);

        void stopConnectionRetryTimer();
        boolean isConnectionRetryTimerRunning();
        void restartConnectionRetryTimer();
        void setConnectionRetryPending(boolean pending);
        boolean isConnectionRetryPending();

        BrokerType appliedBrokerType();
        ApplicationMode appliedApplicationMode();
        String savedApiKey(ApplicationMode mode);
        String savedApiSecret(ApplicationMode mode);

        Color statusErrorColor();
        void setStatus(String message, Color tone);
    }
}

