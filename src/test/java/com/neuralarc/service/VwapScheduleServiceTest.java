package com.neuralarc.service;

import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.VwapSchedule;
import com.neuralarc.vwap.VwapConfig;
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

class VwapScheduleServiceTest {
    private static final ZoneId ET = ZoneId.of("America/New_York");
    private static final LocalDate TRADING_DAY = LocalDate.of(2026, 6, 15);   // Monday, not a holiday
    private static final LocalDate WEEKEND = LocalDate.of(2026, 6, 13);       // Saturday

    private final AtomicInteger fires = new AtomicInteger();

    private VwapScheduleService service(VwapConfig.ExecutionFrequency frequency, boolean enabled) {
        VwapScheduleService svc = new VwapScheduleService(
                new MarketHoursService(), Clock.systemUTC(), schedule -> fires.incrementAndGet(), null);
        svc.setSchedule(new VwapSchedule("s1", enabled, LocalTime.of(10, 0),
                LocalTime.of(10, 0), LocalTime.of(15, 30), false, "w1", configWith(frequency)));
        return svc;
    }

    private static VwapConfig configWith(VwapConfig.ExecutionFrequency frequency) {
        VwapConfig d = VwapConfig.defaults(StrategyMode.PAPER);
        return new VwapConfig(d.minimumDiscountPercent(), d.maximumDiscountPercent(), d.minimumAverageVolume(),
                d.minimumStockPrice(), d.minimumRelativeVolume(), d.maximumStockPrice(), d.trendFilter(),
                d.stopLossPercent(), d.maxStocksToAdd(), frequency, d.mode(), List.of());
    }

    private static Instant et(LocalDate date, int hour, int minute) {
        return ZonedDateTime.of(date, LocalTime.of(hour, minute), ET).toInstant();
    }

    @Test
    void firesOnceInsideWindowOnTradingDay() {
        VwapScheduleService svc = service(VwapConfig.ExecutionFrequency.MANUAL, true);

        assertTrue(svc.evaluate(et(TRADING_DAY, 10, 5)));
        assertEquals(1, fires.get());
        assertFalse(svc.evaluate(et(TRADING_DAY, 10, 20)));
        assertEquals(1, fires.get());
    }

    @Test
    void doesNotFireBeforeScanTimeOrAfterWindowEnd() {
        VwapScheduleService svc = service(VwapConfig.ExecutionFrequency.MANUAL, true);

        assertFalse(svc.evaluate(et(TRADING_DAY, 9, 30)));
        assertFalse(svc.evaluate(et(TRADING_DAY, 15, 30)));
        assertFalse(svc.evaluate(et(TRADING_DAY, 16, 0)));
        assertEquals(0, fires.get());
    }

    @Test
    void neverFiresOnClosedDays() {
        VwapScheduleService svc = service(VwapConfig.ExecutionFrequency.MANUAL, true);

        assertFalse(svc.evaluate(et(WEEKEND, 10, 5)));
        assertEquals(0, fires.get());
    }

    @Test
    void reScansOnCadenceWithinWindow() {
        VwapScheduleService svc = service(VwapConfig.ExecutionFrequency.EVERY_5_MINUTES, true);

        assertTrue(svc.evaluate(et(TRADING_DAY, 10, 0)));   // initial
        assertFalse(svc.evaluate(et(TRADING_DAY, 10, 3)));  // < 5 min later
        assertTrue(svc.evaluate(et(TRADING_DAY, 10, 5)));   // 5 min later
        assertEquals(2, fires.get());
    }

    @Test
    void disabledScheduleNeverFires() {
        VwapScheduleService svc = service(VwapConfig.ExecutionFrequency.EVERY_5_MINUTES, false);

        assertFalse(svc.evaluate(et(TRADING_DAY, 10, 5)));
        assertEquals(0, fires.get());
    }

    @Test
    void noScheduleNeverFires() {
        VwapScheduleService svc = new VwapScheduleService(
                new MarketHoursService(), Clock.systemUTC(), schedule -> fires.incrementAndGet(), null);

        assertFalse(svc.evaluate(et(TRADING_DAY, 10, 5)));
        assertEquals(0, fires.get());
    }

    @Test
    void firesAgainTheNextTradingDay() {
        VwapScheduleService svc = service(VwapConfig.ExecutionFrequency.MANUAL, true);

        assertTrue(svc.evaluate(et(TRADING_DAY, 10, 5)));
        assertTrue(svc.evaluate(et(TRADING_DAY.plusDays(1), 10, 5))); // Tuesday
        assertEquals(2, fires.get());
    }
}
