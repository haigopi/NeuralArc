package com.neuralarc.ui;

import com.neuralarc.model.Position;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyConfig;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StrategyPnlTotalsCalculatorTest {
    private final StrategyPnlTotalsCalculator calculator = new StrategyPnlTotalsCalculator();

    @Test
    void totalsFollowGridRowSemanticsForUnrealizedAndRealized() {
        ManagedStrategy paperOpen = managed("AAPL", StrategyMode.PAPER, StrategyStatus.ACTIVE);
        paperOpen.setCachedPosition(openPosition("AAPL", "100.00", "105.00", 10));

        ManagedStrategy paperClosed = managed("MSFT", StrategyMode.PAPER, StrategyStatus.COMPLETED);
        paperClosed.setCachedPosition(new Position("MSFT"));

        ManagedStrategy liveOpen = managed("TSLA", StrategyMode.LIVE, StrategyStatus.ACTIVE);
        liveOpen.setCachedPosition(openPosition("TSLA", "200.00", "198.00", 5));

        ManagedStrategy liveClosed = managed("NVDA", StrategyMode.LIVE, StrategyStatus.COMPLETED);
        liveClosed.setCachedPosition(new Position("NVDA"));

        Map<String, BigDecimal> realizedByStrategyId = Map.of(
                paperClosed.strategy.id(), new BigDecimal("25.50"),
                liveClosed.strategy.id(), new BigDecimal("-10.25"),
                paperOpen.strategy.id(), new BigDecimal("999.99")
        );

        StrategyPnlTotalsCalculator.Totals totals = calculator.calculate(
                List.of(paperOpen, paperClosed, liveOpen, liveClosed),
                entry -> true,
                id -> realizedByStrategyId.getOrDefault(id, BigDecimal.ZERO)
        );

        assertEquals(new BigDecimal("50.00"), totals.paperUnrealized());
        assertEquals(new BigDecimal("25.50"), totals.paperRealized());
        assertEquals(new BigDecimal("-10.00"), totals.liveUnrealized());
        assertEquals(new BigDecimal("-10.25"), totals.liveRealized());
    }

    @Test
    void totalsRespectVisibleRowFilterPredicate() {
        ManagedStrategy visible = managed("META", StrategyMode.PAPER, StrategyStatus.ACTIVE);
        visible.setCachedPosition(openPosition("META", "90.00", "100.00", 4));

        ManagedStrategy hidden = managed("HIDE", StrategyMode.PAPER, StrategyStatus.ACTIVE);
        hidden.setCachedPosition(openPosition("HIDE", "50.00", "80.00", 2));

        StrategyPnlTotalsCalculator.Totals totals = calculator.calculate(
                List.of(visible, hidden),
                entry -> !"HIDE".equals(entry.strategy.symbol()),
                id -> BigDecimal.ZERO
        );

        assertEquals(new BigDecimal("40.00"), totals.paperUnrealized());
        assertEquals(new BigDecimal("0.00"), totals.paperRealized());
        assertEquals(new BigDecimal("0.00"), totals.liveUnrealized());
        assertEquals(new BigDecimal("0.00"), totals.liveRealized());
    }

    private ManagedStrategy managed(String symbol, StrategyMode mode, StrategyStatus status) {
        Strategy strategy = Strategy.fromConfig(
                UUID.randomUUID().toString(),
                symbol + " Strategy",
                new StrategyConfig(
                        symbol,
                        new BigDecimal("100.00"),
                        10,
                        new BigDecimal("95.00"),
                        new BigDecimal("110.00"),
                        new BigDecimal("94.00"),
                        5,
                        new BigDecimal("92.00"),
                        5,
                        30,
                        mode == StrategyMode.PAPER,
                        false
                ),
                mode
        );
        strategy.setStatus(status);
        return new ManagedStrategy(strategy);
    }

    private Position openPosition(String symbol, String averageCost, String lastPrice, int shares) {
        Position position = new Position(symbol);
        position.applyBuy(shares, new BigDecimal(averageCost));
        position.setLastPrice(new BigDecimal(lastPrice));
        return position;
    }
}

