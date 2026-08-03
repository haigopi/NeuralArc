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
        BigDecimal pnl = Monetary.zero();

        for (ManagedStrategy entry : strategies) {
            if (entry == null || entry.strategy == null) {
                continue;
            }
            pnl = pnl.add(safe(realizedPnlByStrategyId.apply(entry.strategy.id())));
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
            pnl = pnl.add(rowPnl);
        }

        investment = Monetary.round(investment);
        marketValue = Monetary.round(marketValue);
        pnl = Monetary.round(pnl);
        BigDecimal pnlPercent = Monetary.round(PortfolioCaptureSnapshot.percent(pnl, investment));
        BigDecimal progress = targetProgress(pnl, pnlPercent, config);
        return new PortfolioCaptureSnapshot(
                investment,
                marketValue,
                pnl,
                pnlPercent,
                progress,
                rows.size(),
                List.copyOf(rows),
                Instant.now()
        );
    }

    boolean targetReached(PortfolioCaptureSnapshot snapshot, PortfolioCaptureConfig config) {
        if (snapshot == null || config == null || config.targetValue() == null) {
            return false;
        }
        if (config.targetType() == PortfolioCaptureTargetType.PROFIT_PERCENT) {
            return snapshot.profitLossPercent().compareTo(config.targetValue()) >= 0;
        }
        return snapshot.unrealizedPnl().compareTo(config.targetValue()) >= 0;
    }

    private BigDecimal targetProgress(BigDecimal pnl, BigDecimal pnlPercent, PortfolioCaptureConfig config) {
        if (config == null || config.mode() == PortfolioCaptureMode.CAPTURE_NOW
                || config.targetValue() == null || config.targetValue().compareTo(BigDecimal.ZERO) <= 0) {
            return Monetary.zero();
        }
        BigDecimal current = config.targetType() == PortfolioCaptureTargetType.PROFIT_PERCENT ? pnlPercent : pnl;
        BigDecimal progress = PortfolioCaptureSnapshot.percent(current, config.targetValue());
        if (progress.compareTo(BigDecimal.ZERO) < 0) {
            return Monetary.zero();
        }
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
