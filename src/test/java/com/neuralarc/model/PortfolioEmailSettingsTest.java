package com.neuralarc.model;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioEmailSettingsTest {
    @Test
    void defaultsAreTheFiveTimesAroundTheRegularSession() {
        PortfolioEmailSettings defaults = PortfolioEmailSettings.defaults();

        assertTrue(defaults.enabled());
        assertEquals("", defaults.recipient());
        assertEquals(List.of(LocalTime.of(9, 25), LocalTime.of(9, 40), LocalTime.of(12, 0), LocalTime.of(15, 55), LocalTime.of(16, 10)),
                defaults.slots().stream().map(PortfolioEmailSettings.Slot::timeEt).toList());
        assertEquals("on at 09:25, 09:40, 12:00, 15:55, 16:10 ET on trading days", defaults.describe());
    }

    @Test
    void onlySwitchedOnTimesSendAndNoneWhileTheEmailsAreOff() {
        List<PortfolioEmailSettings.Slot> slots = List.of(
                new PortfolioEmailSettings.Slot("Lunch", LocalTime.of(12, 0), true),
                new PortfolioEmailSettings.Slot("After the close", LocalTime.of(16, 10), false));

        assertEquals(1, new PortfolioEmailSettings(true, "", slots).activeSlots().size());
        assertEquals(List.of(), new PortfolioEmailSettings(false, "", slots).activeSlots());
        assertEquals("off", new PortfolioEmailSettings(false, "", slots).describe());
    }

    @Test
    void slotsAreKeptInTimeOrderAndSurviveStorage() {
        PortfolioEmailSettings settings = new PortfolioEmailSettings(true, " me@example.com ", List.of(
                new PortfolioEmailSettings.Slot("Custom", LocalTime.of(14, 5), false),
                new PortfolioEmailSettings.Slot("Before the open", LocalTime.of(9, 25), true)));

        assertEquals("me@example.com", settings.recipient());
        assertEquals("Before the open", settings.slots().get(0).label());
        assertEquals("Before the open@09:25@1;Custom@14:05@0", settings.encodeSlots());
        assertEquals(settings.slots(), PortfolioEmailSettings.decodeSlots(settings.encodeSlots()));
    }

    @Test
    void unreadableStorageFallsBackToTheDefaults() {
        assertEquals(PortfolioEmailSettings.defaultSlots(), PortfolioEmailSettings.decodeSlots(null));
        assertEquals(PortfolioEmailSettings.defaultSlots(), PortfolioEmailSettings.decodeSlots("garbage;also@bad"));
        assertEquals(List.of(new PortfolioEmailSettings.Slot("Lunch", LocalTime.of(12, 0), true)),
                PortfolioEmailSettings.decodeSlots("Lunch@12:00@1;Broken@25:99@1"), "a bad entry is dropped, the rest kept");
    }

    @Test
    void labelsCannotBreakTheStorageFormat() {
        PortfolioEmailSettings.Slot slot = new PortfolioEmailSettings.Slot("A@B;C", LocalTime.of(10, 0, 30), true);

        assertEquals("A B C", slot.label());
        assertEquals(LocalTime.of(10, 0), slot.timeEt(), "seconds are dropped");
        assertEquals(PortfolioEmailSettings.CUSTOM_LABEL, new PortfolioEmailSettings.Slot(" ", null, true).label());
    }
}
