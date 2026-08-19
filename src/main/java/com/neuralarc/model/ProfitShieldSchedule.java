package com.neuralarc.model;

import com.neuralarc.profitshield.ProfitShieldConfig;

import java.time.LocalTime;
import java.util.UUID;

/**
 * A persisted autonomous Profit Shield schedule. Profit Shield reads daily bars, so the scan runs once
 * per trading day shortly after the open (default 9:45 ET — after the prior close is final) looking
 * for names whose defensive profile still qualifies. NeuralArc must be running at the scheduled time
 * (local desktop console, no cloud cron).
 */
public record ProfitShieldSchedule(
        String id,
        boolean enabled,
        LocalTime scanTimeEt,
        LocalTime executionWindowStartEt,
        LocalTime executionWindowEndEt,
        boolean executeAfterScan,
        String workspaceId,
        ProfitShieldConfig config
) {
    public static final LocalTime DEFAULT_SCAN_TIME_ET = LocalTime.of(9, 45);

    public ProfitShieldSchedule {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        scanTimeEt = scanTimeEt == null ? DEFAULT_SCAN_TIME_ET : scanTimeEt;
        executionWindowStartEt = executionWindowStartEt == null
                ? ProfitShieldConfig.PRIMARY_WINDOW_START_ET : executionWindowStartEt;
        executionWindowEndEt = executionWindowEndEt == null
                ? ProfitShieldConfig.PRIMARY_WINDOW_END_ET : executionWindowEndEt;
        workspaceId = workspaceId == null ? "" : workspaceId;
        config = config == null ? ProfitShieldConfig.defaults(null) : config;
    }

    /** Build an enabled schedule with the standard regular-session window for the given config/workspace. */
    public static ProfitShieldSchedule create(String workspaceId, ProfitShieldConfig config, boolean executeAfterScan) {
        return new ProfitShieldSchedule(null, true, DEFAULT_SCAN_TIME_ET,
                ProfitShieldConfig.PRIMARY_WINDOW_START_ET, ProfitShieldConfig.PRIMARY_WINDOW_END_ET,
                executeAfterScan, workspaceId, config);
    }
}
