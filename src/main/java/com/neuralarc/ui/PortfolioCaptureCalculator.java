package com.neuralarc.ui;

import com.neuralarc.model.StrategyStatus;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

final class PortfolioCaptureCalculator {
    private final StrategyOpenPnlCalculator openPnlCalculator;

    PortfolioCaptureCalculator() {
        this(new StrategyOpenPnlCalculator());
    }

    PortfolioCaptureCalculator(StrategyOpenPnlCalculator openPnlCalculator) {
        this.openPnlCalculator = openPnlCalculator;
    }

    PortfolioCaptureSnapshot calculate(List<ManagedStrategy> strategies, PortfolioCaptureConfig config) {
        return calculate(strategies, config, id -> BigDecimal.ZERO);
    }

    PortfolioCaptureSnapshot calculate(
            List<ManagedStrategy> strategies,
            PortfolioCaptureConfig config,
            Function<String, BigDecimal> realizedPnlByStrategyId
    ) {
        if (strategies == null || strategies.isEmpty()) {
            return PortfolioCaptureSnapshot.empty();
        }
        boolean includeLosses = config == null || config.includeLosses();
        List<PortfolioCaptureSnapshot.Row> rows = new ArrayList<>();
        BigDecimal investment = Monetary.zero();
        BigDecimal marketValue = Monetary.zero();
        // Banked and open P&L are accumulated separately: realized profit from already-closed trades
        // is reported but is NOT capturable, so it must never contribute to the target basis.
        BigDecimal realized = Monetary.zero();
        BigDecimal unrealized = Monetary.zero();

        for (ManagedStrategy entry : strategies) {
            if (entry == null || entry.strategy == null) {
                continue;
            }
            realized = realized.add(safe(realizedPnlByStrategyId.apply(entry.strategy.id())));
            if (!eligible(entry, config)) {
                continue;
            }
            StrategyOpenPnlCalculator.Row pnlRow = openPnlCalculator.openRow(entry).orElse(null);
            if (pnlRow == null) {
                continue;
            }
            BigDecimal rowPnl = pnlRow.unrealizedPnl();
            if (!includeLosses && rowPnl.compareTo(BigDecimal.ZERO) < 0) {
                continue;
            }
            rows.add(new PortfolioCaptureSnapshot.Row(
                    pnlRow.strategyId(),
                    pnlRow.symbol(),
                    pnlRow.shares(),
                    pnlRow.averageCost(),
                    pnlRow.lastPrice(),
                    pnlRow.investment(),
                    pnlRow.marketValue(),
                    rowPnl
            ));
            investment = investment.add(pnlRow.investment());
            marketValue = marketValue.add(pnlRow.marketValue());
            unrealized = unrealized.add(rowPnl);
        }

        investment = Monetary.round(investment);
        marketValue = Monetary.round(marketValue);
        realized = Monetary.round(realized);
        unrealized = Monetary.round(unrealized);
        BigDecimal pnlPercent = Monetary.round(PortfolioCaptureSnapshot.percent(unrealized, investment));
        BigDecimal progress = targetProgress(unrealized, pnlPercent, config);
        return new PortfolioCaptureSnapshot(
                investment,
                marketValue,
                realized,
                unrealized,
                pnlPercent,
                progress,
                rows.size(),
                List.copyOf(rows),
                Instant.now()
        );
    }

    /**
     * Whether the configured profit target is actually met by the P&L a liquidation would realize
     * right now. Every precondition is checked explicitly because this is the gate that sells real
     * positions: there must be a positive target, at least one row to sell, and the open P&L of those
     * rows must itself be in profit. Banked realized P&L is deliberately excluded — it cannot be
     * captured a second time, and counting it once allowed a losing portfolio to trip the target.
     */
    boolean targetReached(PortfolioCaptureSnapshot snapshot, PortfolioCaptureConfig config) {
        if (snapshot == null || config == null || config.mode() == PortfolioCaptureMode.CAPTURE_NOW) {
            return false;
        }
        BigDecimal target = config.targetValue();
        if (target == null || target.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        if (snapshot.eligibleCount() <= 0 || snapshot.rows().isEmpty()) {
            return false;
        }
        BigDecimal capturable = config.targetType() == PortfolioCaptureTargetType.PROFIT_PERCENT
                ? snapshot.profitLossPercent()
                : snapshot.unrealizedPnl();
        if (capturable.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        return capturable.compareTo(target) >= 0;
    }

    private BigDecimal targetProgress(BigDecimal pnl, BigDecimal pnlPercent, PortfolioCaptureConfig config) {
        if (config == null || config.mode() == PortfolioCaptureMode.CAPTURE_NOW
                || config.targetValue() == null || config.targetValue().compareTo(BigDecimal.ZERO) <= 0) {
            return Monetary.zero();
        }
        BigDecimal current = config.targetType() == PortfolioCaptureTargetType.PROFIT_PERCENT ? pnlPercent : pnl;
        BigDecimal progress = PortfolioCaptureSnapshot.percent(current, config.targetValue());
        return Monetary.round(progress.min(BigDecimal.valueOf(100)));
    }

    private BigDecimal safe(BigDecimal value) {
        return Monetary.round(value == null ? BigDecimal.ZERO : value);
    }

    private boolean eligible(ManagedStrategy entry, PortfolioCaptureConfig config) {
        if (entry == null || entry.strategy == null) {
            return false;
        }
        if (entry.strategy.status() != StrategyStatus.ACTIVE) {
            return false;
        }
        if (config != null && config.includeOnlyActiveStrategies() && entry.isPaused()) {
            return false;
        }
        return openPnlCalculator.openRow(entry).isPresent();
    }
}
