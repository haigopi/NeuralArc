package com.neuralarc.ui;

import com.neuralarc.model.StrategyMode;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

final class StrategyPnlTotalsCalculator {
    Totals calculate(
            List<ManagedStrategy> strategies,
            Predicate<ManagedStrategy> includeInTotals,
            Function<String, BigDecimal> realizedPnlByStrategyId
    ) {
        BigDecimal paperUnrealized = BigDecimal.ZERO;
        BigDecimal paperRealized = BigDecimal.ZERO;
        BigDecimal liveUnrealized = BigDecimal.ZERO;
        BigDecimal liveRealized = BigDecimal.ZERO;

        if (strategies == null || strategies.isEmpty()) {
            return new Totals(paperUnrealized, paperRealized, liveUnrealized, liveRealized);
        }

        for (ManagedStrategy entry : strategies) {
            if (entry == null || entry.strategy == null || !includeInTotals.test(entry)) {
                continue;
            }
            boolean openPosition = entry.cachedPosition().getTotalShares() > 0;
            BigDecimal pnlValue = openPosition
                    ? entry.cachedPosition().unrealizedPnl()
                    : safe(realizedPnlByStrategyId.apply(entry.strategy.id()));

            if (entry.strategy.mode() == StrategyMode.PAPER) {
                if (openPosition) {
                    paperUnrealized = paperUnrealized.add(pnlValue);
                } else {
                    paperRealized = paperRealized.add(pnlValue);
                }
            } else {
                if (openPosition) {
                    liveUnrealized = liveUnrealized.add(pnlValue);
                } else {
                    liveRealized = liveRealized.add(pnlValue);
                }
            }
        }

        return new Totals(paperUnrealized, paperRealized, liveUnrealized, liveRealized);
    }

    private BigDecimal safe(BigDecimal value) {
        return Monetary.round(value == null ? BigDecimal.ZERO : value);
    }

    record Totals(
            BigDecimal paperUnrealized,
            BigDecimal paperRealized,
            BigDecimal liveUnrealized,
            BigDecimal liveRealized
    ) {
        Totals {
            paperUnrealized = Monetary.round(paperUnrealized == null ? BigDecimal.ZERO : paperUnrealized);
            paperRealized = Monetary.round(paperRealized == null ? BigDecimal.ZERO : paperRealized);
            liveUnrealized = Monetary.round(liveUnrealized == null ? BigDecimal.ZERO : liveUnrealized);
            liveRealized = Monetary.round(liveRealized == null ? BigDecimal.ZERO : liveRealized);
        }
    }
}

