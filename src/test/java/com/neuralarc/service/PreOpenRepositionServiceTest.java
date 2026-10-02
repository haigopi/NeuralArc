package com.neuralarc.service;

import com.neuralarc.model.PreOpenRepositionSettings;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreOpenRepositionServiceTest {
    private static final ZoneId US_EASTERN = ZoneId.of("America/New_York");
    /** A Wednesday, nothing special about it. */
    private static final LocalDate TRADING_DAY = LocalDate.of(2026, 9, 30);

    private final List<PreOpenRepositionSettings> fired = new ArrayList<>();

    @Test
    void itFiresInsideTheWindowBeforeTheBell() {
        PreOpenRepositionService service = service(new PreOpenRepositionSettings(true, 15, true));

        assertTrue(service.evaluate(eastern(TRADING_DAY, 9, 20)));
        assertEquals(1, fired.size());
        assertEquals(15, fired.get(0).minutesBeforeOpen());
    }

    @Test
    void itStaysQuietBeforeTheWindowOpens() {
        PreOpenRepositionService service = service(new PreOpenRepositionSettings(true, 15, true));

        assertFalse(service.evaluate(eastern(TRADING_DAY, 7, 0)), "too early: yesterday's close is not the plan yet");
        assertTrue(fired.isEmpty());
    }

    @Test
    void itDoesNotFireOnceTheMarketHasOpened() {
        PreOpenRepositionService service = service(new PreOpenRepositionSettings(true, 15, true));

        assertFalse(service.evaluate(eastern(TRADING_DAY, 10, 30)), "the window has passed; tomorrow is a day away");
        assertTrue(fired.isEmpty());
    }

    @Test
    void itRunsOnceAMorningHoweverOftenItIsTicked() {
        PreOpenRepositionService service = service(new PreOpenRepositionSettings(true, 15, true));

        assertTrue(service.evaluate(eastern(TRADING_DAY, 9, 20)));
        assertFalse(service.evaluate(eastern(TRADING_DAY, 9, 21)));
        assertFalse(service.evaluate(eastern(TRADING_DAY, 9, 29)));
        assertEquals(1, fired.size(), "one reposition a day, not one a minute");
    }

    @Test
    void aRunThatCouldNotStartIsRetriedByTheNextTick() {
        PreOpenRepositionService service = service(new PreOpenRepositionSettings(true, 15, true));

        assertTrue(service.evaluate(eastern(TRADING_DAY, 9, 20)));
        service.deferToday();

        assertTrue(service.evaluate(eastern(TRADING_DAY, 9, 25)), "the broker may have connected since");
        assertEquals(2, fired.size());
    }

    @Test
    void theNextMorningFiresAgain() {
        PreOpenRepositionService service = service(new PreOpenRepositionSettings(true, 15, true));

        assertTrue(service.evaluate(eastern(TRADING_DAY, 9, 20)));
        assertTrue(service.evaluate(eastern(TRADING_DAY.plusDays(1), 9, 20)));
        assertEquals(2, fired.size());
    }

    @Test
    void weekendsAndHolidaysAreTheMarketsBusiness() {
        PreOpenRepositionService service = service(new PreOpenRepositionSettings(true, 15, true));

        assertFalse(service.evaluate(eastern(LocalDate.of(2026, 10, 3), 9, 20)), "Saturday");
        assertFalse(service.evaluate(eastern(LocalDate.of(2026, 12, 25), 9, 20)), "Christmas Day");
        assertTrue(fired.isEmpty());
    }

    @Test
    void anOffSettingNeverFires() {
        PreOpenRepositionService service = service(PreOpenRepositionSettings.defaults());

        assertFalse(service.evaluate(eastern(TRADING_DAY, 9, 20)));
        assertTrue(fired.isEmpty());
    }

    @Test
    void aWiderWindowStartsEarlier() {
        PreOpenRepositionService service = service(new PreOpenRepositionSettings(true, 60, false));

        assertTrue(service.evaluate(eastern(TRADING_DAY, 8, 45)));
        assertEquals(1, fired.size());
        assertFalse(fired.get(0).dayOrdersOnly(), "the settings the operator chose reach the run unchanged");
    }

    @Test
    void changingTheSettingsLetsTheSameMorningRunAgain() {
        PreOpenRepositionService service = service(new PreOpenRepositionSettings(true, 15, true));
        assertTrue(service.evaluate(eastern(TRADING_DAY, 9, 20)));

        service.setSettings(new PreOpenRepositionSettings(true, 30, false));

        assertTrue(service.evaluate(eastern(TRADING_DAY, 9, 25)), "a just-changed setting deserves a run today");
        assertEquals(2, fired.size());
    }

    private PreOpenRepositionService service(PreOpenRepositionSettings settings) {
        PreOpenRepositionService service = new PreOpenRepositionService(
                new MarketHoursService(), Clock.systemUTC(), fired::add, message -> { });
        service.setSettings(settings);
        return service;
    }

    private static Instant eastern(LocalDate day, int hour, int minute) {
        return ZonedDateTime.of(day, LocalTime.of(hour, minute), US_EASTERN).toInstant();
    }
}
