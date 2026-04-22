package com.neuralarc.analytics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalyticsEventTest {
    @Test
    void telemetryPayloadContainsExpectedFields() {
        AnalyticsEvent event = new AnalyticsEvent("ORDER_FILLED")
                .put("userId", "abc123")
                .put("symbol", "NEO")
                .put("orderQuantity", 10)
                .put("orderPrice", "8.00");

        String json = event.toJson();
        assertTrue(json.contains("ORDER_FILLED"));
        assertTrue(json.contains("userId"));
        assertTrue(json.contains("NEO"));
        assertTrue(json.contains("orderQuantity"));
    }
}
