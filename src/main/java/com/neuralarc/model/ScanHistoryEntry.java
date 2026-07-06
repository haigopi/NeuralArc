package com.neuralarc.model;

import java.time.Instant;
import java.util.UUID;

/**
 * One recorded strategy scan run for a workspace. Captures when the scan ran, whether it was a
 * manual (operator-triggered) or scheduled (autonomous) run, and a short human-readable summary of
 * the outcome (e.g. "Added 3 candidates" or "No qualifying candidates"). Used to surface a
 * last-N scan history in a strategy's empty state so the operator can see what recent scans did.
 */
public record ScanHistoryEntry(
        String id,
        String workspaceId,
        Instant ranAt,
        String trigger,
        String summary
) {
    public static final String TRIGGER_MANUAL = "Manual";
    public static final String TRIGGER_SCHEDULED = "Scheduled";

    public ScanHistoryEntry {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        workspaceId = workspaceId == null ? "" : workspaceId;
        ranAt = ranAt == null ? Instant.now() : ranAt;
        trigger = trigger == null || trigger.isBlank() ? TRIGGER_MANUAL : trigger;
        summary = summary == null ? "" : summary;
    }

    /** Build a new entry for the current instant. */
    public static ScanHistoryEntry now(String workspaceId, boolean interactive, String summary) {
        return new ScanHistoryEntry(null, workspaceId, Instant.now(),
                interactive ? TRIGGER_MANUAL : TRIGGER_SCHEDULED, summary);
    }

    /** Human-readable outcome summary shared by the strategy coordinators. */
    public static String summarize(int added, int updated, int skipped) {
        if (added == 0 && updated == 0 && skipped == 0) {
            return "No changes";
        }
        StringBuilder summary = new StringBuilder();
        summary.append("Added ").append(added);
        if (updated > 0) {
            summary.append(", refreshed ").append(updated);
        }
        if (skipped > 0) {
            summary.append(", skipped ").append(skipped);
        }
        return summary.toString();
    }
}
