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

class StrategyGridTableModelTest {
    @Test
    void scannerRecommendationRowsShowCachedStockPriceWithoutBrokerShares() {
        Strategy strategy = strategy("NVDA");
        strategy.setLatestOrderStatus("EARNINGS_HUNTER_RECOMMENDED");
        ManagedStrategy managed = new ManagedStrategy(strategy);
        Position cached = new Position("NVDA");
        cached.setLastPrice(new BigDecimal("182.45"));
        managed.setCachedPosition(cached);
        StrategyGridTableModel model = new StrategyGridTableModel(
                List.of(managed), ignored -> "Base buy pending", new StrategyTablePresenter());

        assertEquals("182.45", model.getValueAt(0, 3));
    }

    private static Strategy strategy(String symbol) {
        return new Strategy(
                UUID.randomUUID().toString(),
                symbol + " Strategy",
                symbol,
                StrategyMode.PAPER,
                StrategyStatus.CREATED,
                StrategyLifecycleState.CREATED,
                new BigDecimal("180.00"),
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
                new BigDecimal("198.00"),
                BigDecimal.valueOf(1000),
                false,
                false,
                ProfitHoldType.PERCENT_TRAILING,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                10,
                new BigDecimal("180.00"),
                60,
                Instant.now(),
                Instant.now()
        );
    }
}
