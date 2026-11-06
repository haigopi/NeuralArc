package com.neuralarc.service;

import com.neuralarc.model.ScannerEntrySettings;
import com.neuralarc.model.ScannerEntrySettings.Anchor;
import com.neuralarc.model.ScannerEntrySettings.Mode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScannerEntrySettingsPersistenceTest {
    @TempDir
    Path tempDir;

    @Test
    void anUntouchedInstallUsesTheTwoWeekLowOrCurrentLow() {
        assertEquals(ScannerEntrySettings.defaults(),
                new AppSettingsService(tempDir.resolve("entry-defaults.db")).loadScannerEntrySettings());
    }

    @Test
    void theChoiceSurvivesARestart() throws Exception {
        Path dbPath = tempDir.resolve("entry-settings.db");
        ScannerEntrySettings chosen = new ScannerEntrySettings(Mode.CUSTOM_PERCENT, Anchor.SIX_MONTHS, new BigDecimal("7.5"));

        new AppSettingsService(dbPath).saveScannerEntrySettings(chosen);

        assertEquals(chosen, new AppSettingsService(dbPath).loadScannerEntrySettings());
    }

    @Test
    void theAutomaticModeIsStoredToo() throws Exception {
        Path dbPath = tempDir.resolve("entry-auto.db");
        ScannerEntrySettings auto = new ScannerEntrySettings(Mode.AUTO_TREND, Anchor.TWO_WEEKS, BigDecimal.ZERO);

        new AppSettingsService(dbPath).saveScannerEntrySettings(auto);

        assertEquals(Mode.AUTO_TREND, new AppSettingsService(dbPath).loadScannerEntrySettings().mode());
    }
}
