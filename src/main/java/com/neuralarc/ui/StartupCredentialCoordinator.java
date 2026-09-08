package com.neuralarc.ui;

import com.neuralarc.api.ConnectionCheck;
import com.neuralarc.model.ApplicationMode;
import com.neuralarc.model.BrokerType;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns the startup credential check so {@code TradingFrame} only supplies the UI gateway.
 *
 * <p>The broker probe runs after the window is up and off the Swing EDT. Settings is reopened only
 * when the saved keys are genuinely missing or rejected; an unreachable broker leaves the saved keys
 * alone and hands the problem to the existing connection retry timer.
 */
final class StartupCredentialCoordinator {
    /** Callbacks the coordinator needs from the host frame. */
    interface Ui {
        ApplicationMode appliedApplicationMode();
        BrokerType appliedBrokerType();
        String savedApiKey(ApplicationMode mode);
        String savedApiSecret(ApplicationMode mode);
        boolean liveTradingEnabled();
        void log(String message);
        void setVerifyingStatus(String message);
        SettingsDialog.ConnectionResult applyConnectionAttempt(
                TradingRuntimeSupport.ConnectionAttemptResult attempt,
                BrokerType brokerType,
                ApplicationMode mode,
                String apiKey,
                String apiSecret
        );
        void showCredentialProblem(String title, String headline, String summary);
        void openSettings();
        void runInBackground(Runnable task);
        void runOnUiThread(Runnable task);
    }

    private final Ui ui;
    private final StartupCredentialValidator validator;

    StartupCredentialCoordinator(Ui ui, StartupCredentialValidator validator) {
        this.ui = ui;
        this.validator = validator;
    }

    /** Probes the saved paper and live keys in the background, then applies the verdict on the EDT. */
    void verifySavedCredentials() {
        ApplicationMode appliedMode = ui.appliedApplicationMode();
        BrokerType brokerType = ui.appliedBrokerType();
        List<StartupCredentialValidator.ModeCredentials> credentials = savedCredentialsByMode();
        ui.setVerifyingStatus("Verifying saved " + StartupCredentialValidator.Report.modeLabel(appliedMode)
                + " Alpaca credentials...");
        ui.runInBackground(() -> {
            StartupCredentialValidator.Report report = validator.validate(appliedMode, credentials);
            ui.runOnUiThread(() -> applyReport(brokerType, report));
        });
    }

    private List<StartupCredentialValidator.ModeCredentials> savedCredentialsByMode() {
        List<StartupCredentialValidator.ModeCredentials> credentials = new ArrayList<>();
        for (ApplicationMode mode : ApplicationMode.values()) {
            credentials.add(new StartupCredentialValidator.ModeCredentials(
                    mode,
                    ui.savedApiKey(mode),
                    ui.savedApiSecret(mode),
                    mode != ApplicationMode.LIVE || ui.liveTradingEnabled()
            ));
        }
        return credentials;
    }

    /** EDT half: adopt a successful connection, or decide whether the operator has to be asked. */
    void applyReport(BrokerType brokerType, StartupCredentialValidator.Report report) {
        ApplicationMode appliedMode = report.appliedMode();
        ui.log("[STARTUP] Alpaca credential check - " + report.summary() + ".");
        if (report.appliedAttempt() != null) {
            SettingsDialog.ConnectionResult result = ui.applyConnectionAttempt(
                    report.appliedAttempt(),
                    brokerType,
                    appliedMode,
                    ui.savedApiKey(appliedMode),
                    ui.savedApiSecret(appliedMode)
            );
            if (result.connected()) {
                return;
            }
            if (!report.requiresOperatorAction()) {
                ui.log("[STARTUP] " + result.message()
                        + ". The saved keys were not rejected, so they are kept and the connection retry continues.");
                return;
            }
        }
        String modeLabel = StartupCredentialValidator.Report.modeLabel(appliedMode);
        ui.showCredentialProblem(modeLabel + " Credentials Required", headline(report, modeLabel), report.summary());
        ui.openSettings();
    }

    private static String headline(StartupCredentialValidator.Report report, String modeLabel) {
        StartupCredentialValidator.ModeStatus status = report.appliedStatus();
        if (report.appliedAttempt() != null && report.appliedAttempt().liveDisabled()) {
            return "Live trading is disabled in application configuration.";
        }
        if (status != null && status.status() == ConnectionCheck.Status.INVALID_CREDENTIALS) {
            return "Alpaca rejected the saved " + modeLabel + " API key and secret.";
        }
        return modeLabel + " Alpaca API key and secret are required.";
    }
}
