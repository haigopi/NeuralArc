package com.neuralarc.api;

/**
 * Outcome of a broker credential/connectivity probe.
 *
 * <p>A plain {@code boolean} cannot tell "the broker rejected these keys" apart from "the broker was
 * not reachable right now". The app needs that distinction: rejected keys are an operator problem
 * worth a dialog, while an unreachable broker is transient and should only be retried.
 */
public record ConnectionCheck(Status status, String detail) {
    public enum Status {
        /** Broker accepted the credentials. */
        CONNECTED,
        /** No API key/secret was supplied for the mode. */
        MISSING_CREDENTIALS,
        /** Broker answered, but rejected the credentials (HTTP 401/403). */
        INVALID_CREDENTIALS,
        /** Broker could not be reached (DNS, TLS, timeout, offline). */
        UNREACHABLE,
        /** Broker answered with an unexpected status; credentials are neither proven nor rejected. */
        BROKER_ERROR
    }

    public ConnectionCheck {
        status = status == null ? Status.BROKER_ERROR : status;
        detail = detail == null ? "" : detail;
    }

    public static ConnectionCheck accepted() {
        return new ConnectionCheck(Status.CONNECTED, "Broker accepted the credentials.");
    }

    public static ConnectionCheck missingCredentials() {
        return new ConnectionCheck(Status.MISSING_CREDENTIALS, "API key and secret are not configured.");
    }

    public static ConnectionCheck invalidCredentials(int httpStatus) {
        return new ConnectionCheck(Status.INVALID_CREDENTIALS, "Broker rejected the credentials (HTTP " + httpStatus + ").");
    }

    public static ConnectionCheck unreachable(String detail) {
        return new ConnectionCheck(Status.UNREACHABLE, detail == null || detail.isBlank()
                ? "Broker was not reachable."
                : detail);
    }

    public static ConnectionCheck brokerError(int httpStatus) {
        return new ConnectionCheck(Status.BROKER_ERROR, "Broker returned HTTP " + httpStatus + ".");
    }

    /** Maps a trading-endpoint HTTP status onto a check outcome. */
    public static ConnectionCheck forHttpStatus(int httpStatus) {
        if (httpStatus == 200) {
            return accepted();
        }
        if (httpStatus == 401 || httpStatus == 403) {
            return invalidCredentials(httpStatus);
        }
        return brokerError(httpStatus);
    }

    public boolean connected() {
        return status == Status.CONNECTED;
    }

    /** True when the credentials themselves are the problem, so asking the operator to fix them is useful. */
    public boolean credentialProblem() {
        return status == Status.MISSING_CREDENTIALS || status == Status.INVALID_CREDENTIALS;
    }

    /** True when the outcome says nothing about the credentials and retrying may still succeed. */
    public boolean retryable() {
        return status == Status.UNREACHABLE || status == Status.BROKER_ERROR;
    }
}
