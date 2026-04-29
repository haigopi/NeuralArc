package com.neuralarc.service;

import com.neuralarc.model.ApplicationMode;
import com.neuralarc.model.BrokerType;
import com.neuralarc.util.AppMetadata;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class AppSettingsService {
    public static final boolean DEFAULT_AUTO_PAUSE_POLLING_WHEN_MARKET_CLOSED = true;
    public static final boolean DEFAULT_EXTENDED_HOURS_TRADING_ENABLED = false;

    private final Path settingsFile;

    public AppSettingsService() {
        this(AppMetadata.appDataDirectory().resolve("settings.properties"));
    }

    AppSettingsService(Path settingsFile) {
        this.settingsFile = settingsFile;
    }

    public AppSettings load() {
        Properties properties = new Properties();
        if (Files.exists(settingsFile)) {
            try (InputStream input = Files.newInputStream(settingsFile)) {
                properties.load(input);
            } catch (IOException ignored) {
                // Fallback defaults are applied below.
            }
        }
        return new AppSettings(
                properties.getProperty("userEmail", "").trim(),
                parseBoolean(properties, "telemetryEnabled", true),
                parseBoolean(properties, "autoPausePollingWhenMarketClosed", DEFAULT_AUTO_PAUSE_POLLING_WHEN_MARKET_CLOSED),
                parseBoolean(properties, "extendedHoursTradingEnabled", DEFAULT_EXTENDED_HOURS_TRADING_ENABLED),
                parseBroker(properties.getProperty("broker", BrokerType.ALPACA.name())),
                parseMode(properties.getProperty("applicationMode", ApplicationMode.PAPER.name()))
        );
    }

    public void save(AppSettings settings) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("userEmail", settings.userEmail() == null ? "" : settings.userEmail().trim());
        properties.setProperty("telemetryEnabled", String.valueOf(settings.telemetryEnabled()));
        properties.setProperty("autoPausePollingWhenMarketClosed", String.valueOf(settings.autoPausePollingWhenMarketClosed()));
        properties.setProperty("extendedHoursTradingEnabled", String.valueOf(settings.extendedHoursTradingEnabled()));
        properties.setProperty("broker", (settings.brokerType() == null ? BrokerType.ALPACA : settings.brokerType()).name());
        properties.setProperty("applicationMode", (settings.applicationMode() == null ? ApplicationMode.PAPER : settings.applicationMode()).name());
        Files.createDirectories(settingsFile.getParent());
        try (OutputStream output = Files.newOutputStream(settingsFile)) {
            properties.store(output, "NeuralArc settings");
        }
    }

    private boolean parseBoolean(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private BrokerType parseBroker(String value) {
        try {
            return BrokerType.valueOf(value.trim());
        } catch (Exception ignored) {
            return BrokerType.ALPACA;
        }
    }

    private ApplicationMode parseMode(String value) {
        try {
            return ApplicationMode.valueOf(value.trim());
        } catch (Exception ignored) {
            return ApplicationMode.PAPER;
        }
    }

    public record AppSettings(
            String userEmail,
            boolean telemetryEnabled,
            boolean autoPausePollingWhenMarketClosed,
            boolean extendedHoursTradingEnabled,
            BrokerType brokerType,
            ApplicationMode applicationMode
    ) {
    }
}
