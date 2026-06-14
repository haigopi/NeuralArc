package com.neuralarc.ui;

import com.neuralarc.model.Position;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.util.Optional;

final class StrategyOpenPnlCalculator {
    Optional<Row> openRow(ManagedStrategy entry) {
        if (entry == null || entry.strategy == null) {
            return Optional.empty();
        }
        if (GapRocketDisplaySupport.suppressBrokerPosition(entry.strategy)) {
            return Optional.empty();
        }
        Position position = entry.cachedPosition();
        if (position.getTotalShares() <= 0
                || position.getLastPrice().compareTo(BigDecimal.ZERO) <= 0
                || position.getAverageCost().compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }
        return Optional.of(new Row(
                entry.strategy.id(),
                entry.strategy.symbol(),
                position.getTotalShares(),
                position.getAverageCost(),
                position.getLastPrice(),
                position.totalInvested(),
                position.marketValue(),
                position.unrealizedPnl()
        ));
    }

    BigDecimal unrealizedPnl(ManagedStrategy entry) {
        return openRow(entry).map(Row::unrealizedPnl).orElseGet(Monetary::zero);
    }

    record Row(
            String strategyId,
            String symbol,
            int shares,
            BigDecimal averageCost,
            BigDecimal lastPrice,
            BigDecimal investment,
            BigDecimal marketValue,
            BigDecimal unrealizedPnl
    ) {
        Row {
            strategyId = strategyId == null ? "" : strategyId;
            symbol = symbol == null ? "" : symbol;
            averageCost = Monetary.round(averageCost);
            lastPrice = Monetary.round(lastPrice);
            investment = Monetary.round(investment);
            marketValue = Monetary.round(marketValue);
            unrealizedPnl = Monetary.round(unrealizedPnl);
        }
    }
}
