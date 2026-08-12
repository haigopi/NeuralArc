package com.neuralarc.model;

import com.neuralarc.rangerider.RangeRiderConfig;

import java.time.LocalTime;
import java.util.UUID;

/**
 * A persisted autonomous Range Rider schedule. The scan runs shortly after the open (default 9:45 ET,
 * once the session is live and the day can still trade down to the planned entry) and re-scans on a
 * cadence through the execution window, stopping well before the close so the same-day sell still has
 * room to fill. NeuralArc must be running at the scheduled time (local desktop console, no cloud cron).
 */
public record RangeRiderSchedule(
        String id,
        boolean enabled,
        LocalTime scanTimeEt,
        LocalTime executionWindowStartEt,
        LocalTime executionWindowEndEt,
        boolean executeAfterScan,
        String workspaceId,
        RangeRiderConfig config
) {
    public static final LocalTime DEFAULT_SCAN_TIME_ET = LocalTime.of(9, 45);

    public RangeRiderSchedule {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        scanTimeEt = scanTimeEt == null ? DEFAULT_SCAN_TIME_ET : scanTimeEt;
        executionWindowStartEt = executionWindowStartEt == null
                ? RangeRiderConfig.PRIMARY_WINDOW_START_ET : executionWindowStartEt;
        executionWindowEndEt = executionWindowEndEt == null
                ? RangeRiderConfig.PRIMARY_WINDOW_END_ET : executionWindowEndEt;
        workspaceId = workspaceId == null ? "" : workspaceId;
        config = config == null ? RangeRiderConfig.defaults(null) : config;
    }

    /** Build an enabled schedule with the standard regular-session window for the given config/workspace. */
    public static RangeRiderSchedule create(String workspaceId, RangeRiderConfig config, boolean executeAfterScan) {
        return new RangeRiderSchedule(null, true, DEFAULT_SCAN_TIME_ET,
                RangeRiderConfig.PRIMARY_WINDOW_START_ET, RangeRiderConfig.PRIMARY_WINDOW_END_ET,
                executeAfterScan, workspaceId, config);
    }
}
