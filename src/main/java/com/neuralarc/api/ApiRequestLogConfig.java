package com.neuralarc.api;

/**
 * Process-wide toggle for verbose broker API logging. When enabled, request/response JSON bodies
 * are pretty-printed into the logs (useful for debugging, but high-volume). Default OFF — pretty
 * printing produces very large logs that consume a lot of storage and is not recommended.
 */
public final class ApiRequestLogConfig {
    private static volatile boolean verboseJsonLogging = false;

    private ApiRequestLogConfig() {
    }

    public static boolean isVerboseJsonLogging() {
        return verboseJsonLogging;
    }

    public static void setVerboseJsonLogging(boolean enabled) {
        verboseJsonLogging = enabled;
    }
}
