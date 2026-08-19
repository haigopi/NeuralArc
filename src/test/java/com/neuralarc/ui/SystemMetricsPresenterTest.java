package com.neuralarc.ui;

import com.neuralarc.model.Position;
import com.neuralarc.model.ProfitHoldType;
import com.neuralarc.model.StopLossType;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyOrderSide;
import com.neuralarc.model.StrategyOrderStatus;
import com.neuralarc.model.StrategyOrderType;
import com.neuralarc.model.StrategyStage;
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

    @Test
    void marketAndInvestedValuesAreScopedToSelectedMode() {
        ManagedStrategy paper = managed("AAPL", StrategyMode.PAPER, StrategyStatus.ACTIVE, 2, new BigDecimal("100.00"));
        ManagedStrategy live = managed("MSFT", StrategyMode.LIVE, StrategyStatus.ACTIVE, 3, new BigDecimal("200.00"));
        SystemMetricsPresenter presenter = new SystemMetricsPresenter();

        assertEquals("Market Value: 200.00", presenter.formatMarketValueText(List.of(paper, live), StrategyMode.PAPER));
        assertEquals("Market Value: 600.00", presenter.formatMarketValueText(List.of(paper, live), StrategyMode.LIVE));
        assertEquals("Invested Value: 200.00", presenter.formatInvestedValueText(List.of(paper, live), StrategyMode.PAPER));
        assertEquals("Invested Value: 600.00", presenter.formatInvestedValueText(List.of(paper, live), StrategyMode.LIVE));
    }

    @Test
    void marketAndInvestedValuesShowZeroWhenSelectedModeHasNoStocks() {
        ManagedStrategy paper = managed("AAPL", StrategyMode.PAPER, StrategyStatus.ACTIVE, 2, new BigDecimal("100.00"));
        SystemMetricsPresenter presenter = new SystemMetricsPresenter();

        assertEquals("Market Value: 0.00", presenter.formatMarketValueText(List.of(paper), StrategyMode.LIVE));
        assertEquals("Invested Value: 0.00", presenter.formatInvestedValueText(List.of(paper), StrategyMode.LIVE));
    }

    @Test
    void baseBuyPendingTotalIsScopedToSelectedModeAndPendingBaseBuyOrders() {
        ManagedStrategy paper = managed("AAPL", StrategyMode.PAPER, StrategyStatus.ACTIVE, 0, BigDecimal.ZERO);
        ManagedStrategy live = managed("MSFT", StrategyMode.LIVE, StrategyStatus.ACTIVE, 0, BigDecimal.ZERO);
        StrategyOrder paperPending = order(paper.strategy.id(), StrategyStage.BASE_BUY, StrategyOrderStatus.PENDING,
                new BigDecimal("10.00"), new BigDecimal("5"), BigDecimal.ZERO);
        StrategyOrder livePending = order(live.strategy.id(), StrategyStage.BASE_BUY, StrategyOrderStatus.PARTIALLY_FILLED,
                new BigDecimal("20.00"), new BigDecimal("6"), new BigDecimal("2"));
        StrategyOrder liveTargetSell = order(live.strategy.id(), StrategyStage.TARGET_SELL, StrategyOrderStatus.PENDING,
                new BigDecimal("30.00"), new BigDecimal("1"), BigDecimal.ZERO);

        SystemMetricsPresenter presenter = new SystemMetricsPresenter();

        assertEquals(
                "Base Buy Pending Total: 50.00",
                presenter.formatBaseBuyPendingTotalText(
                        List.of(paper, live),
                        id -> id.equals(paper.strategy.id()) ? List.of(paperPending) : List.of(livePending, liveTargetSell),
                        StrategyMode.PAPER
                )
        );
        assertEquals(
                "Base Buy Pending Total: 80.00",
                presenter.formatBaseBuyPendingTotalText(
                        List.of(paper, live),
                        id -> id.equals(paper.strategy.id()) ? List.of(paperPending) : List.of(livePending, liveTargetSell),
                        StrategyMode.LIVE
                )
        );
    }

    @Test
    void cpuUsageShowsAppShareOfTotalCpuCapacity() {
        SystemMetricsPresenter presenter = new SystemMetricsPresenter();

        String text = presenter.formatCpuUsageText(0.10d, 8);

        assertEquals("CPU: 10.0%", text);
    }

    @Test
    void cpuUsageFallsBackWhenCpuLoadIsUnavailable() {
        SystemMetricsPresenter presenter = new SystemMetricsPresenter();

        String text = presenter.formatCpuUsageText(-1.0d, 8);

        assertEquals("CPU: -", text);
    }

    private static ManagedStrategy managed(String symbol, StrategyStatus status, int shares, BigDecimal price) {
        return managed(symbol, StrategyMode.PAPER, status, shares, price);
    }

    private static ManagedStrategy managed(String symbol, StrategyMode mode, StrategyStatus status, int shares, BigDecimal price) {
        ManagedStrategy managed = new ManagedStrategy(strategy(symbol, mode, status));
        Position position = new Position(symbol);
        if (shares > 0) {
            position.applyBuy(shares, price);
            position.setLastPrice(price);
        }
        managed.setCachedPosition(position);
        return managed;
    }

    private static Strategy strategy(String symbol, StrategyStatus status) {
        return strategy(symbol, StrategyMode.PAPER, status);
    }

    private static Strategy strategy(String symbol, StrategyMode mode, StrategyStatus status) {
        return new Strategy(
                UUID.randomUUID().toString(),
                symbol + " Strategy",
                symbol,
                mode,
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

    private static StrategyOrder order(
            String strategyId,
            StrategyStage stage,
            StrategyOrderStatus status,
            BigDecimal limitPrice,
            BigDecimal requestedQuantity,
            BigDecimal filledQuantity
    ) {
        return new StrategyOrder(
                UUID.randomUUID().toString(),
                strategyId,
                stage,
                "alpaca-" + UUID.randomUUID(),
                "client-" + UUID.randomUUID(),
                "AAPL",
                StrategyOrderSide.BUY,
                StrategyOrderType.LIMIT,
                limitPrice,
                BigDecimal.ZERO,
                requestedQuantity,
                filledQuantity,
                BigDecimal.ZERO,
                status,
                Instant.now(),
                Instant.now(),
                null,
                "{}"
        );
    }
}
