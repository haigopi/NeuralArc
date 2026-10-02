package com.neuralarc.service;

import com.neuralarc.model.PreOpenRepositionSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreOpenRepositionSettingsPersistenceTest {
    @TempDir
    Path tempDir;

    @Test
    void nothingIsRepositionedUntilTheOperatorAsksForIt() {
        PreOpenRepositionSettings defaults =
                new AppSettingsService(tempDir.resolve("preopen-defaults.db")).loadPreOpenRepositionSettings();

        assertFalse(defaults.enabled(), "orders must never move on their own by default");
        assertEquals(PreOpenRepositionSettings.DEFAULT_MINUTES_BEFORE_OPEN, defaults.minutesBeforeOpen());
        assertTrue(defaults.dayOrdersOnly(), "a GTC order is still working and has missed nothing");
    }

    @Test
    void theChoiceSurvivesARestart() throws Exception {
        Path dbPath = tempDir.resolve("preopen-settings.db");

        new AppSettingsService(dbPath).savePreOpenRepositionSettings(
                new PreOpenRepositionSettings(true, 45, false));
        PreOpenRepositionSettings loaded = new AppSettingsService(dbPath).loadPreOpenRepositionSettings();

        assertTrue(loaded.enabled());
        assertEquals(45, loaded.minutesBeforeOpen());
        assertFalse(loaded.dayOrdersOnly());
    }

    @Test
    void anOutlandishLeadTimeIsClampedRatherThanTrusted() throws Exception {
        AppSettingsService service = new AppSettingsService(tempDir.resolve("preopen-limits.db"));

        service.savePreOpenRepositionSettings(new PreOpenRepositionSettings(true, 5_000, true));
        assertEquals(PreOpenRepositionSettings.MAX_MINUTES_BEFORE_OPEN,
                service.loadPreOpenRepositionSettings().minutesBeforeOpen());

        service.savePreOpenRepositionSettings(new PreOpenRepositionSettings(true, 0, true));
        assertEquals(PreOpenRepositionSettings.DEFAULT_MINUTES_BEFORE_OPEN,
                service.loadPreOpenRepositionSettings().minutesBeforeOpen(),
                "zero would mean the bell itself");
    }

    @Test
    void theSummaryReadsLikeTheSentenceItReplaces() {
        assertEquals("15 minutes before the open · DAY entries only",
                PreOpenRepositionSettings.defaults().summary());
        assertEquals("1 minute before the open · every expired entry",
                new PreOpenRepositionSettings(true, 1, false).summary());
    }
}
