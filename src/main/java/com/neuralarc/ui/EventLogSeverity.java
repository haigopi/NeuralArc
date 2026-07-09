package com.neuralarc.ui;

import java.util.Locale;
import java.util.regex.Pattern;

final class EventLogSeverity {
    private static final Pattern FAILURE_PATTERN = Pattern.compile(
            "(^|[\\]\\s:-])("
                    + "failed|failure|failures|rejected|rejection|error|exception|cancel failed|order rejected"
                    + ")([\\s:.,;\\]]|$)"
    );

    private EventLogSeverity() {
    }

    static boolean isFailure(String logEntry) {
        if (logEntry == null || logEntry.isBlank()) {
            return false;
        }
        String normalized = logEntry.toLowerCase(Locale.ROOT);
        if (isBusinessInfoPhrase(normalized)) {
            return false;
        }
        return FAILURE_PATTERN.matcher(normalized).find();
    }

    private static boolean isBusinessInfoPhrase(String normalized) {
        return normalized.contains("recovered stale failed status")
                || normalized.contains("failed status from broker exposure")
                || normalized.contains("no failures")
                || normalized.contains("failure count=0")
                || normalized.contains("failed=0");
    }
}
