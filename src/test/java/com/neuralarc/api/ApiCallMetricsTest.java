package com.neuralarc.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiCallMetricsTest {
    @Test
    void recordsTotalsSuccessesAndFailures() {
        ApiCallMetrics.reset();
        ApiCallMetrics.record(true);
        ApiCallMetrics.record(true);
        ApiCallMetrics.record(true);
        ApiCallMetrics.record(false);

        ApiCallMetrics.Snapshot snapshot = ApiCallMetrics.snapshot();
        assertEquals(4, snapshot.total());
        assertEquals(1, snapshot.failed());
        assertEquals(3, snapshot.succeeded());
        assertEquals(75.0, snapshot.successRatePercent(), 0.001);
    }

    @Test
    void resetClearsCountersAndRateDefaultsToFullyHealthy() {
        ApiCallMetrics.record(false);
        ApiCallMetrics.reset();

        ApiCallMetrics.Snapshot snapshot = ApiCallMetrics.snapshot();
        assertEquals(0, snapshot.total());
        assertEquals(0, snapshot.failed());
        assertEquals(0, snapshot.succeeded());
        assertEquals(100.0, snapshot.successRatePercent(), 0.001);
    }
}
