package com.neuralarc.ui;

import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyStatus;

import java.util.List;

final class BrokerSnapshotRefreshPolicy {
    static final long FALLBACK_INTERVAL_MILLIS = 5_000L;
    static final long MINIMUM_FLOOR_INTERVAL_MILLIS = 2_000L;

    private BrokerSnapshotRefreshPolicy() {
    }

    static boolean eligibleForBrokerSnapshot(Strategy strategy) {
        return strategy != null
                && strategy.status() == StrategyStatus.ACTIVE
                && strategy.symbol() != null
                && !strategy.symbol().isBlank();
    }

    static long resolveIntervalMillis(List<Strategy> stored) {
        if (stored == null || stored.isEmpty()) {
            return FALLBACK_INTERVAL_MILLIS;
        }
        long minimum = Long.MAX_VALUE;
        for (Strategy strategy : stored) {
            if (!eligibleForBrokerSnapshot(strategy)) {
                continue;
            }
            long strategyMillis = Math.max(1L, strategy.pollingIntervalSeconds()) * 1_000L;
            if (strategyMillis < minimum) {
                minimum = strategyMillis;
            }
        }
        if (minimum == Long.MAX_VALUE) {
            return FALLBACK_INTERVAL_MILLIS;
        }
        return Math.max(MINIMUM_FLOOR_INTERVAL_MILLIS, minimum);
    }
}

