package com.neuralarc.model;

import com.neuralarc.gaprocket.GapRocketConfig;

import java.time.LocalTime;
import java.util.UUID;

/**
 * A persisted autonomous gap-and-go schedule. The scan runs premarket (default 09:05 ET — the
 * earliest reliably-usable slot on the IEX free feed) and carries through the post-open execution
 * window. NeuralArc must be running at the scheduled time (local desktop console, no cloud cron).
 */
public record GapAndGoSchedule(
        String id,
        boolean enabled,
        LocalTime scanTimeEt,
        LocalTime executionWindowStartEt,
        LocalTime executionWindowEndEt,
        boolean executeAfterScan,
        String workspaceId,
        GapRocketConfig config
) {
    public static final LocalTime DEFAULT_SCAN_TIME_ET = LocalTime.of(9, 5);

    public GapAndGoSchedule {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        scanTimeEt = scanTimeEt == null ? DEFAULT_SCAN_TIME_ET : scanTimeEt;
        executionWindowStartEt = executionWindowStartEt == null
                ? GapRocketConfig.PRIMARY_WINDOW_START_ET : executionWindowStartEt;
        executionWindowEndEt = executionWindowEndEt == null
                ? GapRocketConfig.PRIMARY_WINDOW_END_ET : executionWindowEndEt;
        workspaceId = workspaceId == null ? "" : workspaceId;
        config = config == null ? GapRocketConfig.defaults(null) : config;
    }

    /** Build an enabled schedule with the standard premarket window for the given config/workspace. */
    public static GapAndGoSchedule create(String workspaceId, GapRocketConfig config, boolean executeAfterScan) {
        return new GapAndGoSchedule(null, true, DEFAULT_SCAN_TIME_ET,
                GapRocketConfig.PRIMARY_WINDOW_START_ET, GapRocketConfig.PRIMARY_WINDOW_END_ET,
                executeAfterScan, workspaceId, config);
    }
}
