package com.neuralarc.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Properties;

public final class AppMetadata {
    private static final Properties PROPERTIES = loadProperties();
    private static final int DEFAULT_SPLASH_DURATION_MILLIS = 5000;
    private static final int DEFAULT_STRATEGY_POLLING_SECONDS = 20;

    private AppMetadata() {
    }

    public static String name() {
        return PROPERTIES.getProperty("app.name", "NeuralArc");
    }

    public static String version() {
        Package appPackage = AppMetadata.class.getPackage();
        if (appPackage != null && appPackage.getImplementationVersion() != null) {
            return appPackage.getImplementationVersion();
        }
        return PROPERTIES.getProperty("app.version", "dev");
    }

    public static String copyright() {
        return PROPERTIES.getProperty("app.copyright", "Copyright © 2026 NeuralArc. All rights reserved.");
    }

    public static String patent() {
        return PROPERTIES.getProperty("app.patent", "Patent Pending.");
    }

    public static int splashDurationMillis() {
        String configured = PROPERTIES.getProperty("app.splash.duration.millis", String.valueOf(DEFAULT_SPLASH_DURATION_MILLIS));
        try {
            return Math.max(0, Integer.parseInt(configured.trim()));
        } catch (NumberFormatException ignored) {
            return DEFAULT_SPLASH_DURATION_MILLIS;
        }
    }

    public static boolean analyticsEnabled() {
        return Boolean.parseBoolean(PROPERTIES.getProperty("app.analytics.enabled", "true").trim());
    }

    public static String analyticsEndpointDefault() {
        return PROPERTIES.getProperty("app.analytics.endpoint.default", "http://localhost:8080/events").trim();
    }


    public static String alpacaBaseUrl() {
        return PROPERTIES.getProperty("alpaca.baseUrl", "https://paper-api.alpaca.markets").trim();
    }

    public static String alpacaDataUrl() {
        return PROPERTIES.getProperty("alpaca.dataUrl", "https://data.alpaca.markets").trim();
    }

    public static String alpacaMode() {
        return PROPERTIES.getProperty("alpaca.mode", "PAPER").trim();
    }

    public static boolean liveTradingEnabled() {
        return Boolean.parseBoolean(PROPERTIES.getProperty("trading.live.enabled", "false").trim());
    }

    public static int defaultStrategyPollingSeconds() {
        String configured = PROPERTIES.getProperty(
                "app.strategy.default.polling.seconds",
                String.valueOf(DEFAULT_STRATEGY_POLLING_SECONDS)
        );
        try {
            return Math.max(1, Integer.parseInt(configured.trim()));
        } catch (NumberFormatException ignored) {
            return DEFAULT_STRATEGY_POLLING_SECONDS;
        }
    }

    public static Path appDataDirectory() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        String userHome = System.getProperty("user.home");
        if (osName.contains("mac")) {
            return Path.of(userHome, "Library", "Application Support", "NeuralArc");
        }
        if (osName.contains("win")) {
            String roaming = System.getenv("APPDATA");
            if (roaming != null && !roaming.isBlank()) {
                return Path.of(roaming, "NeuralArc");
            }
        }
        return Path.of(userHome, ".neuralarc");
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();
        try (InputStream stream = AppMetadata.class.getClassLoader().getResourceAsStream("app.properties")) {
            if (stream != null) {
                properties.load(stream);
            }
        } catch (IOException ignored) {
            // Fall back to defaults when metadata cannot be loaded.
        }
        return properties;
    }
}
