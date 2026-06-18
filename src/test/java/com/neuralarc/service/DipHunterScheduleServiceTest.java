package com.neuralarc.service;

import com.neuralarc.diphunter.DipHunterConfig;
import com.neuralarc.model.DipHunterSchedule;
import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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

class DipHunterScheduleServiceTest {
    private static final ZoneId ET = ZoneId.of("America/New_York");
    private static final LocalDate TRADING_DAY = LocalDate.of(2026, 6, 15);   // Monday, not a holiday
    private static final LocalDate WEEKEND = LocalDate.of(2026, 6, 13);       // Saturday

    private final AtomicInteger fires = new AtomicInteger();

    private DipHunterScheduleService service(DipHunterConfig.ExecutionFrequency frequency, boolean enabled) {
        DipHunterScheduleService svc = new DipHunterScheduleService(
                new MarketHoursService(), Clock.systemUTC(), schedule -> fires.incrementAndGet(), null);
        svc.setSchedule(new DipHunterSchedule("s1", enabled, LocalTime.of(10, 0),
                LocalTime.of(10, 0), LocalTime.of(15, 30), false, "w1", configWith(frequency)));
        return svc;
    }

    private static DipHunterConfig configWith(DipHunterConfig.ExecutionFrequency frequency) {
        DipHunterConfig d = DipHunterConfig.defaults(StrategyMode.PAPER);
        return new DipHunterConfig(d.minimumPullbackPercent(), d.maximumPullbackPercent(), d.minimumAverageVolume(),
                d.minimumStockPrice(), d.minimumRelativeVolume(), d.maximumStockPrice(), d.trendFilter(),
                d.bounceConfirmation(), d.stopLossPercent(), d.takeProfitPercent(), d.maxStocksToAdd(),
                frequency, d.mode(), List.of());
    }

    private static Instant et(LocalDate date, int hour, int minute) {
        return ZonedDateTime.of(date, LocalTime.of(hour, minute), ET).toInstant();
    }

    @Test
    void firesOnceInsideWindowOnTradingDay() {
        DipHunterScheduleService svc = service(DipHunterConfig.ExecutionFrequency.MANUAL, true);

        assertTrue(svc.evaluate(et(TRADING_DAY, 10, 5)));
        assertEquals(1, fires.get());
        assertFalse(svc.evaluate(et(TRADING_DAY, 10, 20)));
        assertEquals(1, fires.get());
    }

    @Test
    void doesNotFireBeforeScanTimeOrAfterWindowEnd() {
        DipHunterScheduleService svc = service(DipHunterConfig.ExecutionFrequency.MANUAL, true);

        assertFalse(svc.evaluate(et(TRADING_DAY, 9, 30)));
        assertFalse(svc.evaluate(et(TRADING_DAY, 15, 30)));
        assertFalse(svc.evaluate(et(TRADING_DAY, 16, 0)));
        assertEquals(0, fires.get());
    }

    @Test
    void neverFiresOnClosedDays() {
        DipHunterScheduleService svc = service(DipHunterConfig.ExecutionFrequency.MANUAL, true);

        assertFalse(svc.evaluate(et(WEEKEND, 10, 5)));
        assertEquals(0, fires.get());
    }

    @Test
    void reScansOnCadenceWithinWindow() {
        DipHunterScheduleService svc = service(DipHunterConfig.ExecutionFrequency.EVERY_5_MINUTES, true);

        assertTrue(svc.evaluate(et(TRADING_DAY, 10, 0)));   // initial
        assertFalse(svc.evaluate(et(TRADING_DAY, 10, 3)));  // < 5 min later
        assertTrue(svc.evaluate(et(TRADING_DAY, 10, 5)));   // 5 min later
        assertEquals(2, fires.get());
    }

    @Test
    void disabledScheduleNeverFires() {
        DipHunterScheduleService svc = service(DipHunterConfig.ExecutionFrequency.EVERY_5_MINUTES, false);

        assertFalse(svc.evaluate(et(TRADING_DAY, 10, 5)));
        assertEquals(0, fires.get());
    }

    @Test
    void noScheduleNeverFires() {
        DipHunterScheduleService svc = new DipHunterScheduleService(
                new MarketHoursService(), Clock.systemUTC(), schedule -> fires.incrementAndGet(), null);

        assertFalse(svc.evaluate(et(TRADING_DAY, 10, 5)));
        assertEquals(0, fires.get());
    }

    @Test
    void firesAgainTheNextTradingDay() {
        DipHunterScheduleService svc = service(DipHunterConfig.ExecutionFrequency.MANUAL, true);

        assertTrue(svc.evaluate(et(TRADING_DAY, 10, 5)));
        assertTrue(svc.evaluate(et(TRADING_DAY.plusDays(1), 10, 5))); // Tuesday
        assertEquals(2, fires.get());
    }
}
