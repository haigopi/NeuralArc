package com.neuralarc.util;

import com.neuralarc.model.ApplicationMode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Properties;

public final class AppMetadata {
    private static final Properties PROPERTIES = loadProperties();
    private static final int DEFAULT_SPLASH_DURATION_MILLIS = 2000;
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

    public static String displayVersion() {
        return normalizeDisplayVersion(version());
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


    public static String alpacaTradingBaseUrl(ApplicationMode mode) {
        String key = mode == ApplicationMode.LIVE ? "alpaca.trading.liveUrl" : "alpaca.trading.paperUrl";
        String defaultValue = mode == ApplicationMode.LIVE
                ? "https://api.alpaca.markets"
                : "https://paper-api.alpaca.markets";
        return PROPERTIES.getProperty(key, defaultValue).trim();
    }

    public static String alpacaDataUrl() {
        return PROPERTIES.getProperty("alpaca.dataUrl", "https://data.alpaca.markets").trim();
    }

    public static String alpacaTradingEventsWebSocketUrl(boolean liveMode) {
        String key = liveMode ? "alpaca.trading.events.websocket.liveUrl" : "alpaca.trading.events.websocket.paperUrl";
        String configured = PROPERTIES.getProperty(key, "").trim();
        if (!configured.isBlank()) {
            return configured;
        }
        return liveMode ? "wss://api.alpaca.markets/stream" : "wss://paper-api.alpaca.markets/stream";
    }

    public static boolean alpacaTradingEventsWebSocketEnabled() {
        return Boolean.parseBoolean(PROPERTIES.getProperty("alpaca.trading.events.websocket.enabled", "true").trim());
    }

    public static String alpacaMode() {
        return PROPERTIES.getProperty("alpaca.mode", "PAPER").trim();
    }

    public static boolean liveTradingEnabled() {
        return Boolean.parseBoolean(PROPERTIES.getProperty("trading.live.enabled", "false").trim());
    }

    public static boolean updateCheckEnabled() {
        return Boolean.parseBoolean(PROPERTIES.getProperty("app.update.enabled", "true").trim());
    }

    public static String githubLatestReleaseUrl() {
        return PROPERTIES.getProperty("app.update.github.latestReleaseUrl", "").trim();
    }

    public static String alpacaSignupUrl() {
        return PROPERTIES.getProperty("alpaca.signup.url", "https://app.alpaca.markets/signup").trim();
    }

    public static String mailjetApiKey() {
        return configuredOrEnv("mailjet.api.key", "MAILJET_API_KEY", "");
    }

    public static String mailjetApiSecret() {
        return configuredOrEnv("mailjet.api.secret", "MAILJET_API_SECRET", "");
    }

    public static String mailjetFromEmail() {
        return configuredOrEnv("mailjet.from.email", "MAILJET_FROM_EMAIL", "");
    }

    public static String mailjetFromName() {
        return configuredOrEnv("mailjet.from.name", "MAILJET_FROM_NAME", "NeuralArc Desktop");
    }

    public static String mailjetToEmail() {
        return configuredOrEnv("mailjet.to.email", "MAILJET_TO_EMAIL", "");
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

    private static String configuredOrEnv(String propertyKey, String envKey, String defaultValue) {
        String configured = PROPERTIES.getProperty(propertyKey, "").trim();
        if (!configured.isBlank()) {
            return configured;
        }
        String env = System.getenv(envKey);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return defaultValue;
    }

    private static String normalizeDisplayVersion(String version) {
        if (version == null || version.isBlank()) {
            return "dev";
        }
        String trimmed = version.trim();
        String normalized = trimmed.replaceFirst("(?i)-SNAPSHOT$", "");
        return normalized.isBlank() ? trimmed : normalized;
    }
}
