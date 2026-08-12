package com.neuralarc.service;

import com.neuralarc.model.RangeRiderSchedule;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.rangerider.RangeRiderConfig;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RangeRiderScheduleServiceTest {
    private static final ZoneId ET = ZoneId.of("America/New_York");
    private static final LocalDate TRADING_DAY = LocalDate.of(2026, 6, 15);   // Monday, not a holiday
    private static final LocalDate WEEKEND = LocalDate.of(2026, 6, 13);       // Saturday

    private final AtomicInteger fires = new AtomicInteger();

    private RangeRiderScheduleService service(RangeRiderConfig.ExecutionFrequency frequency, boolean enabled) {
        RangeRiderScheduleService svc = new RangeRiderScheduleService(
                new MarketHoursService(), Clock.systemUTC(), schedule -> fires.incrementAndGet(), null);
        svc.setSchedule(new RangeRiderSchedule("s1", enabled, LocalTime.of(9, 45),
                LocalTime.of(9, 45), LocalTime.of(15, 30), false, "w1", configWith(frequency)));
        return svc;
    }

    private static RangeRiderConfig configWith(RangeRiderConfig.ExecutionFrequency frequency) {
        RangeRiderConfig d = RangeRiderConfig.defaults(StrategyMode.PAPER);
        return new RangeRiderConfig(d.lookbackSessions(), d.minimumAverageRangePercent(),
                d.maximumAverageRangePercent(), d.minimumSameDayFillRatePercent(), d.minimumAverageVolume(),
                d.minimumStockPrice(), d.maximumStockPrice(), d.entryBufferPercent(), d.exitBufferPercent(),
                d.stopLossPercent(), d.maxStocksToAdd(), frequency, d.mode(), List.of());
    }

    private static Instant et(LocalDate date, int hour, int minute) {
        return ZonedDateTime.of(date, LocalTime.of(hour, minute), ET).toInstant();
    }

    @Test
    void firesOnceInsideWindowOnTradingDay() {
        RangeRiderScheduleService svc = service(RangeRiderConfig.ExecutionFrequency.MANUAL, true);

        assertTrue(svc.evaluate(et(TRADING_DAY, 9, 50)));
        assertEquals(1, fires.get());
        assertFalse(svc.evaluate(et(TRADING_DAY, 10, 20)));
        assertEquals(1, fires.get());
    }

    @Test
    void doesNotFireBeforeScanTimeOrAfterWindowEnd() {
        RangeRiderScheduleService svc = service(RangeRiderConfig.ExecutionFrequency.MANUAL, true);

        assertFalse(svc.evaluate(et(TRADING_DAY, 9, 30)));
        assertFalse(svc.evaluate(et(TRADING_DAY, 15, 30)));
        assertFalse(svc.evaluate(et(TRADING_DAY, 16, 0)));
        assertEquals(0, fires.get());
    }

    @Test
    void neverFiresOnClosedDays() {
        RangeRiderScheduleService svc = service(RangeRiderConfig.ExecutionFrequency.MANUAL, true);

        assertFalse(svc.evaluate(et(WEEKEND, 9, 50)));
        assertEquals(0, fires.get());
    }

    @Test
    void reScansOnCadenceWithinWindow() {
        RangeRiderScheduleService svc = service(RangeRiderConfig.ExecutionFrequency.EVERY_15_MINUTES, true);

        assertTrue(svc.evaluate(et(TRADING_DAY, 9, 45)));    // initial
        assertFalse(svc.evaluate(et(TRADING_DAY, 9, 55)));   // < 15 min later
        assertTrue(svc.evaluate(et(TRADING_DAY, 10, 0)));    // 15 min later
        assertEquals(2, fires.get());
    }

    @Test
    void honorsTheHalfHourlyCadence() {
        RangeRiderScheduleService svc = service(RangeRiderConfig.ExecutionFrequency.EVERY_30_MINUTES, true);

        assertTrue(svc.evaluate(et(TRADING_DAY, 9, 45)));    // initial
        assertFalse(svc.evaluate(et(TRADING_DAY, 10, 0)));   // 15 min is not enough
        assertTrue(svc.evaluate(et(TRADING_DAY, 10, 15)));   // 30 min later
        assertEquals(2, fires.get());
    }

    @Test
    void disabledScheduleNeverFires() {
        RangeRiderScheduleService svc = service(RangeRiderConfig.ExecutionFrequency.EVERY_15_MINUTES, false);

        assertFalse(svc.evaluate(et(TRADING_DAY, 9, 50)));
        assertEquals(0, fires.get());
    }

    @Test
    void noScheduleNeverFires() {
        RangeRiderScheduleService svc = new RangeRiderScheduleService(
                new MarketHoursService(), Clock.systemUTC(), schedule -> fires.incrementAndGet(), null);

        assertFalse(svc.evaluate(et(TRADING_DAY, 9, 50)));
        assertEquals(0, fires.get());
    }

    @Test
    void clearingTheScheduleStopsFurtherFires() {
        RangeRiderScheduleService svc = service(RangeRiderConfig.ExecutionFrequency.MANUAL, true);
        svc.clearSchedule();

        assertFalse(svc.evaluate(et(TRADING_DAY, 9, 50)));
        assertEquals(0, fires.get());
    }

    @Test
    void firesAgainTheNextTradingDay() {
        RangeRiderScheduleService svc = service(RangeRiderConfig.ExecutionFrequency.MANUAL, true);

        assertTrue(svc.evaluate(et(TRADING_DAY, 9, 50)));
        assertTrue(svc.evaluate(et(TRADING_DAY.plusDays(1), 9, 50))); // Tuesday
        assertEquals(2, fires.get());
    }
}
