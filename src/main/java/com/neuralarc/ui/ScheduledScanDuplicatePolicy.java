package com.neuralarc.ui;

import com.neuralarc.model.Strategy;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;

final class ScheduledScanDuplicatePolicy {
    private ScheduledScanDuplicatePolicy() {
    }

    static boolean samePlannedPrices(
            Strategy strategy,
            BigDecimal plannedEntry,
            BigDecimal stopLoss,
            BigDecimal target
    ) {
        if (strategy == null) {
            return false;
        }
        return same(strategy.baseBuyLimitPrice(), plannedEntry)
                && same(strategy.stopLossPrice(), stopLoss)
                && same(strategy.targetSellPrice(), target);
    }

    private static boolean same(BigDecimal left, BigDecimal right) {
        return Monetary.round(left).compareTo(Monetary.round(right)) == 0;
    }
}
