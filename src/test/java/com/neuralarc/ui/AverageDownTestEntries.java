package com.neuralarc.ui;

import com.neuralarc.model.Position;
import com.neuralarc.model.ProfitHoldType;
import com.neuralarc.model.StopLossType;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** Builds strategy rows holding a cached position, for the average-down tests. */
final class AverageDownTestEntries {
    private AverageDownTestEntries() {
    }

    /** An active row holding {@code shares} bought at {@code averageCost}, now priced at {@code lastPrice}. */
    static ManagedStrategy position(String symbol, int shares, String averageCost, String lastPrice) {
        ManagedStrategy entry = new ManagedStrategy(strategy(symbol));
        Position position = new Position(symbol);
        if (shares > 0) {
            position.applyBuy(shares, new BigDecimal(averageCost));
        }
        position.setLastPrice(new BigDecimal(lastPrice));
        entry.setCachedPosition(position);
        return entry;
    }

    static ManagedStrategy withState(ManagedStrategy entry, StrategyStatus status, StrategyLifecycleState state) {
        entry.strategy.setStatus(status);
        entry.strategy.setCurrentState(state);
        return entry;
    }

    static ManagedStrategy withWorkingSell(ManagedStrategy entry) {
        withState(entry, StrategyStatus.ACTIVE, StrategyLifecycleState.SELL_PLACED);
        entry.strategy.setLatestOrderStatus("new");
        return entry;
    }

    /** A position whose working exit is the strategy's own target sell, as the grid snapshot records it. */
    static ManagedStrategy withWorkingTargetSell(ManagedStrategy entry) {
        withWorkingSell(entry);
        entry.setTradeSnapshot(BigDecimal.ZERO, BigDecimal.ZERO, null,
                new StrategyTablePresenter.PendingOrderSummary(new BigDecimal("12.00"), BigDecimal.ONE, false, true));
        return entry;
    }

    static ManagedStrategy inWorkspace(ManagedStrategy entry, String workspaceId) {
        entry.strategy.setWorkspaceId(workspaceId);
        return entry;
    }

    private static Strategy strategy(String symbol) {
        return new Strategy(
                symbol + "-id",
                symbol + " Strategy",
                symbol,
                StrategyMode.PAPER,
                StrategyStatus.ACTIVE,
                StrategyLifecycleState.BUY_LIMIT_1_FILLED,
                new BigDecimal("8.00"),
                10,
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                0,
                false,
                StopLossType.FIXED_PRICE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                BigDecimal.ZERO,
                false,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                false,
                ProfitHoldType.PERCENT_TRAILING,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                25,
                new BigDecimal("300.00"),
                2,
                Instant.now(),
                Instant.now()
        );
    }
}
