package com.neuralarc.ui;

import java.util.Locale;
import java.util.regex.Pattern;

final class EventLogSeverity {
    private static final Pattern FAILURE_PATTERN = Pattern.compile(
            "(^|[\\]\\s:-])("
                    + "failed|failure|failures|rejected|rejection|error|exception|cancel failed|order rejected"
                    + ")([\\s:.,;\\]]|$)"
    );
    private static final Pattern WARNING_PATTERN = Pattern.compile(
            "(^|[\\]\\s:-])("
                    + "skipped|canceled|cancelled|blocked|disabled|required|missing|unavailable|warning|paused"
                    + ")([\\s:.,;\\]]|$)"
    );
    private static final Pattern SUCCESS_PATTERN = Pattern.compile(
            "(^|[\\]\\s:-])("
                    + "completed|succeeded|success|submitted|placed|deleted|restored|recovered|saved|added|created"
                    + ")([\\s:.,;\\]]|$)"
    );
    private static final Pattern PROCESSING_PATTERN = Pattern.compile(
            "(^|[\\]\\s:-])("
                    + "preparing|evaluating|checking|loading|refresh|polling|analyzed|sync|placement check|started"
                    + ")([\\s:.,;\\]]|$)"
    );

    private EventLogSeverity() {
    }

    static boolean isFailure(String logEntry) {
        return tone(logEntry) == Tone.FAILURE;
    }

    static Tone tone(String logEntry) {
        if (logEntry == null || logEntry.isBlank()) {
            return Tone.INFO;
        }
        String normalized = logEntry.toLowerCase(Locale.ROOT);
        if (!isBusinessInfoPhrase(normalized) && FAILURE_PATTERN.matcher(normalized).find()) {
            return Tone.FAILURE;
        }
        if (WARNING_PATTERN.matcher(normalized).find()) {
            return Tone.WARNING;
        }
        if (SUCCESS_PATTERN.matcher(normalized).find()) {
            return Tone.SUCCESS;
        }
        if (PROCESSING_PATTERN.matcher(normalized).find()) {
            return Tone.PROCESSING;
        }
        return Tone.INFO;
    }

    private static boolean isBusinessInfoPhrase(String normalized) {
        return normalized.contains("recovered stale failed status")
                || normalized.contains("failed status from broker exposure")
                || normalized.contains("no failures")
                || normalized.contains("failure count=0")
                || normalized.contains("failed=0");
    }

    enum Tone {
        INFO,
        PROCESSING,
        SUCCESS,
        WARNING,
        FAILURE
    }
}
