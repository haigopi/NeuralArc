package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduledScanRetryPolicyTest {
    @Test
    void retriesWhenAtLeastHalfTheListHadNoDataForToday() {
        // This morning: 23 of 30 discovered symbols had no premarket print on the IEX feed.
        assertTrue(ScheduledScanRetryPolicy.mostlyUnmeasurable(23, 30));
        assertTrue(ScheduledScanRetryPolicy.mostlyUnmeasurable(15, 30));
    }

    @Test
    void trustsTheResultWhenMostSymbolsWereMeasured() {
        assertFalse(ScheduledScanRetryPolicy.mostlyUnmeasurable(3, 30));
        assertFalse(ScheduledScanRetryPolicy.mostlyUnmeasurable(0, 0));
    }
}
