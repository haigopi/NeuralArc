package com.neuralarc.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class AppMetadata {
    private static final Properties PROPERTIES = loadProperties();

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
        String configured = PROPERTIES.getProperty("app.splash.duration.millis", "5000");
        try {
            return Math.max(0, Integer.parseInt(configured.trim()));
        } catch (NumberFormatException ignored) {
            return 5000;
        }
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
