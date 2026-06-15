package com.neuralarc.ui;

import com.neuralarc.model.StrategyMode;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

final class StrategyPnlTotalsCalculator {
    private final StrategyOpenPnlCalculator openPnlCalculator;

    StrategyPnlTotalsCalculator() {
        this(new StrategyOpenPnlCalculator());
    }

    StrategyPnlTotalsCalculator(StrategyOpenPnlCalculator openPnlCalculator) {
        this.openPnlCalculator = openPnlCalculator;
    }

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
            boolean hasShares = entry.cachedPosition().getTotalShares() > 0;
            java.util.Optional<StrategyOpenPnlCalculator.Row> openRow = openPnlCalculator.openRow(entry);
            if (hasShares && openRow.isEmpty()) {
                continue;
            }
            BigDecimal unrealized = openRow.map(StrategyOpenPnlCalculator.Row::unrealizedPnl).orElse(BigDecimal.ZERO);
            BigDecimal realized = safe(realizedPnlByStrategyId.apply(entry.strategy.id()));

            if (entry.strategy.mode() == StrategyMode.PAPER) {
                paperUnrealized = paperUnrealized.add(unrealized);
                paperRealized = paperRealized.add(realized);
            } else {
                liveUnrealized = liveUnrealized.add(unrealized);
                liveRealized = liveRealized.add(realized);
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
        BigDecimal paperTotal() {
            return Monetary.round(paperUnrealized.add(paperRealized));
        }

        BigDecimal liveTotal() {
            return Monetary.round(liveUnrealized.add(liveRealized));
        }

        Totals {
            paperUnrealized = Monetary.round(paperUnrealized == null ? BigDecimal.ZERO : paperUnrealized);
            paperRealized = Monetary.round(paperRealized == null ? BigDecimal.ZERO : paperRealized);
            liveUnrealized = Monetary.round(liveUnrealized == null ? BigDecimal.ZERO : liveUnrealized);
            liveRealized = Monetary.round(liveRealized == null ? BigDecimal.ZERO : liveRealized);
        }
    }
}
