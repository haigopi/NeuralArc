package com.neuralarc.ui;

import com.neuralarc.model.Position;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyOrderSide;
import com.neuralarc.model.StrategyStage;
import com.neuralarc.model.StrategyStatus;
import com.neuralarc.util.Monetary;

import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Pure presenter — formats CPU, memory, and market-value display strings
 * without accessing any Swing components or broker I/O.
 */
public final class SystemMetricsPresenter {

    public String formatMarketValueText(List<ManagedStrategy> strategies) {
        return formatMarketValueText(strategies, null);
    }

    public String formatMarketValueText(List<ManagedStrategy> strategies, StrategyMode mode) {
        BigDecimal total = BigDecimal.ZERO;
        for (ManagedStrategy strategy : strategies) {
            if (shouldSkipStrategy(strategy, mode)) {
                continue;
            }
            total = total.add(strategy.cachedPosition().marketValue());
        }
        return "Market Value: " + Monetary.round(total).toPlainString();
    }

    public String formatInvestedValueText(List<ManagedStrategy> strategies) {
        return formatInvestedValueText(strategies, null);
    }

    public String formatInvestedValueText(List<ManagedStrategy> strategies, StrategyMode mode) {
        BigDecimal total = BigDecimal.ZERO;
        for (ManagedStrategy strategy : strategies) {
            if (shouldSkipStrategy(strategy, mode)) {
                continue;
            }
            Position position = strategy.cachedPosition();
            if (position.getTotalShares() <= 0) {
                continue;
            }
            total = total.add(position.totalInvested());
        }
        return "Invested Value: " + Monetary.round(total).toPlainString();
    }

    public String formatBaseBuyPendingTotalText(
            List<ManagedStrategy> strategies,
            Function<String, List<StrategyOrder>> ordersByStrategyId,
            StrategyMode mode
    ) {
        BigDecimal total = BigDecimal.ZERO;
        for (ManagedStrategy strategy : strategies) {
            if (shouldSkipStrategy(strategy, mode)) {
                continue;
            }
            List<StrategyOrder> orders = ordersByStrategyId.apply(strategy.strategy.id());
            for (StrategyOrder order : orders) {
                if (order.stage() != StrategyStage.BASE_BUY
                        || order.side() != StrategyOrderSide.BUY
                        || !order.isPending()) {
                    continue;
                }
                BigDecimal remainingQuantity = order.requestedQuantity().subtract(order.filledQuantity());
                if (remainingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                total = total.add(order.limitPrice().multiply(remainingQuantity));
            }
        }
        return "Base Buy Pending Total: " + Monetary.round(total).toPlainString();
    }

    private boolean shouldSkipStrategy(ManagedStrategy strategy, StrategyMode mode) {
        return strategy == null
                || strategy.strategy == null
                || strategy.strategy.status() == StrategyStatus.COMPLETED
                || (mode != null && strategy.strategy.mode() != mode);
    }

    public String formatCpuUsageText() {
        try {
            com.sun.management.OperatingSystemMXBean osBean =
                    ManagementFactory.getPlatformMXBean(com.sun.management.OperatingSystemMXBean.class);
            if (osBean == null) {
                return "CPU: -";
            }
            double processCpuLoad = osBean.getProcessCpuLoad();
            if (processCpuLoad < 0.0) {
                return "CPU: -";
            }
            return String.format(Locale.US, "CPU: %.1f%%", processCpuLoad * 100.0d);
        } catch (Exception ex) {
            return "CPU: -";
        }
    }

    public String formatMemoryUsageText() {
        Runtime runtime = Runtime.getRuntime();
        long usedBytes = runtime.totalMemory() - runtime.freeMemory();
        long usedMb = usedBytes / (1024L * 1024L);
        return "Memory: " + usedMb + " MB";
    }
}
