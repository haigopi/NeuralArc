package com.neuralarc.service;

import com.neuralarc.model.RecommendationType;
import com.neuralarc.model.SmartPicksSchedule;
import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartPicksScheduleServiceTest {
    private static final ZoneId ET = ZoneId.of("America/New_York");
    private static final LocalDate FRIDAY = LocalDate.of(2026, 9, 25);
    private static final LocalDate THURSDAY = FRIDAY.minusDays(1);
    private static final LocalDate THANKSGIVING = LocalDate.of(2026, 11, 26);

    private final AtomicInteger fires = new AtomicInteger();

    @Test
    void weekendReboundFiresOnFridayAfterItsScanTimeOnce() {
        SmartPicksScheduleService service = service(EnumSet.of(DayOfWeek.FRIDAY), LocalTime.of(15, 30));

        assertFalse(service.evaluate(et(FRIDAY, 15, 29)), "not before its time");
        assertTrue(service.evaluate(et(FRIDAY, 15, 30)));
        assertFalse(service.evaluate(et(FRIDAY, 15, 45)), "once a day");
        assertEquals(1, fires.get());
    }

    @Test
    void itSkipsDaysThatAreNotChosenAndStopsAtTheClose() {
        SmartPicksScheduleService service = service(EnumSet.of(DayOfWeek.FRIDAY), LocalTime.of(15, 30));

        assertFalse(service.evaluate(et(THURSDAY, 15, 30)), "Weekend Rebound does not run on Thursdays");
        assertFalse(service.evaluate(et(FRIDAY, 16, 0)), "no scan after the regular session");
        assertEquals(0, fires.get());
    }

    @Test
    void aLateStartStillRunsThatDaysScan() {
        SmartPicksScheduleService service = service(EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), LocalTime.of(10, 0));

        assertTrue(service.evaluate(et(THURSDAY, 13, 12)), "the app opened after 10:00 ET");
    }

    @Test
    void marketHolidaysAreSkipped() {
        SmartPicksScheduleService service = service(EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), LocalTime.of(10, 0));

        assertFalse(service.evaluate(et(THANKSGIVING, 10, 30)));
    }

    @Test
    void aDeferredScanRunsAgainOnALaterTick() {
        SmartPicksScheduleService service = service(EnumSet.of(DayOfWeek.FRIDAY), LocalTime.of(15, 30));
        assertTrue(service.evaluate(et(FRIDAY, 15, 30)));

        service.deferToday();

        assertTrue(service.evaluate(et(FRIDAY, 15, 31)), "a scan that could not start is retried, not lost");
        assertEquals(2, fires.get());
    }

    @Test
    void aDisabledOrClearedScheduleNeverFires() {
        SmartPicksScheduleService service = service(EnumSet.of(DayOfWeek.FRIDAY), LocalTime.of(15, 30));
        service.clearSchedule();

        assertFalse(service.evaluate(et(FRIDAY, 15, 30)));
    }

    private SmartPicksScheduleService service(EnumSet<DayOfWeek> days, LocalTime time) {
        SmartPicksScheduleService service = new SmartPicksScheduleService(new MarketHoursService(), Clock.systemUTC(),
                schedule -> fires.incrementAndGet(), null);
        service.setSchedule(new SmartPicksSchedule("s1", true, "w1", "REBOUND", time, days, false, 1,
                RecommendationType.SHORT_TERM, StrategyMode.PAPER));
        return service;
    }

    private static Instant et(LocalDate date, int hour, int minute) {
        return ZonedDateTime.of(date, LocalTime.of(hour, minute), ET).toInstant();
    }
}
