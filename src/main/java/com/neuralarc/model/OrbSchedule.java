package com.neuralarc.model;

import com.neuralarc.orb.OrbConfig;

import java.time.LocalTime;
import java.util.UUID;

/**
 * A persisted autonomous ORB schedule. The analysis fires after the opening range closes
 * (e.g., 9:45 ET for a 15-minute range) and the execution window ends at {@link OrbConfig#latestEntryTimeEt()}.
 * NeuralArc must be running at the scheduled time (local desktop console, no cloud cron).
 */
public record OrbSchedule(
        String id,
        boolean enabled,
        LocalTime rangeAnalysisTimeEt,
        LocalTime executionWindowEndEt,
        boolean executeAfterRangeClose,
        String workspaceId,
        OrbConfig config
) {
    public OrbSchedule {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        OrbConfig safeConfig = config == null ? OrbConfig.defaults(null) : config;
        rangeAnalysisTimeEt = rangeAnalysisTimeEt == null ? defaultAnalysisTime(safeConfig) : rangeAnalysisTimeEt;
        executionWindowEndEt = executionWindowEndEt == null ? safeConfig.latestEntryTimeEt() : executionWindowEndEt;
        workspaceId = workspaceId == null ? "" : workspaceId;
        config = safeConfig;
    }

    /** Build an enabled schedule for the given config, firing after the range closes. */
    public static OrbSchedule create(String workspaceId, OrbConfig config, boolean executeAfterRangeClose) {
        OrbConfig safeConfig = config == null ? OrbConfig.defaults(null) : config;
        return new OrbSchedule(null, true, defaultAnalysisTime(safeConfig),
                safeConfig.latestEntryTimeEt(), executeAfterRangeClose, workspaceId, safeConfig);
    }

    private static LocalTime defaultAnalysisTime(OrbConfig config) {
        return LocalTime.of(9, 30).plusMinutes(config.rangeDurationMinutes());
    }
}
