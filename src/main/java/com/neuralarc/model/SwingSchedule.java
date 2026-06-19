package com.neuralarc.model;

import com.neuralarc.swing.SwingConfig;

import java.time.LocalTime;
import java.util.UUID;

/**
 * A persisted autonomous Swing Vault schedule. Swing Vault works on daily bars, so the scan runs once
 * per trading day shortly after the open (default 9:45 ET — after the prior close and the opening
 * context are available) looking for fresh pullback-to-support setups in strong up-trending names.
 * NeuralArc must be running at the scheduled time (local desktop console, no cloud cron).
 */
public record SwingSchedule(
        String id,
        boolean enabled,
        LocalTime scanTimeEt,
        LocalTime executionWindowStartEt,
        LocalTime executionWindowEndEt,
        boolean executeAfterScan,
        String workspaceId,
        SwingConfig config
) {
    public static final LocalTime DEFAULT_SCAN_TIME_ET = LocalTime.of(9, 45);

    public SwingSchedule {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        scanTimeEt = scanTimeEt == null ? DEFAULT_SCAN_TIME_ET : scanTimeEt;
        executionWindowStartEt = executionWindowStartEt == null
                ? SwingConfig.PRIMARY_WINDOW_START_ET : executionWindowStartEt;
        executionWindowEndEt = executionWindowEndEt == null
                ? SwingConfig.PRIMARY_WINDOW_END_ET : executionWindowEndEt;
        workspaceId = workspaceId == null ? "" : workspaceId;
        config = config == null ? SwingConfig.defaults(null) : config;
    }

    /** Build an enabled schedule with the standard regular-session window for the given config/workspace. */
    public static SwingSchedule create(String workspaceId, SwingConfig config, boolean executeAfterScan) {
        return new SwingSchedule(null, true, DEFAULT_SCAN_TIME_ET,
                SwingConfig.PRIMARY_WINDOW_START_ET, SwingConfig.PRIMARY_WINDOW_END_ET,
                executeAfterScan, workspaceId, config);
    }
}
