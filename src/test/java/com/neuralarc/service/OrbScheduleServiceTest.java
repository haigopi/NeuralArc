package com.neuralarc.service;

import com.neuralarc.model.OrbSchedule;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.orb.OrbConfig;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class OrbScheduleServiceTest {
    private static final ZoneId ET = ZoneId.of("America/New_York");
    private static final LocalDate TRADING_DAY = LocalDate.of(2026, 6, 15);   // Monday, not a holiday
    private static final LocalDate WEEKEND = LocalDate.of(2026, 6, 13);       // Saturday

    private final AtomicInteger fires = new AtomicInteger();

    private OrbScheduleService service(boolean enabled) {
        OrbScheduleService svc = new OrbScheduleService(
                new MarketHoursService(), Clock.systemUTC(), schedule -> fires.incrementAndGet(), null);
        // 15-min range: analysis time = 9:45, window end = 11:00
        svc.setSchedule(new OrbSchedule("s1", enabled,
                LocalTime.of(9, 45), LocalTime.of(11, 0), false, "w1",
                OrbConfig.defaults(StrategyMode.PAPER)));
        return svc;
    }

    private static Instant et(LocalDate date, int hour, int minute) {
        return ZonedDateTime.of(date, LocalTime.of(hour, minute), ET).toInstant();
    }

    @Test
    void firesOnceInsideWindowOnTradingDay() {
        OrbScheduleService svc = service(true);

        assertTrue(svc.evaluate(et(TRADING_DAY, 9, 46)));
        assertEquals(1, fires.get());
        // Does not fire again the same day.
        assertFalse(svc.evaluate(et(TRADING_DAY, 9, 50)));
        assertEquals(1, fires.get());
    }

    @Test
    void doesNotFireBeforeRangeClosesOrAfterWindowEnd() {
        OrbScheduleService svc = service(true);

        assertFalse(svc.evaluate(et(TRADING_DAY, 9, 30)));  // before range close
        assertFalse(svc.evaluate(et(TRADING_DAY, 9, 44)));  // still before 9:45
        assertFalse(svc.evaluate(et(TRADING_DAY, 11, 0)));  // exactly at window end (exclusive)
        assertFalse(svc.evaluate(et(TRADING_DAY, 12, 0)));  // after window end
        assertEquals(0, fires.get());
    }

    @Test
    void neverFiresOnClosedDays() {
        OrbScheduleService svc = service(true);

        assertFalse(svc.evaluate(et(WEEKEND, 9, 46)));
        assertEquals(0, fires.get());
    }

    @Test
    void firesAgainOnNextTradingDay() {
        OrbScheduleService svc = service(true);

        assertTrue(svc.evaluate(et(TRADING_DAY, 9, 46)));
        assertTrue(svc.evaluate(et(TRADING_DAY.plusDays(1), 9, 46)));  // Tuesday
        assertEquals(2, fires.get());
    }

    @Test
    void disabledScheduleNeverFires() {
        OrbScheduleService svc = service(false);

        assertFalse(svc.evaluate(et(TRADING_DAY, 9, 46)));
        assertEquals(0, fires.get());
    }

    @Test
    void noScheduleNeverFires() {
        OrbScheduleService svc = new OrbScheduleService(
                new MarketHoursService(), Clock.systemUTC(), schedule -> fires.incrementAndGet(), null);

        assertFalse(svc.evaluate(et(TRADING_DAY, 9, 46)));
        assertEquals(0, fires.get());
    }

    @Test
    void clearScheduleResetsFireState() {
        OrbScheduleService svc = service(true);

        assertTrue(svc.evaluate(et(TRADING_DAY, 9, 46)));
        assertEquals(1, fires.get());

        svc.clearSchedule();
        svc.setSchedule(new OrbSchedule("s2", true,
                LocalTime.of(9, 45), LocalTime.of(11, 0), true, "w1",
                OrbConfig.defaults(StrategyMode.PAPER)));

        assertTrue(svc.evaluate(et(TRADING_DAY, 9, 50)));
        assertEquals(2, fires.get());
    }

    @Test
    void defaultAnalysisTimeIsRangeDurationAfterOpen() {
        OrbConfig config15 = OrbConfig.defaults(StrategyMode.PAPER); // rangeDuration=15
        OrbSchedule schedule = OrbSchedule.create("w1", config15, false);
        assertEquals(LocalTime.of(9, 45), schedule.rangeAnalysisTimeEt());
        assertEquals(LocalTime.of(11, 0), schedule.executionWindowEndEt());
    }

    @Test
    void fiveMinuteRangeProducesCorrectAnalysisTime() {
        OrbConfig config5 = new OrbConfig(5, null, null, null, null, 5,
                null, null, null, null, LocalTime.of(11, 0), java.util.List.of(), true, false, StrategyMode.PAPER);
        OrbSchedule schedule = OrbSchedule.create("w1", config5, false);
        assertEquals(LocalTime.of(9, 35), schedule.rangeAnalysisTimeEt());
    }
}
