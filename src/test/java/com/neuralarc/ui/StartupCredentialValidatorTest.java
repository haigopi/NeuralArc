package com.neuralarc.ui;

import com.neuralarc.api.ConnectionCheck;
import com.neuralarc.model.ApplicationMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartupCredentialValidatorTest {
    @Test
    void transientFailureIsRetriedAndNeverReportedAsCredentialProblem() {
        RecordingProbe probe = new RecordingProbe(
                attempt(ConnectionCheck.unreachable("timeout")),
                attempt(ConnectionCheck.unreachable("timeout")),
                attempt(ConnectionCheck.accepted())
        );
        StartupCredentialValidator validator = new StartupCredentialValidator(probe, 3, 0L, millis -> { });

        StartupCredentialValidator.Report report = validator.validate(ApplicationMode.PAPER, List.of(
                configured(ApplicationMode.PAPER),
                configured(ApplicationMode.LIVE)
        ));

        assertEquals(3, probe.callsFor(ApplicationMode.PAPER));
        assertTrue(report.connected());
        assertFalse(report.requiresOperatorAction());
        assertTrue(report.appliedStatus().valid());
    }

    @Test
    void transientFailureThatNeverRecoversDoesNotAskTheOperatorForKeys() {
        RecordingProbe probe = new RecordingProbe(attempt(ConnectionCheck.unreachable("timeout")));
        StartupCredentialValidator validator = new StartupCredentialValidator(probe, 2, 0L, millis -> { });

        StartupCredentialValidator.Report report = validator.validate(ApplicationMode.PAPER, List.of(configured(ApplicationMode.PAPER)));

        assertEquals(2, probe.callsFor(ApplicationMode.PAPER));
        assertFalse(report.connected());
        assertFalse(report.requiresOperatorAction());
        assertEquals(ConnectionCheck.Status.UNREACHABLE, report.appliedStatus().status());
    }

    @Test
    void rejectedKeysAreNotRetriedAndRequireOperatorAction() {
        RecordingProbe probe = new RecordingProbe(attempt(ConnectionCheck.invalidCredentials(401)));
        StartupCredentialValidator validator = new StartupCredentialValidator(probe, 3, 0L, millis -> { });

        StartupCredentialValidator.Report report = validator.validate(ApplicationMode.PAPER, List.of(configured(ApplicationMode.PAPER)));

        assertEquals(1, probe.callsFor(ApplicationMode.PAPER));
        assertTrue(report.requiresOperatorAction());
        assertEquals(ConnectionCheck.Status.INVALID_CREDENTIALS, report.appliedStatus().status());
    }

    @Test
    void unconfiguredModeIsReportedWithoutProbingTheBroker() {
        RecordingProbe probe = new RecordingProbe(attempt(ConnectionCheck.accepted()));
        StartupCredentialValidator validator = new StartupCredentialValidator(probe, 3, 0L, millis -> { });

        StartupCredentialValidator.Report report = validator.validate(ApplicationMode.PAPER, List.of(
                configured(ApplicationMode.PAPER),
                new StartupCredentialValidator.ModeCredentials(ApplicationMode.LIVE, "", "", true)
        ));

        assertEquals(0, probe.callsFor(ApplicationMode.LIVE));
        assertEquals("Paper keys: valid | Live keys: not configured", report.summary());
    }

    @Test
    void nonAppliedModeIsProbedOnceSoBothModesAreReported() {
        RecordingProbe probe = new RecordingProbe(attempt(ConnectionCheck.invalidCredentials(403)));
        StartupCredentialValidator validator = new StartupCredentialValidator(probe, 3, 0L, millis -> { });

        StartupCredentialValidator.Report report = validator.validate(ApplicationMode.LIVE, List.of(
                configured(ApplicationMode.PAPER),
                configured(ApplicationMode.LIVE)
        ));

        assertEquals(1, probe.callsFor(ApplicationMode.PAPER));
        assertEquals("Paper keys: rejected by broker | Live keys: rejected by broker", report.summary());
        assertEquals(ApplicationMode.LIVE, report.appliedMode());
    }

    @Test
    void disabledLiveModeIsNotProbed() {
        RecordingProbe probe = new RecordingProbe(attempt(ConnectionCheck.accepted()));
        StartupCredentialValidator validator = new StartupCredentialValidator(probe, 3, 0L, millis -> { });

        StartupCredentialValidator.Report report = validator.validate(ApplicationMode.PAPER, List.of(
                configured(ApplicationMode.PAPER),
                new StartupCredentialValidator.ModeCredentials(ApplicationMode.LIVE, "live-key", "live-secret", false)
        ));

        assertEquals(0, probe.callsFor(ApplicationMode.LIVE));
        assertFalse(report.modeStatuses().get(ApplicationMode.LIVE).probed());
        assertTrue(report.connected());
    }

    private static StartupCredentialValidator.ModeCredentials configured(ApplicationMode mode) {
        return new StartupCredentialValidator.ModeCredentials(mode, mode.name() + "-key", mode.name() + "-secret", true);
    }

    private static TradingRuntimeSupport.ConnectionAttemptResult attempt(ConnectionCheck check) {
        return new TradingRuntimeSupport.ConnectionAttemptResult(
                check.connected(), null, check.detail(), false, false, check);
    }

    /** Replays scripted outcomes in order, repeating the last one, and counts calls per mode. */
    private static final class RecordingProbe implements StartupCredentialValidator.ModeProbe {
        private final List<TradingRuntimeSupport.ConnectionAttemptResult> outcomes;
        private final List<ApplicationMode> calls = new ArrayList<>();

        private RecordingProbe(TradingRuntimeSupport.ConnectionAttemptResult... outcomes) {
            this.outcomes = List.of(outcomes);
        }

        @Override
        public TradingRuntimeSupport.ConnectionAttemptResult probe(ApplicationMode mode, String apiKey, String apiSecret) {
            int index = Math.min(callsFor(mode), outcomes.size() - 1);
            calls.add(mode);
            return outcomes.get(index);
        }

        int callsFor(ApplicationMode mode) {
            return (int) calls.stream().filter(called -> called == mode).count();
        }
    }
}
