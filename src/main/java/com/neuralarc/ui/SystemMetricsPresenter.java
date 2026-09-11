package com.neuralarc.ui;

import com.neuralarc.model.Position;
import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyOrderSide;
import com.neuralarc.model.StrategyStatus;
import com.neuralarc.util.Monetary;

import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Pure presenter — formats CPU and memory display strings and totals the portfolio figures of a
 * grid scope, without accessing any Swing components or broker I/O.
 */
public final class SystemMetricsPresenter {

    public record PortfolioScopeMetrics(
            BigDecimal marketValue,
            BigDecimal investedValue,
            BigDecimal upcomingBuyTotal,
            BigDecimal gainingPnl,
            int gainingCount,
            BigDecimal losingPnl,
            int losingCount,
            int pendingBuyPositions,
            int pendingSellPositions
    ) {
    }

    /**
     * Totals one grid scope. The caller passes only the rows of that scope (mode and workspace).
     * Gaining and losing count every held position, running or paused, since a paused position still
     * moves with the market. A position counts once as pending buy or pending sell however many of
     * its orders are working; upcoming buys are valued at the limit price, or at the last price for
     * a market order.
     */
    public PortfolioScopeMetrics computePortfolioScopeMetrics(
            List<ManagedStrategy> strategies,
            Function<String, List<StrategyOrder>> ordersByStrategyId
    ) {
        BigDecimal marketValue = BigDecimal.ZERO;
        BigDecimal investedValue = BigDecimal.ZERO;
        BigDecimal upcomingBuyTotal = BigDecimal.ZERO;
        BigDecimal gainingPnl = BigDecimal.ZERO;
        int gainingCount = 0;
        BigDecimal losingPnl = BigDecimal.ZERO;
        int losingCount = 0;
        int pendingBuyPositions = 0;
        int pendingSellPositions = 0;
        if (strategies == null) {
            return new PortfolioScopeMetrics(
                    marketValue,
                    investedValue,
                    upcomingBuyTotal,
                    gainingPnl,
                    gainingCount,
                    losingPnl,
                    losingCount,
                    pendingBuyPositions,
                    pendingSellPositions
            );
        }
        for (ManagedStrategy managed : strategies) {
            if (managed == null || managed.strategy == null || managed.strategy.status() == StrategyStatus.COMPLETED) {
                continue;
            }
            Position position = managed.cachedPosition();
            marketValue = marketValue.add(position.marketValue());
            if (position.getTotalShares() > 0) {
                investedValue = investedValue.add(position.totalInvested());
            }
            if (position.getTotalShares() > 0 && position.getLastPrice().compareTo(BigDecimal.ZERO) > 0) {
                int trend = position.getLastPrice().compareTo(position.getAverageCost());
                if (trend > 0) {
                    gainingPnl = gainingPnl.add(position.unrealizedPnl());
                    gainingCount++;
                } else if (trend < 0) {
                    losingPnl = losingPnl.add(position.unrealizedPnl());
                    losingCount++;
                }
            }
            boolean hasPendingBuy = false;
            boolean hasPendingSell = false;
            List<StrategyOrder> orders = ordersByStrategyId == null
                    ? List.of()
                    : ordersByStrategyId.apply(managed.strategy.id());
            for (StrategyOrder order : orders) {
                if (order == null || !order.isPending()) {
                    continue;
                }
                BigDecimal remainingQuantity = order.requestedQuantity().subtract(order.filledQuantity());
                if (remainingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                if (order.side() == StrategyOrderSide.BUY) {
                    hasPendingBuy = true;
                    BigDecimal price = order.limitPrice().compareTo(BigDecimal.ZERO) > 0
                            ? order.limitPrice()
                            : position.getLastPrice();
                    upcomingBuyTotal = upcomingBuyTotal.add(price.multiply(remainingQuantity));
                } else if (order.side() == StrategyOrderSide.SELL) {
                    hasPendingSell = true;
                }
            }
            if (hasPendingBuy) {
                pendingBuyPositions++;
            }
            if (hasPendingSell) {
                pendingSellPositions++;
            }
        }
        return new PortfolioScopeMetrics(
                Monetary.round(marketValue),
                Monetary.round(investedValue),
                Monetary.round(upcomingBuyTotal),
                Monetary.round(gainingPnl),
                gainingCount,
                Monetary.round(losingPnl),
                losingCount,
                pendingBuyPositions,
                pendingSellPositions
        );
    }

    public String formatCpuUsageText() {
        try {
            com.sun.management.OperatingSystemMXBean osBean =
                    ManagementFactory.getPlatformMXBean(com.sun.management.OperatingSystemMXBean.class);
            if (osBean == null) {
                return "CPU: -";
            }
            return formatCpuUsageText(osBean.getProcessCpuLoad(), osBean.getAvailableProcessors());
        } catch (Exception ex) {
            return "CPU: -";
        }
    }

    String formatCpuUsageText(double processCpuLoad, int availableProcessors) {
        if (processCpuLoad < 0.0d) {
            return "CPU: -";
        }
        double normalizedProcessPercent = processCpuLoad * 100.0d;
        return String.format(Locale.US, "CPU: %.1f%%", normalizedProcessPercent);
    }

    public String formatMemoryUsageText() {
        Runtime runtime = Runtime.getRuntime();
        long usedBytes = runtime.totalMemory() - runtime.freeMemory();
        long usedMb = usedBytes / (1024L * 1024L);
        return "Memory: " + usedMb + " MB";
    }
}
