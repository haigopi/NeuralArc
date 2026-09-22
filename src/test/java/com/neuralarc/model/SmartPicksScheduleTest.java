package com.neuralarc.model;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SmartPicksScheduleTest {
    @Test
    void daysRoundTripThroughTheirStoredCode() {
        SmartPicksSchedule schedule = new SmartPicksSchedule(null, true, "w", "LEADERS", LocalTime.of(10, 0),
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), false, 1, null, null);

        assertEquals("MON,WED", schedule.daysCode());
        assertEquals(EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), SmartPicksSchedule.parseDays(schedule.daysCode()));
        assertEquals("Mon, Wed 10:00 ET", schedule.summary());
    }

    @Test
    void noDaysMeansEveryWeekday() {
        SmartPicksSchedule schedule = new SmartPicksSchedule(null, true, "w", "MOVERS", null, null, false, 0, null, null);

        assertEquals("Weekdays 10:00 ET", schedule.summary());
        assertEquals(1, schedule.quantity(), "at least one share per pick");
        assertEquals(EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), SmartPicksSchedule.parseDays(""));
    }
}
