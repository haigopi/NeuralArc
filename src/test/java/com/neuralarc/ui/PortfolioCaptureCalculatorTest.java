package com.neuralarc.ui;

import com.neuralarc.model.Position;
import com.neuralarc.model.ProfitHoldType;
import com.neuralarc.model.RecommendationType;
import com.neuralarc.model.StopLossType;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioCaptureCalculatorTest {
    private final PortfolioCaptureCalculator calculator = new PortfolioCaptureCalculator();

    @Test
    void aggregatesPortfolioProfitAmountAndPercent() {
        ManagedStrategy gain = strategy("s1", "AAPL", StrategyStatus.ACTIVE, 10, "100", "115");
        ManagedStrategy loss = strategy("s2", "MSFT", StrategyStatus.ACTIVE, 5, "200", "190");

        PortfolioCaptureSnapshot snapshot = calculator.calculate(
                List.of(gain, loss),
                config(PortfolioCaptureTargetType.PROFIT_AMOUNT, "100", true)
        );

        assertEquals(new BigDecimal("2000.00"), snapshot.totalInvestment());
        assertEquals(new BigDecimal("2100.00"), snapshot.marketValue());
        assertEquals(new BigDecimal("100.00"), snapshot.unrealizedPnl());
        assertEquals(new BigDecimal("5.00"), snapshot.profitLossPercent());
        assertEquals(2, snapshot.eligibleCount());
        assertTrue(calculator.targetReached(snapshot, config(PortfolioCaptureTargetType.PROFIT_AMOUNT, "100", true)));
    }

    @Test
    void canExcludeLosingRowsFromNetCalculation() {
        ManagedStrategy gain = strategy("s1", "AAPL", StrategyStatus.ACTIVE, 10, "100", "115");
        ManagedStrategy loss = strategy("s2", "MSFT", StrategyStatus.ACTIVE, 5, "200", "190");

        PortfolioCaptureSnapshot snapshot = calculator.calculate(
                List.of(gain, loss),
                config(PortfolioCaptureTargetType.PROFIT_AMOUNT, "100", false)
        );

        assertEquals(new BigDecimal("1000.00"), snapshot.totalInvestment());
        assertEquals(new BigDecimal("1150.00"), snapshot.marketValue());
        assertEquals(new BigDecimal("150.00"), snapshot.unrealizedPnl());
        assertEquals(1, snapshot.eligibleCount());
    }

    @Test
    void skipsInactiveAndInvalidRows() {
        ManagedStrategy active = strategy("s1", "AAPL", StrategyStatus.ACTIVE, 10, "100", "115");
        ManagedStrategy paused = strategy("s2", "MSFT", StrategyStatus.PAUSED, 5, "200", "220");
        ManagedStrategy missingPrice = strategy("s3", "NVDA", StrategyStatus.ACTIVE, 3, "100", "0");

        PortfolioCaptureSnapshot snapshot = calculator.calculate(
                List.of(active, paused, missingPrice),
                config(PortfolioCaptureTargetType.PROFIT_PERCENT, "5", true)
        );

        assertEquals(1, snapshot.eligibleCount());
        assertEquals("AAPL", snapshot.rows().getFirst().symbol());
        assertTrue(calculator.targetReached(snapshot, config(PortfolioCaptureTargetType.PROFIT_PERCENT, "5", true)));
        assertFalse(calculator.targetReached(snapshot, config(PortfolioCaptureTargetType.PROFIT_PERCENT, "25", true)));
    }

    private PortfolioCaptureConfig config(PortfolioCaptureTargetType targetType, String target, boolean includeLosses) {
        return new PortfolioCaptureConfig(
                PortfolioCaptureMode.TARGET_MONITORING,
                targetType,
                new BigDecimal(target),
                includeLosses,
                1,
                true,
                true,
                PortfolioCaptureExecutionFlow.EXECUTE_ONCE_AND_STOP,
                StrategyMode.PAPER,
                1,
                RecommendationType.SHORT_TERM,
                PortfolioCaptureLuckyStrategy.VOLATILE,
                false
        );
    }

    private ManagedStrategy strategy(
            String id,
            String symbol,
            StrategyStatus status,
            int quantity,
            String averageCost,
            String lastPrice
    ) {
        Strategy strategy = new Strategy(
                id,
                symbol + " Strategy",
                symbol,
                StrategyMode.PAPER,
                status,
                StrategyLifecycleState.BASE_BUY_FILLED,
                new BigDecimal(averageCost),
                quantity,
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
                quantity,
                new BigDecimal(averageCost).multiply(BigDecimal.valueOf(quantity)),
                1,
                Instant.now(),
                Instant.now()
        );
        Position position = new Position(symbol);
        if (quantity > 0) {
            position.applyBuy(quantity, new BigDecimal(averageCost));
        }
        position.setLastPrice(new BigDecimal(lastPrice));
        ManagedStrategy managed = new ManagedStrategy(strategy);
        managed.setCachedPosition(position);
        return managed;
    }
}
