package com.neuralarc.service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Relaxes polling cadence for a strategy whose observed price/position hasn't changed across
 * recent poll cycles, and snaps back to full speed the instant anything changes. State is
 * in-memory/session-only — there is no product value in remembering "unchanged for N cycles"
 * across an app restart, and a fresh session starting at full speed is the safe default.
 */
final class AdaptivePollingPacer {
    private static final int RELAXED_TIER_MIN_CYCLES = 3;
    private static final int MAX_TIER_MIN_CYCLES = 10;

    private final Map<String, ObservedState> observedByStrategyId = new ConcurrentHashMap<>();

    void recordObservation(String strategyId, BigDecimal price, BigDecimal quantity, BigDecimal avgEntryPrice) {
        if (strategyId == null || strategyId.isBlank()) {
            return;
        }
        ObservedState previous = observedByStrategyId.get(strategyId);
        if (previous != null && previous.matches(price, quantity, avgEntryPrice)) {
            observedByStrategyId.put(strategyId, previous.withIncrementedUnchangedCycles());
        } else {
            observedByStrategyId.put(strategyId, new ObservedState(price, quantity, avgEntryPrice, 0));
        }
    }

    /** 1x below RELAXED_TIER_MIN_CYCLES unchanged cycles, then 2x, then maxMultiplier at/above MAX_TIER_MIN_CYCLES. */
    long pacingMultiplier(String strategyId, long maxMultiplier) {
        long safeMax = Math.max(1L, maxMultiplier);
        ObservedState state = observedByStrategyId.get(strategyId);
        if (state == null || state.unchangedCycles() < RELAXED_TIER_MIN_CYCLES) {
            return 1L;
        }
        if (state.unchangedCycles() < MAX_TIER_MIN_CYCLES) {
            return Math.min(2L, safeMax);
        }
        return safeMax;
    }

    void reset(String strategyId) {
        observedByStrategyId.remove(strategyId);
    }

    private record ObservedState(BigDecimal price, BigDecimal quantity, BigDecimal avgEntryPrice, int unchangedCycles) {
        boolean matches(BigDecimal otherPrice, BigDecimal otherQuantity, BigDecimal otherAvgEntryPrice) {
            return sameValue(price, otherPrice) && sameValue(quantity, otherQuantity) && sameValue(avgEntryPrice, otherAvgEntryPrice);
        }

        ObservedState withIncrementedUnchangedCycles() {
            return new ObservedState(price, quantity, avgEntryPrice, unchangedCycles + 1);
        }

        private static boolean sameValue(BigDecimal a, BigDecimal b) {
            if (a == null || b == null) {
                return a == b;
            }
            return a.compareTo(b) == 0;
        }
    }
}
