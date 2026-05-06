package com.neuralarc.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketHoursServiceTest {
    private final MarketHoursService service = new MarketHoursService();

    @Test
    void extendedHoursEnabledAllowsPreMarketSession() {
        Instant instant = ZonedDateTime.of(2026, 4, 29, 8, 0, 0, 0, ZoneId.of("America/New_York")).toInstant();
        assertTrue(service.isTradingSessionOpen(instant, true));
    }

    @Test
    void extendedHoursDisabledBlocksPreMarketSession() {
        Instant instant = ZonedDateTime.of(2026, 4, 29, 8, 0, 0, 0, ZoneId.of("America/New_York")).toInstant();
        assertFalse(service.isTradingSessionOpen(instant, false));
    }

    @Test
    void eveningSessionCanBeExtendedOpenWhileRegularMarketIsClosed() {
        Instant instant = ZonedDateTime.of(2026, 4, 29, 19, 0, 0, 0, ZoneId.of("America/New_York")).toInstant();
        assertTrue(service.isTradingSessionOpen(instant, true));
        assertFalse(service.isRegularMarketHours(instant));
    }

    @Test
    void weekendIsClosed() {
        Instant instant = ZonedDateTime.of(2026, 5, 2, 11, 0, 0, 0, ZoneId.of("America/New_York")).toInstant();
        assertFalse(service.isTradingSessionOpen(instant, true));
        assertFalse(service.isTradingSessionOpen(instant, false));
    }

    @Test
    void holidayIsClosed() {
        Instant instant = ZonedDateTime.of(2026, 12, 25, 11, 0, 0, 0, ZoneId.of("America/New_York")).toInstant();
        assertFalse(service.isTradingSessionOpen(instant, true));
        assertFalse(service.isTradingSessionOpen(instant, false));
    }
}
