package com.neuralarc.ui;

import com.neuralarc.model.Position;
import com.neuralarc.model.StrategyStatus;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class PortfolioCaptureCalculator {
    PortfolioCaptureSnapshot calculate(List<ManagedStrategy> strategies, PortfolioCaptureConfig config) {
        if (strategies == null || strategies.isEmpty()) {
            return PortfolioCaptureSnapshot.empty();
        }
        boolean includeLosses = config == null || config.includeLosses();
        List<PortfolioCaptureSnapshot.Row> rows = new ArrayList<>();
        BigDecimal investment = Monetary.zero();
        BigDecimal marketValue = Monetary.zero();
        BigDecimal pnl = Monetary.zero();

        for (ManagedStrategy entry : strategies) {
            if (!eligible(entry, config)) {
                continue;
            }
            Position position = entry.cachedPosition();
            BigDecimal rowPnl = position.unrealizedPnl();
            if (!includeLosses && rowPnl.compareTo(BigDecimal.ZERO) < 0) {
                continue;
            }
            BigDecimal rowInvestment = position.totalInvested();
            BigDecimal rowMarketValue = position.marketValue();
            rows.add(new PortfolioCaptureSnapshot.Row(
                    entry.strategy.id(),
                    entry.strategy.symbol(),
                    position.getTotalShares(),
                    position.getAverageCost(),
                    position.getLastPrice(),
                    rowInvestment,
                    rowMarketValue,
                    rowPnl
            ));
            investment = investment.add(rowInvestment);
            marketValue = marketValue.add(rowMarketValue);
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
        if (config == null || config.mode() != PortfolioCaptureMode.TARGET_MONITORING
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
        Position position = entry.cachedPosition();
        return position.getTotalShares() > 0
                && position.getLastPrice().compareTo(BigDecimal.ZERO) > 0
                && position.getAverageCost().compareTo(BigDecimal.ZERO) > 0;
    }
}
