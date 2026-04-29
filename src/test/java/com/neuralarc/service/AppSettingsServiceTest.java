package com.neuralarc.service;

import com.neuralarc.model.ApplicationMode;
import com.neuralarc.model.BrokerType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppSettingsServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void persistsAutoPauseAndExtendedHoursSettings() throws Exception {
        AppSettingsService service = new AppSettingsService(tempDir.resolve("settings.properties"));
        AppSettingsService.AppSettings expected = new AppSettingsService.AppSettings(
                "user@example.com",
                true,
                false,
                true,
                BrokerType.ALPACA,
                ApplicationMode.PAPER
        );

        service.save(expected);
        AppSettingsService.AppSettings loaded = service.load();

        assertEquals("user@example.com", loaded.userEmail());
        assertFalse(loaded.autoPausePollingWhenMarketClosed());
        assertTrue(loaded.extendedHoursTradingEnabled());
        assertEquals(BrokerType.ALPACA, loaded.brokerType());
        assertEquals(ApplicationMode.PAPER, loaded.applicationMode());
    }
}
