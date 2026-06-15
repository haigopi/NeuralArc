package com.neuralarc.service;

import com.neuralarc.gaprocket.GapRocketConfig;
import com.neuralarc.model.GapAndGoSchedule;
import com.neuralarc.model.StrategyMode;
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

class GapAndGoScheduleServiceTest {
    private static final ZoneId ET = ZoneId.of("America/New_York");
    private static final LocalDate TRADING_DAY = LocalDate.of(2026, 6, 15);   // Monday, not a holiday
    private static final LocalDate WEEKEND = LocalDate.of(2026, 6, 13);       // Saturday

    private final AtomicInteger fires = new AtomicInteger();

    private GapAndGoScheduleService service(GapRocketConfig.ExecutionFrequency frequency, boolean enabled) {
        GapAndGoScheduleService svc = new GapAndGoScheduleService(
                new MarketHoursService(), Clock.systemUTC(), schedule -> fires.incrementAndGet(), null);
        svc.setSchedule(new GapAndGoSchedule("s1", enabled, LocalTime.of(9, 5),
                LocalTime.of(9, 45), LocalTime.of(11, 0), false, "w1", configWith(frequency)));
        return svc;
    }

    private static GapRocketConfig configWith(GapRocketConfig.ExecutionFrequency frequency) {
        GapRocketConfig d = GapRocketConfig.defaults(StrategyMode.PAPER);
        return new GapRocketConfig(d.minimumPremarketGapPercent(), d.minimumPremarketVolume(), d.minimumStockPrice(),
                d.minimumRelativeVolume(), d.maximumStockPrice(), d.newsCatalystRequired(), d.catalystTypes(),
                d.marketTrendFilter(), d.entryStyle(), d.openingRangeDuration(), d.stopLossPercent(),
                d.takeProfitPercent(), d.maxStocksToAdd(), frequency, d.mode());
    }

    private static Instant et(LocalDate date, int hour, int minute) {
        return ZonedDateTime.of(date, LocalTime.of(hour, minute), ET).toInstant();
    }

    @Test
    void firesOnceInsideWindowOnTradingDay() {
        GapAndGoScheduleService svc = service(GapRocketConfig.ExecutionFrequency.MANUAL, true);

        assertTrue(svc.evaluate(et(TRADING_DAY, 9, 10)));
        assertEquals(1, fires.get());
        // No re-scan with MANUAL frequency.
        assertFalse(svc.evaluate(et(TRADING_DAY, 9, 20)));
        assertEquals(1, fires.get());
    }

    @Test
    void doesNotFireBeforeScanTimeOrAfterWindowEnd() {
        GapAndGoScheduleService svc = service(GapRocketConfig.ExecutionFrequency.MANUAL, true);

        assertFalse(svc.evaluate(et(TRADING_DAY, 8, 30)));
        assertFalse(svc.evaluate(et(TRADING_DAY, 11, 0)));
        assertFalse(svc.evaluate(et(TRADING_DAY, 14, 0)));
        assertEquals(0, fires.get());
    }

    @Test
    void neverFiresOnClosedDays() {
        GapAndGoScheduleService svc = service(GapRocketConfig.ExecutionFrequency.MANUAL, true);

        assertFalse(svc.evaluate(et(WEEKEND, 9, 10)));
        assertEquals(0, fires.get());
    }

    @Test
    void reScansOnCadenceWithinWindow() {
        GapAndGoScheduleService svc = service(GapRocketConfig.ExecutionFrequency.EVERY_5_MINUTES, true);

        assertTrue(svc.evaluate(et(TRADING_DAY, 9, 5)));   // initial
        assertFalse(svc.evaluate(et(TRADING_DAY, 9, 8)));  // < 5 min later
        assertTrue(svc.evaluate(et(TRADING_DAY, 9, 10)));  // 5 min later
        assertEquals(2, fires.get());
    }

    @Test
    void disabledScheduleNeverFires() {
        GapAndGoScheduleService svc = service(GapRocketConfig.ExecutionFrequency.EVERY_5_MINUTES, false);

        assertFalse(svc.evaluate(et(TRADING_DAY, 9, 10)));
        assertEquals(0, fires.get());
    }

    @Test
    void noScheduleNeverFires() {
        GapAndGoScheduleService svc = new GapAndGoScheduleService(
                new MarketHoursService(), Clock.systemUTC(), schedule -> fires.incrementAndGet(), null);

        assertFalse(svc.evaluate(et(TRADING_DAY, 9, 10)));
        assertEquals(0, fires.get());
    }

    @Test
    void firesAgainTheNextTradingDay() {
        GapAndGoScheduleService svc = service(GapRocketConfig.ExecutionFrequency.MANUAL, true);

        assertTrue(svc.evaluate(et(TRADING_DAY, 9, 10)));
        assertTrue(svc.evaluate(et(TRADING_DAY.plusDays(1), 9, 10))); // Tuesday
        assertEquals(2, fires.get());
    }
}
