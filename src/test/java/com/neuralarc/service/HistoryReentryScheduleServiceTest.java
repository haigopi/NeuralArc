package com.neuralarc.service;

import com.neuralarc.model.HistoryReentrySchedule;
import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryReentryScheduleServiceTest {
    private final List<HistoryReentrySchedule> fired = new ArrayList<>();

    /** Monday 2026-09-21, 10:30 ET. */
    private static final Instant MONDAY_1030_ET = Instant.parse("2026-09-21T14:30:00Z");

    @Test
    void firesOnceOnItsDayAfterItsTime() {
        HistoryReentryScheduleService service = service(schedule(DayOfWeek.MONDAY, LocalTime.of(10, 0), null));

        assertFalse(service.evaluate(Instant.parse("2026-09-21T13:00:00Z")), "09:00 ET is before the scheduled time");
        assertTrue(service.evaluate(MONDAY_1030_ET));
        assertFalse(service.evaluate(Instant.parse("2026-09-21T15:00:00Z")), "a run already happened today");
        assertEquals(1, fired.size());
    }

    @Test
    void doesNotFireOnAnotherWeekdayOrAfterTheClose() {
        HistoryReentryScheduleService service = service(schedule(DayOfWeek.MONDAY, LocalTime.of(10, 0), null));

        assertFalse(service.evaluate(Instant.parse("2026-09-22T14:30:00Z")), "Tuesday is not its day");
        assertFalse(service.evaluate(Instant.parse("2026-09-21T20:30:00Z")), "16:30 ET is after the close");
        assertTrue(fired.isEmpty());
    }

    @Test
    void aFortnightlyRunWaitsOutTheWeekInBetween() {
        HistoryReentrySchedule lastWeek = new HistoryReentrySchedule(null, true, StrategyMode.PAPER,
                DayOfWeek.MONDAY, LocalTime.of(10, 0), HistoryReentrySchedule.Cadence.BIWEEKLY,
                HistoryReentrySchedule.Group.GAINS, 10, LocalDate.of(2026, 9, 14));

        assertFalse(service(lastWeek).evaluate(MONDAY_1030_ET), "only 7 days since the last run");
        assertTrue(service(lastWeek.withLastRunDate(LocalDate.of(2026, 9, 7))).evaluate(MONDAY_1030_ET),
                "14 days on, it comes round again");
    }

    @Test
    void aWeeklyRunFiresTheFollowingWeek() {
        HistoryReentrySchedule lastWeek = schedule(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalDate.of(2026, 9, 14));

        assertTrue(service(lastWeek).evaluate(MONDAY_1030_ET));
    }

    @Test
    void aScheduleThatIsOffNeverFires() {
        HistoryReentrySchedule off = new HistoryReentrySchedule(null, false, StrategyMode.PAPER, DayOfWeek.MONDAY,
                LocalTime.of(10, 0), HistoryReentrySchedule.Cadence.WEEKLY, HistoryReentrySchedule.Group.GAINS, 10, null);

        assertFalse(service(off).evaluate(MONDAY_1030_ET));
    }

    @Test
    void marketHolidaysAreSkipped() {
        // 2026-07-03 is the observed Independence Day holiday, a Friday.
        HistoryReentryScheduleService service = service(schedule(DayOfWeek.FRIDAY, LocalTime.of(10, 0), null));

        assertFalse(service.evaluate(Instant.parse("2026-07-03T14:30:00Z")));
    }

    @Test
    void deferringLetsALaterTickRunTheSameDay() {
        HistoryReentryScheduleService service = service(schedule(DayOfWeek.MONDAY, LocalTime.of(10, 0), null));
        assertTrue(service.evaluate(MONDAY_1030_ET));

        service.deferToday();

        assertTrue(service.evaluate(Instant.parse("2026-09-21T15:00:00Z")),
                "a run that could not start must not cost the operator the whole week");
    }

    private HistoryReentryScheduleService service(HistoryReentrySchedule schedule) {
        fired.clear();
        HistoryReentryScheduleService service = new HistoryReentryScheduleService(
                new MarketHoursService(), Clock.systemUTC(), fired::add, message -> { });
        service.setSchedule(schedule);
        return service;
    }

    private static HistoryReentrySchedule schedule(DayOfWeek day, LocalTime time, LocalDate lastRun) {
        return new HistoryReentrySchedule(null, true, StrategyMode.PAPER, day, time,
                HistoryReentrySchedule.Cadence.WEEKLY, HistoryReentrySchedule.Group.GAINS, 10, lastRun);
    }
}
