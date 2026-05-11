package com.neuralarc.ui;

import com.neuralarc.model.Position;
import com.neuralarc.model.StrategyStatus;
import com.neuralarc.util.Monetary;

import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Pure presenter — formats CPU, memory, and market-value display strings
 * without accessing any Swing components or broker I/O.
 */
public final class SystemMetricsPresenter {

    public String formatMarketValueText(List<ManagedStrategy> strategies) {
        BigDecimal total = BigDecimal.ZERO;
        for (ManagedStrategy strategy : strategies) {
            if (strategy == null || strategy.strategy == null || strategy.strategy.status() == StrategyStatus.COMPLETED) {
                continue;
            }
            total = total.add(strategy.cachedPosition().marketValue());
        }
        return "Market Value: " + Monetary.round(total).toPlainString();
    }

    public String formatInvestedValueText(List<ManagedStrategy> strategies) {
        BigDecimal total = BigDecimal.ZERO;
        for (ManagedStrategy strategy : strategies) {
            if (strategy == null || strategy.strategy == null || strategy.strategy.status() == StrategyStatus.COMPLETED) {
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
