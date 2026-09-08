package com.neuralarc.ui;

import com.neuralarc.api.ConnectionCheck;
import com.neuralarc.model.ApplicationMode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Startup credential check that runs once the app has finished loading, off the Swing EDT.
 *
 * <p>It answers two questions the old single boolean probe could not: are the saved keys for each
 * mode (paper and live) actually accepted by the broker, and — when the applied mode fails — is that
 * because the keys are wrong or because the broker was momentarily unreachable. A cold JVM's first
 * HTTPS call can time out even with perfectly valid keys, so a transient failure is retried here and
 * never surfaces as a "credentials missing" prompt.
 */
final class StartupCredentialValidator {
    static final int DEFAULT_MAX_ATTEMPTS = 3;
    static final long DEFAULT_RETRY_DELAY_MILLIS = 2_000L;

    /** Runs one broker probe for a mode. Implemented by the frame with {@code TradingRuntimeSupport}. */
    @FunctionalInterface
    interface ModeProbe {
        TradingRuntimeSupport.ConnectionAttemptResult probe(ApplicationMode mode, String apiKey, String apiSecret);
    }

    /** Injected so tests do not have to wait out the retry delay. */
    @FunctionalInterface
    interface Pause {
        void sleep(long millis) throws InterruptedException;
    }

    private final ModeProbe probe;
    private final int maxAttempts;
    private final long retryDelayMillis;
    private final Pause pause;

    StartupCredentialValidator(ModeProbe probe) {
        this(probe, DEFAULT_MAX_ATTEMPTS, DEFAULT_RETRY_DELAY_MILLIS, Thread::sleep);
    }

    StartupCredentialValidator(ModeProbe probe, int maxAttempts, long retryDelayMillis, Pause pause) {
        this.probe = probe;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryDelayMillis = Math.max(0L, retryDelayMillis);
        this.pause = pause;
    }

    /** Saved credentials for one mode, plus whether the app is allowed to use that mode at all. */
    record ModeCredentials(ApplicationMode mode, String apiKey, String apiSecret, boolean supported) {
        boolean configured() {
            return apiKey != null && !apiKey.isBlank() && apiSecret != null && !apiSecret.isBlank();
        }
    }

    /** What the check concluded about one mode's saved keys. */
    record ModeStatus(ApplicationMode mode, ConnectionCheck.Status status, String detail, boolean probed, int attempts) {
        String label() {
            return switch (status) {
                case CONNECTED -> "valid";
                case MISSING_CREDENTIALS -> "not configured";
                case INVALID_CREDENTIALS -> "rejected by broker";
                case UNREACHABLE -> "not verified (broker unreachable)";
                case BROKER_ERROR -> "not verified";
            };
        }

        boolean valid() {
            return status == ConnectionCheck.Status.CONNECTED;
        }

        boolean credentialProblem() {
            return status == ConnectionCheck.Status.MISSING_CREDENTIALS
                    || status == ConnectionCheck.Status.INVALID_CREDENTIALS;
        }
    }

    /** Per-mode verdicts plus the attempt for the applied mode, which the caller may adopt as the live connection. */
    record Report(
            ApplicationMode appliedMode,
            Map<ApplicationMode, ModeStatus> modeStatuses,
            TradingRuntimeSupport.ConnectionAttemptResult appliedAttempt
    ) {
        ModeStatus appliedStatus() {
            return modeStatuses.get(appliedMode);
        }

        boolean connected() {
            return appliedAttempt != null && appliedAttempt.connected();
        }

        /** True only when the operator has to fix something: blank keys, rejected keys, or live mode disabled. */
        boolean requiresOperatorAction() {
            if (connected()) {
                return false;
            }
            if (appliedAttempt != null && (appliedAttempt.brokerMissing() || appliedAttempt.liveDisabled())) {
                return true;
            }
            ModeStatus status = appliedStatus();
            return status != null && status.credentialProblem();
        }

        /** e.g. {@code "Paper keys: valid | Live keys: not configured"}. */
        String summary() {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<ApplicationMode, ModeStatus> entry : modeStatuses.entrySet()) {
                parts.add(modeLabel(entry.getKey()) + " keys: " + entry.getValue().label());
            }
            return String.join(" | ", parts);
        }

        static String modeLabel(ApplicationMode mode) {
            return mode == ApplicationMode.LIVE ? "Live" : "Paper";
        }
    }

    /**
     * Probes every supplied mode. The applied mode is retried while the failure is transient; other
     * modes are probed once, only to report whether their saved keys are usable.
     */
    Report validate(ApplicationMode appliedMode, List<ModeCredentials> credentials) {
        ApplicationMode safeAppliedMode = appliedMode == null ? ApplicationMode.PAPER : appliedMode;
        Map<ApplicationMode, ModeStatus> statuses = new LinkedHashMap<>();
        TradingRuntimeSupport.ConnectionAttemptResult appliedAttempt = null;

        for (ModeCredentials modeCredentials : credentials) {
            if (modeCredentials == null) {
                continue;
            }
            ApplicationMode mode = modeCredentials.mode();
            boolean applied = mode == safeAppliedMode;
            if (!modeCredentials.supported()) {
                statuses.put(mode, new ModeStatus(mode, ConnectionCheck.Status.BROKER_ERROR,
                        "Mode is disabled in application configuration.", false, 0));
                continue;
            }
            if (!modeCredentials.configured()) {
                statuses.put(mode, new ModeStatus(mode, ConnectionCheck.Status.MISSING_CREDENTIALS,
                        "API key and secret are not saved for this mode.", false, 0));
                continue;
            }
            ProbeOutcome outcome = probeMode(modeCredentials, applied ? maxAttempts : 1);
            statuses.put(mode, new ModeStatus(mode, outcome.check().status(), outcome.check().detail(), true, outcome.attempts()));
            if (applied) {
                appliedAttempt = outcome.attempt();
            }
        }

        if (appliedAttempt == null && !statuses.containsKey(safeAppliedMode)) {
            statuses.put(safeAppliedMode, new ModeStatus(safeAppliedMode, ConnectionCheck.Status.MISSING_CREDENTIALS,
                    "API key and secret are not saved for this mode.", false, 0));
        }
        return new Report(safeAppliedMode, statuses, appliedAttempt);
    }

    private ProbeOutcome probeMode(ModeCredentials credentials, int attemptBudget) {
        TradingRuntimeSupport.ConnectionAttemptResult attempt = null;
        ConnectionCheck check = ConnectionCheck.unreachable("Broker was not reachable.");
        for (int attemptNumber = 1; attemptNumber <= attemptBudget; attemptNumber++) {
            attempt = probe.probe(credentials.mode(), credentials.apiKey(), credentials.apiSecret());
            check = attempt == null ? ConnectionCheck.unreachable("No broker response.") : attempt.check();
            if (!check.retryable() || attemptNumber == attemptBudget) {
                return new ProbeOutcome(attempt, check, attemptNumber);
            }
            if (!sleepBetweenAttempts()) {
                return new ProbeOutcome(attempt, check, attemptNumber);
            }
        }
        return new ProbeOutcome(attempt, check, attemptBudget);
    }

    private boolean sleepBetweenAttempts() {
        if (retryDelayMillis <= 0L) {
            return true;
        }
        try {
            pause.sleep(retryDelayMillis);
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private record ProbeOutcome(TradingRuntimeSupport.ConnectionAttemptResult attempt, ConnectionCheck check, int attempts) {
    }
}
