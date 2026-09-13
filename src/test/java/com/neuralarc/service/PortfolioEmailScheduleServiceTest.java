package com.neuralarc.service;

import com.neuralarc.model.PortfolioEmailSettings;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioEmailScheduleServiceTest {
    private static final ZoneId ET = ZoneId.of("America/New_York");
    // Monday 14 September 2026 is a trading day; Labor Day was Monday 7 September.
    private final List<String> sent = new ArrayList<>();
    private final PortfolioEmailScheduleService scheduler = new PortfolioEmailScheduleService(
            new MarketHoursService(), Clock.systemUTC(), slot -> sent.add(slot.label()), message -> { });

    @Test
    void eachDefaultTimeSendsOnceOnATradingDay() {
        scheduler.setSettings(PortfolioEmailSettings.defaults());

        everyMinuteOf(2026, 9, 14);

        assertEquals(List.of("Before the open", "After the open", "Lunch", "Before the close", "After the close"), sent);
    }

    @Test
    void sendsAtTheTimeAndNotBefore() {
        scheduler.setSettings(PortfolioEmailSettings.defaults());

        assertTrue(scheduler.evaluate(et(2026, 9, 14, 9, 24, 59)).isEmpty());
        assertEquals("Before the open", scheduler.evaluate(et(2026, 9, 14, 9, 25, 0)).orElseThrow().label());
    }

    @Test
    void weekendsAndMarketHolidaysAreSkipped() {
        scheduler.setSettings(PortfolioEmailSettings.defaults());

        everyMinuteOf(2026, 9, 12);
        everyMinuteOf(2026, 9, 7);

        assertEquals(List.of(), sent);
    }

    @Test
    void aTimeMissedByMoreThanTheGraceIsSkippedRatherThanSentLate() {
        scheduler.setSettings(PortfolioEmailSettings.defaults());
        assertTrue(scheduler.evaluate(et(2026, 9, 14, 9, 28, 0)).isEmpty(), "three minutes late is too late");

        PortfolioEmailScheduleService other = new PortfolioEmailScheduleService(
                new MarketHoursService(), Clock.systemUTC(), slot -> sent.add(slot.label()), message -> { });
        other.setSettings(PortfolioEmailSettings.defaults());
        assertEquals("Before the open", other.evaluate(et(2026, 9, 14, 9, 27, 30)).orElseThrow().label(),
                "a couple of minutes late still sends");
    }

    @Test
    void switchedOffTimesAndTheMainSwitchNeverSend() {
        scheduler.setSettings(new PortfolioEmailSettings(true, List.of(
                new PortfolioEmailSettings.Slot("Lunch", LocalTime.of(12, 0), false))));
        everyMinuteOf(2026, 9, 14);

        scheduler.setSettings(new PortfolioEmailSettings(false, PortfolioEmailSettings.defaultSlots()));
        everyMinuteOf(2026, 9, 15);

        assertEquals(List.of(), sent);
    }

    @Test
    void customTimesSend() {
        scheduler.setSettings(new PortfolioEmailSettings(true, List.of(
                new PortfolioEmailSettings.Slot(PortfolioEmailSettings.CUSTOM_LABEL, LocalTime.of(13, 30), true))));

        everyMinuteOf(2026, 9, 14);

        assertEquals(List.of("Custom"), sent);
    }

    @Test
    void savingSettingsAgainDoesNotResendATimeAlreadySent() {
        scheduler.setSettings(PortfolioEmailSettings.defaults());
        scheduler.evaluate(et(2026, 9, 14, 9, 25, 10));

        scheduler.setSettings(PortfolioEmailSettings.defaults());
        scheduler.evaluate(et(2026, 9, 14, 9, 26, 0));

        assertEquals(List.of("Before the open"), sent);
    }

    @Test
    void theNextTradingDayStartsAfresh() {
        scheduler.setSettings(PortfolioEmailSettings.defaults());

        scheduler.evaluate(et(2026, 9, 14, 12, 0, 5));
        scheduler.evaluate(et(2026, 9, 15, 12, 0, 5));

        assertEquals(List.of("Lunch", "Lunch"), sent);
    }

    private void everyMinuteOf(int year, int month, int day) {
        for (int minute = 0; minute < 24 * 60; minute++) {
            scheduler.evaluate(et(year, month, day, minute / 60, minute % 60, 0));
        }
    }

    private static Instant et(int year, int month, int day, int hour, int minute, int second) {
        return ZonedDateTime.of(year, month, day, hour, minute, second, 0, ET).toInstant();
    }
}
