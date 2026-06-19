package com.neuralarc.model;

import com.neuralarc.vwap.VwapConfig;

import java.time.LocalTime;
import java.util.UUID;

/**
 * A persisted autonomous VWAP Desk schedule. The scan runs during the regular session (default
 * 10:00 ET — after the opening volatility settles and a meaningful VWAP has formed) and re-scans on a
 * cadence through the execution window looking for fresh discounts below VWAP. NeuralArc must be
 * running at the scheduled time (local desktop console, no cloud cron).
 */
public record VwapSchedule(
        String id,
        boolean enabled,
        LocalTime scanTimeEt,
        LocalTime executionWindowStartEt,
        LocalTime executionWindowEndEt,
        boolean executeAfterScan,
        String workspaceId,
        VwapConfig config
) {
    public static final LocalTime DEFAULT_SCAN_TIME_ET = LocalTime.of(10, 0);

    public VwapSchedule {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        scanTimeEt = scanTimeEt == null ? DEFAULT_SCAN_TIME_ET : scanTimeEt;
        executionWindowStartEt = executionWindowStartEt == null
                ? VwapConfig.PRIMARY_WINDOW_START_ET : executionWindowStartEt;
        executionWindowEndEt = executionWindowEndEt == null
                ? VwapConfig.PRIMARY_WINDOW_END_ET : executionWindowEndEt;
        workspaceId = workspaceId == null ? "" : workspaceId;
        config = config == null ? VwapConfig.defaults(null) : config;
    }

    /** Build an enabled schedule with the standard regular-session window for the given config/workspace. */
    public static VwapSchedule create(String workspaceId, VwapConfig config, boolean executeAfterScan) {
        return new VwapSchedule(null, true, DEFAULT_SCAN_TIME_ET,
                VwapConfig.PRIMARY_WINDOW_START_ET, VwapConfig.PRIMARY_WINDOW_END_ET,
                executeAfterScan, workspaceId, config);
    }
}
