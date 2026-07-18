package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventLogSeverityTest {
    @Test
    void treatsBusinessInfoAboutRecoveredFailedStatusAsNormal() {
        assertFalse(EventLogSeverity.isFailure(
                "[Jul 8th - 10:15 AM] [RESTORE] Recovered stale failed status from broker exposure for: AAPL"
        ));
        assertEquals(EventLogSeverity.Tone.SUCCESS, EventLogSeverity.tone(
                "[Jul 8th - 10:15 AM] [RESTORE] Recovered stale failed status from broker exposure for: AAPL"
        ));
    }

    @Test
    void treatsZeroFailureSummariesAsNormal() {
        assertFalse(EventLogSeverity.isFailure(
                "[Jul 8th - 10:15 AM] [Portfolio Actions] completed. Succeeded=4, failed=0."
        ));
        assertEquals(EventLogSeverity.Tone.SUCCESS, EventLogSeverity.tone(
                "[Jul 8th - 10:15 AM] [Portfolio Actions] completed. Succeeded=4, failed=0."
        ));
    }

    @Test
    void treatsActualFailuresAsFailure() {
        assertTrue(EventLogSeverity.isFailure(
                "[Jul 8th - 10:15 AM] [AAPL] Failed to place pending base buy: broker rejected order"
        ));
        assertEquals(EventLogSeverity.Tone.FAILURE, EventLogSeverity.tone(
                "[Jul 8th - 10:15 AM] [AAPL] Failed to place pending base buy: broker rejected order"
        ));
    }

    @Test
    void treatsActualRejectedOrdersAsFailure() {
        assertTrue(EventLogSeverity.isFailure(
                "[Jul 8th - 10:15 AM] [AAPL] Alpaca order rejected: insufficient buying power"
        ));
    }

    @Test
    void detectsProcessingLogs() {
        assertEquals(EventLogSeverity.Tone.PROCESSING, EventLogSeverity.tone(
                "[Jul 8th - 10:15 AM] [Place Pending Base Buys] evaluating AAPL for pending base-buy placement."
        ));
    }

    @Test
    void detectsWarningLogs() {
        assertEquals(EventLogSeverity.Tone.WARNING, EventLogSeverity.tone(
                "[Jul 8th - 10:15 AM] [Portfolio Actions] skipped MSFT: broker client is unavailable."
        ));
    }
}
