package com.neuralarc.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionCheckTest {
    @Test
    void okStatusIsConnected() {
        ConnectionCheck check = ConnectionCheck.forHttpStatus(200);

        assertEquals(ConnectionCheck.Status.CONNECTED, check.status());
        assertTrue(check.connected());
        assertFalse(check.credentialProblem());
        assertFalse(check.retryable());
    }

    @Test
    void unauthorizedAndForbiddenMeanRejectedCredentials() {
        for (int httpStatus : new int[]{401, 403}) {
            ConnectionCheck check = ConnectionCheck.forHttpStatus(httpStatus);

            assertEquals(ConnectionCheck.Status.INVALID_CREDENTIALS, check.status(), "status for HTTP " + httpStatus);
            assertTrue(check.credentialProblem());
            assertFalse(check.retryable());
        }
    }

    @Test
    void otherHttpStatusesAreRetryableWithoutBlamingCredentials() {
        for (int httpStatus : new int[]{429, 500, 503}) {
            ConnectionCheck check = ConnectionCheck.forHttpStatus(httpStatus);

            assertEquals(ConnectionCheck.Status.BROKER_ERROR, check.status(), "status for HTTP " + httpStatus);
            assertFalse(check.credentialProblem());
            assertTrue(check.retryable());
        }
    }

    @Test
    void transportFailureIsUnreachableNotInvalidCredentials() {
        ConnectionCheck check = ConnectionCheck.unreachable("HttpConnectTimeoutException");

        assertEquals(ConnectionCheck.Status.UNREACHABLE, check.status());
        assertFalse(check.credentialProblem());
        assertTrue(check.retryable());
        assertEquals("HttpConnectTimeoutException", check.detail());
    }

    @Test
    void blankCredentialsAreMissingNotRejected() {
        ConnectionCheck check = ConnectionCheck.missingCredentials();

        assertEquals(ConnectionCheck.Status.MISSING_CREDENTIALS, check.status());
        assertTrue(check.credentialProblem());
        assertFalse(check.retryable());
    }
}
