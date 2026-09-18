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
    private final SystemMetricsPresenter presenter = new SystemMetricsPresenter();

    @Test
    void investedAndMarketValueCountHeldSharesAndSkipCompletedStrategies() {
        ManagedStrategy active = managed("AAPL", StrategyStatus.ACTIVE, 2, new BigDecimal("100.00"));
        ManagedStrategy completed = managed("MSFT", StrategyStatus.COMPLETED, 3, new BigDecimal("200.00"));
        ManagedStrategy waiting = managed("NVDA", StrategyStatus.ACTIVE, 0, BigDecimal.ZERO);

        SystemMetricsPresenter.PortfolioScopeMetrics metrics = presenter.computePortfolioScopeMetrics(
                List.of(active, completed, waiting), id -> List.of());

        assertEquals(new BigDecimal("200.00"), metrics.investedValue());
        assertEquals(new BigDecimal("200.00"), metrics.marketValue());
    }

    @Test
    void upcomingBuysAreTheUnfilledPartOfEveryWorkingBuyOrder() {
        ManagedStrategy first = managed("AAPL", StrategyStatus.ACTIVE, 0, BigDecimal.ZERO);
        ManagedStrategy second = managed("MSFT", StrategyStatus.ACTIVE, 4, new BigDecimal("25.00"));
        StrategyOrder baseBuy = order(first.strategy.id(), StrategyStage.BASE_BUY, StrategyOrderSide.BUY,
                StrategyOrderStatus.PENDING, "10.00", "5", "0");
        StrategyOrder partlyFilled = order(second.strategy.id(), StrategyStage.BASE_BUY, StrategyOrderSide.BUY,
                StrategyOrderStatus.PARTIALLY_FILLED, "20.00", "6", "2");
        StrategyOrder filled = order(second.strategy.id(), StrategyStage.BASE_BUY, StrategyOrderSide.BUY,
                StrategyOrderStatus.FILLED, "20.00", "4", "4");
        StrategyOrder canceled = order(second.strategy.id(), StrategyStage.BASE_BUY, StrategyOrderSide.BUY,
                StrategyOrderStatus.CANCELED, "19.00", "9", "0");
        StrategyOrder targetSell = order(second.strategy.id(), StrategyStage.TARGET_SELL, StrategyOrderSide.SELL,
                StrategyOrderStatus.SUBMITTED, "30.00", "4", "0");

        SystemMetricsPresenter.PortfolioScopeMetrics metrics = presenter.computePortfolioScopeMetrics(
                List.of(first, second),
                id -> id.equals(first.strategy.id()) ? List.of(baseBuy) : List.of(partlyFilled, filled, canceled, targetSell));

        assertEquals(new BigDecimal("130.00"), metrics.upcomingBuyTotal(), "5 x $10 plus the 4 unfilled of 6 x $20");
        assertEquals(new BigDecimal("100.00"), metrics.investedValue());
        assertEquals(2, metrics.pendingBuyPositions());
        assertEquals(1, metrics.pendingSellPositions());
    }

    @Test
    void aMarketBuyWithoutALimitIsValuedAtTheLastPrice() {
        ManagedStrategy held = managed("AAPL", StrategyStatus.ACTIVE, 1, new BigDecimal("12.00"));
        StrategyOrder marketBuy = order(held.strategy.id(), StrategyStage.BASE_BUY, StrategyOrderSide.BUY,
                StrategyOrderStatus.SUBMITTED, "0", "3", "0");

        SystemMetricsPresenter.PortfolioScopeMetrics metrics = presenter.computePortfolioScopeMetrics(
                List.of(held), id -> List.of(marketBuy));

        assertEquals(new BigDecimal("36.00"), metrics.upcomingBuyTotal());
    }

    @Test
    void aPositionWithSeveralWorkingOrdersCountsOnce() {
        ManagedStrategy held = managed("AAPL", StrategyStatus.ACTIVE, 2, new BigDecimal("10.00"));
        StrategyOrder firstBuy = order(held.strategy.id(), StrategyStage.BASE_BUY, StrategyOrderSide.BUY,
                StrategyOrderStatus.PENDING, "9.00", "1", "0");
        StrategyOrder secondBuy = order(held.strategy.id(), StrategyStage.BASE_BUY, StrategyOrderSide.BUY,
                StrategyOrderStatus.PENDING, "8.00", "1", "0");
        StrategyOrder firstSell = order(held.strategy.id(), StrategyStage.TARGET_SELL, StrategyOrderSide.SELL,
                StrategyOrderStatus.PENDING, "12.00", "1", "0");
        StrategyOrder secondSell = order(held.strategy.id(), StrategyStage.TARGET_SELL, StrategyOrderSide.SELL,
                StrategyOrderStatus.PENDING, "13.00", "1", "0");

        SystemMetricsPresenter.PortfolioScopeMetrics metrics = presenter.computePortfolioScopeMetrics(
                List.of(held), id -> List.of(firstBuy, secondBuy, firstSell, secondSell));

        assertEquals(1, metrics.pendingBuyPositions());
        assertEquals(1, metrics.pendingSellPositions());
        assertEquals(new BigDecimal("17.00"), metrics.upcomingBuyTotal());
    }

    @Test
    void gainingAndLosingIncludePausedPositionsAndIgnoreFlatOnes() {
        ManagedStrategy gaining = managed("AAPL", StrategyStatus.ACTIVE, 10, new BigDecimal("10.00"), new BigDecimal("12.00"));
        ManagedStrategy pausedLosing = managed("MSFT", StrategyStatus.PAUSED, 5, new BigDecimal("20.00"), new BigDecimal("18.00"));
        ManagedStrategy flat = managed("NVDA", StrategyStatus.ACTIVE, 3, new BigDecimal("50.00"));

        SystemMetricsPresenter.PortfolioScopeMetrics metrics = presenter.computePortfolioScopeMetrics(
                List.of(gaining, pausedLosing, flat), id -> List.of());

        assertEquals(1, metrics.gainingCount());
        assertEquals(new BigDecimal("20.00"), metrics.gainingPnl());
        assertEquals(1, metrics.losingCount(), "a paused position still moves with the market");
        assertEquals(new BigDecimal("-10.00"), metrics.losingPnl());
    }

    @Test
    void noRowsGiveZeros() {
        SystemMetricsPresenter.PortfolioScopeMetrics metrics = presenter.computePortfolioScopeMetrics(null, null);

        assertEquals(BigDecimal.ZERO, metrics.investedValue());
        assertEquals(0, metrics.pendingBuyPositions());
        assertEquals(0, metrics.gainingCount());
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

    @Test
    void aShortPositionCountsAndGainingPlusLosingEqualsTheWholePnl() {
        // Regression guard from the screenshot: the header read -$476.69 while Gaining + Losing read
        // +$3.11 and -$442.25. The $37.55 gap was MRVL, short 1 share at $201.20 now $238.75, which
        // the footer skipped because it only counted long positions.
        ManagedStrategy longWinner = managed("AAPL", StrategyStatus.ACTIVE, 1, new BigDecimal("100.00"), new BigDecimal("103.11"));
        ManagedStrategy longLoser = managed("ORCL", StrategyStatus.ACTIVE, 1, new BigDecimal("600.00"), new BigDecimal("157.75"));
        ManagedStrategy shortLoser = managed("MRVL", StrategyStatus.ACTIVE, -1, new BigDecimal("201.20"), new BigDecimal("238.75"));

        SystemMetricsPresenter.PortfolioScopeMetrics metrics = new SystemMetricsPresenter()
                .computePortfolioScopeMetrics(List.of(longWinner, longLoser, shortLoser), id -> List.of());

        assertEquals(1, metrics.gainingCount());
        assertEquals(2, metrics.losingCount(), "a short priced above its entry is losing");
        assertEquals(new BigDecimal("-479.80"), metrics.losingPnl());
        BigDecimal wholePnl = longWinner.cachedPosition().unrealizedPnl()
                .add(longLoser.cachedPosition().unrealizedPnl())
                .add(shortLoser.cachedPosition().unrealizedPnl());
        assertEquals(0, wholePnl.compareTo(metrics.gainingPnl().add(metrics.losingPnl())),
                "the footer's two sides sum to the same P&L the header shows");
    }

    @Test
    void aShortPositionPricedBelowItsEntryIsGaining() {
        ManagedStrategy shortWinner = managed("MRVL", StrategyStatus.ACTIVE, -2, new BigDecimal("240.00"), new BigDecimal("230.00"));

        SystemMetricsPresenter.PortfolioScopeMetrics metrics = new SystemMetricsPresenter()
                .computePortfolioScopeMetrics(List.of(shortWinner), id -> List.of());

        assertEquals(1, metrics.gainingCount());
        assertEquals(new BigDecimal("20.00"), metrics.gainingPnl().setScale(2));
    }

    private static ManagedStrategy managed(String symbol, StrategyStatus status, int shares, BigDecimal price) {
        return managed(symbol, status, shares, price, price);
    }

    private static ManagedStrategy managed(String symbol, StrategyStatus status, int shares, BigDecimal cost, BigDecimal lastPrice) {
        ManagedStrategy managed = new ManagedStrategy(strategy(symbol, StrategyMode.PAPER, status));
        Position position = new Position(symbol);
        if (shares != 0) {
            position.applyBuy(shares, cost); // A negative count opens a short at this cost.
            position.setLastPrice(lastPrice);
        }
        managed.setCachedPosition(position);
        return managed;
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
            StrategyOrderSide side,
            StrategyOrderStatus status,
            String limitPrice,
            String requestedQuantity,
            String filledQuantity
    ) {
        return new StrategyOrder(
                UUID.randomUUID().toString(),
                strategyId,
                stage,
                "alpaca-" + UUID.randomUUID(),
                "client-" + UUID.randomUUID(),
                "AAPL",
                side,
                StrategyOrderType.LIMIT,
                new BigDecimal(limitPrice),
                BigDecimal.ZERO,
                new BigDecimal(requestedQuantity),
                new BigDecimal(filledQuantity),
                BigDecimal.ZERO,
                status,
                Instant.now(),
                Instant.now(),
                null,
                "{}"
        );
    }
}
