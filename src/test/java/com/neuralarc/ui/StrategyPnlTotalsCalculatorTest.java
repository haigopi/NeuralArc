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
    private final PortfolioCaptureCalculator captureCalculator = new PortfolioCaptureCalculator();

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

    @Test
    void headerUnrealizedPnlMatchesLiquidationSnapshotForSameOpenRows() {
        ManagedStrategy gain = managed("AAPL", StrategyMode.PAPER, StrategyStatus.ACTIVE);
        gain.setCachedPosition(openPosition("AAPL", "100.00", "115.00", 10));
        ManagedStrategy loss = managed("MSFT", StrategyMode.PAPER, StrategyStatus.ACTIVE);
        loss.setCachedPosition(openPosition("MSFT", "200.00", "190.00", 5));
        ManagedStrategy closed = managed("CLOSED", StrategyMode.PAPER, StrategyStatus.COMPLETED);
        closed.setCachedPosition(new Position("CLOSED"));

        List<ManagedStrategy> visibleRows = List.of(gain, loss, closed);
        StrategyPnlTotalsCalculator.Totals totals = calculator.calculate(
                visibleRows,
                entry -> true,
                id -> new BigDecimal("99.00")
        );
        PortfolioCaptureSnapshot snapshot = captureCalculator.calculate(
                visibleRows,
                config(true)
        );

        assertEquals(totals.paperUnrealized(), snapshot.unrealizedPnl());
        assertEquals(new BigDecimal("100.00"), snapshot.unrealizedPnl());
    }

    @Test
    void invalidOpenRowsAreSkippedByHeaderAndLiquidationMath() {
        ManagedStrategy invalid = managed("BAD", StrategyMode.PAPER, StrategyStatus.ACTIVE);
        Position position = new Position("BAD");
        position.applyBuy(5, new BigDecimal("100.00"));
        position.setLastPrice(BigDecimal.ZERO);
        invalid.setCachedPosition(position);

        StrategyPnlTotalsCalculator.Totals totals = calculator.calculate(
                List.of(invalid),
                entry -> true,
                id -> BigDecimal.ZERO
        );
        PortfolioCaptureSnapshot snapshot = captureCalculator.calculate(
                List.of(invalid),
                config(true)
        );

        assertEquals(new BigDecimal("0.00"), totals.paperUnrealized());
        assertEquals(new BigDecimal("0.00"), snapshot.unrealizedPnl());
        assertEquals(0, snapshot.eligibleCount());
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

    private PortfolioCaptureConfig config(boolean includeLosses) {
        return new PortfolioCaptureConfig(
                PortfolioCaptureMode.TARGET_MONITORING,
                PortfolioCaptureTargetType.PROFIT_AMOUNT,
                new BigDecimal("100.00"),
                includeLosses,
                1,
                true,
                true,
                PortfolioCaptureExecutionFlow.EXECUTE_ONCE_AND_STOP,
                StrategyMode.PAPER,
                1,
                com.neuralarc.model.RecommendationType.SHORT_TERM,
                PortfolioCaptureLuckyStrategy.VOLATILE,
                false
        );
    }
}
