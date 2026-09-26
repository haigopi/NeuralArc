package com.neuralarc.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionHighCacheTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 25);
    private final SessionHighCache cache = new SessionHighCache();

    @Test
    void theHighestPriceSeenTodayWins() {
        cache.record("TSLA", TODAY, new BigDecimal("372.75"));
        cache.record("tsla", TODAY, new BigDecimal("386.67"));
        cache.record("TSLA", TODAY, new BigDecimal("380.00"));

        assertEquals(new BigDecimal("386.67"), cache.highFor("TSLA", TODAY),
                "a peak between polls is exactly what the engine cannot see for itself");
    }

    @Test
    void yesterdaysPeakNeverArmsTodaysHold() {
        cache.record("TSLA", TODAY.minusDays(1), new BigDecimal("500.00"));

        assertEquals(BigDecimal.ZERO, cache.highFor("TSLA", TODAY));
    }

    @Test
    void aNewDayReplacesTheOldHighRatherThanKeepingIt() {
        cache.record("TSLA", TODAY.minusDays(1), new BigDecimal("500.00"));
        cache.record("TSLA", TODAY, new BigDecimal("386.67"));

        assertEquals(new BigDecimal("386.67"), cache.highFor("TSLA", TODAY));
    }

    @Test
    void unknownSymbolsAndJunkInputsReadAsUnknown() {
        cache.record(null, TODAY, new BigDecimal("10"));
        cache.record("NIO", TODAY, BigDecimal.ZERO);

        assertEquals(BigDecimal.ZERO, cache.highFor("NIO", TODAY));
        assertEquals(BigDecimal.ZERO, cache.highFor("MSFT", TODAY));
    }
}
