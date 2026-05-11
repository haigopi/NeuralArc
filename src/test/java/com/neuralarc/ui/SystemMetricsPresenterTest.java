package com.neuralarc.ui;

import com.neuralarc.model.Position;
import com.neuralarc.model.ProfitHoldType;
import com.neuralarc.model.StopLossType;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SystemMetricsPresenterTest {
    @Test
    void marketValueExcludesCompletedStrategies() {
        ManagedStrategy active = managed("AAPL", StrategyStatus.ACTIVE, 2, new BigDecimal("100.00"));
        ManagedStrategy completed = managed("MSFT", StrategyStatus.COMPLETED, 3, new BigDecimal("200.00"));

        String text = new SystemMetricsPresenter().formatMarketValueText(List.of(active, completed));

        assertEquals("Market Value: 200.00", text);
    }

    @Test
    void investedValueExcludesCompletedStrategies() {
        ManagedStrategy active = managed("AAPL", StrategyStatus.ACTIVE, 2, new BigDecimal("100.00"));
        ManagedStrategy completed = managed("MSFT", StrategyStatus.COMPLETED, 3, new BigDecimal("200.00"));

        String text = new SystemMetricsPresenter().formatInvestedValueText(List.of(active, completed));

        assertEquals("Invested Value: 200.00", text);
    }

    private static ManagedStrategy managed(String symbol, StrategyStatus status, int shares, BigDecimal price) {
        ManagedStrategy managed = new ManagedStrategy(strategy(symbol, status));
        Position position = new Position(symbol);
        position.applyBuy(shares, price);
        position.setLastPrice(price);
        managed.setCachedPosition(position);
        return managed;
    }

    private static Strategy strategy(String symbol, StrategyStatus status) {
        return new Strategy(
                UUID.randomUUID().toString(),
                symbol + " Strategy",
                symbol,
                StrategyMode.PAPER,
                status,
                StrategyLifecycleState.CREATED,
                new BigDecimal("100.00"),
                1,
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
                true,
                new BigDecimal("120.00"),
                BigDecimal.ONE,
                false,
                false,
                ProfitHoldType.PERCENT_TRAILING,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                10,
                new BigDecimal("1000.00"),
                30,
                Instant.now(),
                Instant.now()
        );
    }
}
