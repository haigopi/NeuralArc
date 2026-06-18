package com.neuralarc.model;

import com.neuralarc.diphunter.DipHunterConfig;

import java.time.LocalTime;
import java.util.UUID;

/**
 * A persisted autonomous Dip Hunter schedule. The scan runs during the regular session (default
 * 10:00 ET — after the opening volatility settles) and re-scans on a cadence through the execution
 * window looking for fresh pullbacks. NeuralArc must be running at the scheduled time (local desktop
 * console, no cloud cron).
 */
public record DipHunterSchedule(
        String id,
        boolean enabled,
        LocalTime scanTimeEt,
        LocalTime executionWindowStartEt,
        LocalTime executionWindowEndEt,
        boolean executeAfterScan,
        String workspaceId,
        DipHunterConfig config
) {
    public static final LocalTime DEFAULT_SCAN_TIME_ET = LocalTime.of(10, 0);

    public DipHunterSchedule {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        scanTimeEt = scanTimeEt == null ? DEFAULT_SCAN_TIME_ET : scanTimeEt;
        executionWindowStartEt = executionWindowStartEt == null
                ? DipHunterConfig.PRIMARY_WINDOW_START_ET : executionWindowStartEt;
        executionWindowEndEt = executionWindowEndEt == null
                ? DipHunterConfig.PRIMARY_WINDOW_END_ET : executionWindowEndEt;
        workspaceId = workspaceId == null ? "" : workspaceId;
        config = config == null ? DipHunterConfig.defaults(null) : config;
    }

    /** Build an enabled schedule with the standard regular-session window for the given config/workspace. */
    public static DipHunterSchedule create(String workspaceId, DipHunterConfig config, boolean executeAfterScan) {
        return new DipHunterSchedule(null, true, DEFAULT_SCAN_TIME_ET,
                DipHunterConfig.PRIMARY_WINDOW_START_ET, DipHunterConfig.PRIMARY_WINDOW_END_ET,
                executeAfterScan, workspaceId, config);
    }
}
