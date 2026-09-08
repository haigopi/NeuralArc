package com.neuralarc.ui;

import com.neuralarc.api.ConnectionCheck;
import com.neuralarc.model.ApplicationMode;
import com.neuralarc.model.BrokerType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartupCredentialCoordinatorTest {
    @Test
    void validKeysConnectWithoutOpeningSettings() {
        FakeUi ui = new FakeUi();
        StartupCredentialCoordinator coordinator = coordinator(ui, ConnectionCheck.accepted());

        coordinator.verifySavedCredentials();

        assertTrue(ui.appliedAttempt);
        assertFalse(ui.settingsOpened);
        assertTrue(ui.credentialProblems.isEmpty());
    }

    @Test
    void unreachableBrokerKeepsSavedKeysAndDoesNotPrompt() {
        FakeUi ui = new FakeUi();
        StartupCredentialCoordinator coordinator = coordinator(ui, ConnectionCheck.unreachable("timeout"));

        coordinator.verifySavedCredentials();

        assertTrue(ui.appliedAttempt);
        assertFalse(ui.settingsOpened);
        assertTrue(ui.credentialProblems.isEmpty());
        assertTrue(ui.logs.stream().anyMatch(line -> line.contains("were not rejected")));
    }

    @Test
    void rejectedKeysPromptAndOpenSettings() {
        FakeUi ui = new FakeUi();
        StartupCredentialCoordinator coordinator = coordinator(ui, ConnectionCheck.invalidCredentials(401));

        coordinator.verifySavedCredentials();

        assertTrue(ui.settingsOpened);
        assertEquals(1, ui.credentialProblems.size());
        assertEquals("Paper Credentials Required", ui.credentialProblems.get(0).title());
        assertTrue(ui.credentialProblems.get(0).headline().contains("rejected"));
    }

    @Test
    void missingKeysForAppliedModePromptWithoutProbing() {
        FakeUi ui = new FakeUi();
        ui.apiKey = "";
        ui.apiSecret = "";
        StartupCredentialCoordinator coordinator = coordinator(ui, ConnectionCheck.accepted());

        coordinator.verifySavedCredentials();

        assertFalse(ui.appliedAttempt);
        assertTrue(ui.settingsOpened);
        assertEquals(1, ui.credentialProblems.size());
        assertTrue(ui.credentialProblems.get(0).headline().contains("required"));
    }

    @Test
    void bothModesAreReportedInTheStartupLog() {
        FakeUi ui = new FakeUi();
        StartupCredentialCoordinator coordinator = coordinator(ui, ConnectionCheck.accepted());

        coordinator.verifySavedCredentials();

        assertTrue(ui.logs.stream().anyMatch(line ->
                line.contains("Paper keys: valid") && line.contains("Live keys: valid")));
    }

    private static StartupCredentialCoordinator coordinator(FakeUi ui, ConnectionCheck check) {
        StartupCredentialValidator validator = new StartupCredentialValidator(
                (mode, apiKey, apiSecret) -> new TradingRuntimeSupport.ConnectionAttemptResult(
                        check.connected(), null, check.detail(), false, false, check),
                1,
                0L,
                millis -> { }
        );
        return new StartupCredentialCoordinator(ui, validator);
    }

    private record CredentialProblem(String title, String headline, String summary) {
    }

    /** Runs "background" and "UI" work inline so each test stays deterministic. */
    private static final class FakeUi implements StartupCredentialCoordinator.Ui {
        private final List<String> logs = new ArrayList<>();
        private final List<CredentialProblem> credentialProblems = new ArrayList<>();
        private String apiKey = "key";
        private String apiSecret = "secret";
        private boolean settingsOpened;
        private boolean appliedAttempt;

        @Override public ApplicationMode appliedApplicationMode() { return ApplicationMode.PAPER; }
        @Override public BrokerType appliedBrokerType() { return BrokerType.ALPACA; }
        @Override public String savedApiKey(ApplicationMode mode) { return apiKey; }
        @Override public String savedApiSecret(ApplicationMode mode) { return apiSecret; }
        @Override public boolean liveTradingEnabled() { return true; }
        @Override public void log(String message) { logs.add(message); }
        @Override public void setVerifyingStatus(String message) { }

        @Override
        public SettingsDialog.ConnectionResult applyConnectionAttempt(
                TradingRuntimeSupport.ConnectionAttemptResult attempt,
                BrokerType brokerType,
                ApplicationMode mode,
                String apiKey,
                String apiSecret
        ) {
            appliedAttempt = true;
            return new SettingsDialog.ConnectionResult(attempt.connected(), attempt.message());
        }

        @Override
        public void showCredentialProblem(String title, String headline, String summary) {
            credentialProblems.add(new CredentialProblem(title, headline, summary));
        }

        @Override public void openSettings() { settingsOpened = true; }
        @Override public void runInBackground(Runnable task) { task.run(); }
        @Override public void runOnUiThread(Runnable task) { task.run(); }
    }
}
