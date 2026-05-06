package com.neuralarc.service;

import com.neuralarc.model.ApplicationMode;
import com.neuralarc.model.BrokerType;
import com.neuralarc.security.CredentialManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

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

    @Test
    void fallsBackModeWithoutClearingBrokerWhenApplicationModeIsInvalid() throws Exception {
        Path dbPath = tempDir.resolve("settings.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath())) {
            connection.createStatement().execute("CREATE TABLE IF NOT EXISTS app_settings (key TEXT PRIMARY KEY, value TEXT NOT NULL DEFAULT '', encrypted INTEGER NOT NULL DEFAULT 0)");
            try (PreparedStatement ps = connection.prepareStatement("INSERT OR REPLACE INTO app_settings (key, value, encrypted) VALUES (?, ?, 0)")) {
                ps.setString(1, "userEmail");
                ps.setString(2, "user@example.com");
                ps.executeUpdate();
                ps.setString(1, "broker");
                ps.setString(2, BrokerType.ALPACA.name());
                ps.executeUpdate();
                ps.setString(1, "applicationMode");
                ps.setString(2, "INVALID_MODE");
                ps.executeUpdate();
            }
        }

        AppSettingsService service = new AppSettingsService(dbPath);
        AppSettingsService.AppSettings loaded = service.load();

        assertEquals("user@example.com", loaded.userEmail());
        assertEquals(BrokerType.ALPACA, loaded.brokerType());
        assertEquals(ApplicationMode.PAPER, loaded.applicationMode());
    }

    @Test
    void migratesLegacyFilesIntoSqliteOnFirstLoad() throws Exception {
        Path legacyDir = tempDir.resolve("legacy");
        java.nio.file.Files.createDirectories(legacyDir);

        java.util.Properties settings = new java.util.Properties();
        settings.setProperty("userEmail", "legacy@example.com");
        settings.setProperty("telemetryEnabled", "false");
        settings.setProperty("autoPausePollingWhenMarketClosed", "false");
        settings.setProperty("extendedHoursTradingEnabled", "true");
        settings.setProperty("broker", BrokerType.ALPACA.name());
        settings.setProperty("applicationMode", ApplicationMode.LIVE.name());
        settings.setProperty("endpoint", "http://legacy-endpoint");
        try (java.io.OutputStream output = java.nio.file.Files.newOutputStream(legacyDir.resolve("settings.properties"))) {
            settings.store(output, "legacy");
        }

        UserIdentityService identityService = new UserIdentityService();
        String passphrase = identityService.generateUserId("legacy@example.com").substring(0, 16);
        new CredentialManager().save("LEGACY_KEY", "LEGACY_SECRET".toCharArray(), legacyDir.resolve("credentials-paper.properties"), passphrase);

        Path dbPath = tempDir.resolve("settings.db");
        AppSettingsService service = new AppSettingsService(dbPath, legacyDir);

        AppSettingsService.AppSettings loaded = service.load();
        assertEquals("legacy@example.com", loaded.userEmail());
        assertFalse(loaded.telemetryEnabled());
        assertFalse(loaded.autoPausePollingWhenMarketClosed());
        assertTrue(loaded.extendedHoursTradingEnabled());
        assertEquals(BrokerType.ALPACA, loaded.brokerType());
        assertEquals(ApplicationMode.LIVE, loaded.applicationMode());
        assertEquals("http://legacy-endpoint", service.loadEndpoint());

        String[] paperCreds = service.loadCredentials(ApplicationMode.PAPER);
        assertEquals("LEGACY_KEY", paperCreds[0]);
        assertEquals("LEGACY_SECRET", paperCreds[1]);

        assertFalse(java.nio.file.Files.exists(legacyDir.resolve("settings.properties")));
        assertFalse(java.nio.file.Files.exists(legacyDir.resolve("credentials-paper.properties")));
    }
}
