package com.neuralarc.service;

import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.SwingSchedule;
import com.neuralarc.swing.SwingConfig;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwingScheduleServiceTest {
    private static final ZoneId ET = ZoneId.of("America/New_York");
    private static final LocalDate TRADING_DAY = LocalDate.of(2026, 6, 15);   // Monday, not a holiday
    private static final LocalDate WEEKEND = LocalDate.of(2026, 6, 13);       // Saturday

    private final AtomicInteger fires = new AtomicInteger();

    private SwingScheduleService service(boolean enabled) {
        SwingScheduleService svc = new SwingScheduleService(
                new MarketHoursService(), Clock.systemUTC(), schedule -> fires.incrementAndGet(), null);
        svc.setSchedule(new SwingSchedule("s1", enabled, LocalTime.of(9, 45),
                LocalTime.of(9, 45), LocalTime.of(15, 45), false, "w1", SwingConfig.defaults(StrategyMode.PAPER)));
        return svc;
    }

    private static Instant et(LocalDate date, int hour, int minute) {
        return ZonedDateTime.of(date, LocalTime.of(hour, minute), ET).toInstant();
    }

    @Test
    void firesOnceInsideWindowOnTradingDay() {
        SwingScheduleService svc = service(true);

        assertTrue(svc.evaluate(et(TRADING_DAY, 9, 50)));
        assertEquals(1, fires.get());
        assertFalse(svc.evaluate(et(TRADING_DAY, 11, 0)));
        assertEquals(1, fires.get());
    }

    @Test
    void doesNotFireBeforeScanTimeOrAfterWindowEnd() {
        SwingScheduleService svc = service(true);

        assertFalse(svc.evaluate(et(TRADING_DAY, 9, 30)));
        assertFalse(svc.evaluate(et(TRADING_DAY, 15, 45)));
        assertFalse(svc.evaluate(et(TRADING_DAY, 16, 0)));
        assertEquals(0, fires.get());
    }

    @Test
    void neverFiresOnClosedDays() {
        SwingScheduleService svc = service(true);

        assertFalse(svc.evaluate(et(WEEKEND, 9, 50)));
        assertEquals(0, fires.get());
    }

    @Test
    void scansOnlyOncePerSession() {
        SwingScheduleService svc = service(true);

        assertTrue(svc.evaluate(et(TRADING_DAY, 9, 50)));   // initial
        assertFalse(svc.evaluate(et(TRADING_DAY, 10, 30))); // same day — no intraday re-scan
        assertFalse(svc.evaluate(et(TRADING_DAY, 14, 0)));
        assertEquals(1, fires.get());
    }

    @Test
    void disabledScheduleNeverFires() {
        SwingScheduleService svc = service(false);

        assertFalse(svc.evaluate(et(TRADING_DAY, 9, 50)));
        assertEquals(0, fires.get());
    }

    @Test
    void noScheduleNeverFires() {
        SwingScheduleService svc = new SwingScheduleService(
                new MarketHoursService(), Clock.systemUTC(), schedule -> fires.incrementAndGet(), null);

        assertFalse(svc.evaluate(et(TRADING_DAY, 9, 50)));
        assertEquals(0, fires.get());
    }

    @Test
    void firesAgainTheNextTradingDay() {
        SwingScheduleService svc = service(true);

        assertTrue(svc.evaluate(et(TRADING_DAY, 9, 50)));
        assertTrue(svc.evaluate(et(TRADING_DAY.plusDays(1), 9, 50))); // Tuesday
        assertEquals(2, fires.get());
    }
}
