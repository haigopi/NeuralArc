package com.neuralarc.service;

import com.neuralarc.model.ProfitShieldSchedule;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.profitshield.ProfitShieldConfig;
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

class ProfitShieldScheduleServiceTest {
    private static final ZoneId ET = ZoneId.of("America/New_York");
    private static final LocalDate TRADING_DAY = LocalDate.of(2026, 6, 15);   // Monday, not a holiday
    private static final LocalDate WEEKEND = LocalDate.of(2026, 6, 13);       // Saturday

    private final AtomicInteger fires = new AtomicInteger();

    private ProfitShieldScheduleService service(boolean enabled) {
        ProfitShieldScheduleService svc = new ProfitShieldScheduleService(
                new MarketHoursService(), Clock.systemUTC(), schedule -> fires.incrementAndGet(), null);
        svc.setSchedule(new ProfitShieldSchedule("s1", enabled, LocalTime.of(9, 45),
                LocalTime.of(9, 45), LocalTime.of(15, 45), false, "w1",
                ProfitShieldConfig.defaults(StrategyMode.PAPER)));
        return svc;
    }

    private static Instant et(LocalDate date, int hour, int minute) {
        return ZonedDateTime.of(date, LocalTime.of(hour, minute), ET).toInstant();
    }

    @Test
    void firesOnceInsideTheWindowOnATradingDay() {
        ProfitShieldScheduleService svc = service(true);

        assertTrue(svc.evaluate(et(TRADING_DAY, 9, 50)));
        assertEquals(1, fires.get());
    }

    @Test
    void neverReScansTheSameSession() {
        ProfitShieldScheduleService svc = service(true);

        assertTrue(svc.evaluate(et(TRADING_DAY, 9, 45)));
        assertFalse(svc.evaluate(et(TRADING_DAY, 10, 30)));
        assertFalse(svc.evaluate(et(TRADING_DAY, 15, 0)));
        assertEquals(1, fires.get(), "a defensive profile does not change intraday");
    }

    @Test
    void doesNotFireBeforeScanTimeOrAfterWindowEnd() {
        ProfitShieldScheduleService svc = service(true);

        assertFalse(svc.evaluate(et(TRADING_DAY, 9, 30)));
        assertFalse(svc.evaluate(et(TRADING_DAY, 15, 45)));
        assertFalse(svc.evaluate(et(TRADING_DAY, 16, 30)));
        assertEquals(0, fires.get());
    }

    @Test
    void neverFiresOnClosedDays() {
        ProfitShieldScheduleService svc = service(true);

        assertFalse(svc.evaluate(et(WEEKEND, 9, 50)));
        assertEquals(0, fires.get());
    }

    @Test
    void firesAgainTheNextTradingDay() {
        ProfitShieldScheduleService svc = service(true);

        assertTrue(svc.evaluate(et(TRADING_DAY, 9, 50)));
        assertTrue(svc.evaluate(et(TRADING_DAY.plusDays(1), 9, 50)));
        assertEquals(2, fires.get());
    }

    @Test
    void disabledScheduleNeverFires() {
        ProfitShieldScheduleService svc = service(false);

        assertFalse(svc.evaluate(et(TRADING_DAY, 9, 50)));
        assertEquals(0, fires.get());
    }

    @Test
    void noScheduleNeverFires() {
        ProfitShieldScheduleService svc = new ProfitShieldScheduleService(
                new MarketHoursService(), Clock.systemUTC(), schedule -> fires.incrementAndGet(), null);

        assertFalse(svc.evaluate(et(TRADING_DAY, 9, 50)));
        assertEquals(0, fires.get());
    }

    @Test
    void clearingTheScheduleStopsFurtherFires() {
        ProfitShieldScheduleService svc = service(true);
        svc.clearSchedule();

        assertFalse(svc.evaluate(et(TRADING_DAY, 9, 50)));
        assertEquals(0, fires.get());
    }

    @Test
    void resettingTheScheduleAllowsAFreshFireTheSameDay() {
        ProfitShieldScheduleService svc = service(true);
        assertTrue(svc.evaluate(et(TRADING_DAY, 9, 50)));

        svc.setSchedule(svc.schedule()); // re-registering clears the fired-today state

        assertTrue(svc.evaluate(et(TRADING_DAY, 10, 30)));
        assertEquals(2, fires.get());
    }
}
