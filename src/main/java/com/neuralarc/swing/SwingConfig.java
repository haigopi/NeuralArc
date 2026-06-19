package com.neuralarc.swing;

import com.neuralarc.model.StrategyMode;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

/**
 * Configuration for the Swing Vault strategy: multi-day swing positions in strong, up-trending names
 * that have pulled back to a rising moving-average support zone on the daily chart, intending to hold
 * across sessions for a swing back toward a recent high. Mirrors the shape of the other strategy
 * configs so the dialog, codec, and schedule stack stay consistent across strategies.
 *
 * <p>The compact constructor normalises null/invalid inputs to the documented defaults so persisted or
 * legacy configs never produce a broken scanner.
 */
public record SwingConfig(
        BigDecimal minimumPullbackPercent,
        BigDecimal maximumPullbackPercent,
        long minimumAverageVolume,
        BigDecimal minimumStockPrice,
        BigDecimal minimumRelativeVolume,
        BigDecimal maximumStockPrice,
        TrendFilter trendFilter,
        BigDecimal stopLossPercent,
        BigDecimal targetProfitPercent,
        int maxStocksToAdd,
        ExecutionFrequency executionFrequency,
        StrategyMode mode,
        List<String> candidateSymbols
) {
    /**
     * Regular-session scan window. Swing Vault works on daily bars, so it scans shortly after the open
     * (once the prior close and the day's opening context are available) rather than at the premarket.
     */
    public static final LocalTime PRIMARY_WINDOW_START_ET = LocalTime.of(9, 45);
    public static final LocalTime PRIMARY_WINDOW_END_ET = LocalTime.of(15, 45);

    public SwingConfig {
        minimumPullbackPercent = posOrDefault(minimumPullbackPercent, "3");
        maximumPullbackPercent = posOrDefault(maximumPullbackPercent, "15");
        if (maximumPullbackPercent.compareTo(minimumPullbackPercent) < 0) {
            maximumPullbackPercent = minimumPullbackPercent;
        }
        minimumStockPrice = posOrDefault(minimumStockPrice, "5");
        minimumRelativeVolume = posOrDefault(minimumRelativeVolume, "0.8");
        minimumAverageVolume = minimumAverageVolume <= 0 ? 500_000L : minimumAverageVolume;
        stopLossPercent = posOrDefault(stopLossPercent, "6");
        targetProfitPercent = posOrDefault(targetProfitPercent, "12");
        trendFilter = trendFilter == null ? TrendFilter.ABOVE_MA_50_AND_200 : trendFilter;
        maxStocksToAdd = maxStocksToAdd <= 0 ? 10 : maxStocksToAdd;
        executionFrequency = executionFrequency == null ? ExecutionFrequency.MANUAL : executionFrequency;
        mode = mode == null ? StrategyMode.PAPER : mode;
        candidateSymbols = candidateSymbols == null ? List.of() : List.copyOf(candidateSymbols);
    }

    public SwingConfig(
            BigDecimal minimumPullbackPercent, BigDecimal maximumPullbackPercent, long minimumAverageVolume,
            BigDecimal minimumStockPrice, BigDecimal minimumRelativeVolume, BigDecimal maximumStockPrice,
            TrendFilter trendFilter, BigDecimal stopLossPercent, BigDecimal targetProfitPercent,
            int maxStocksToAdd, ExecutionFrequency executionFrequency, StrategyMode mode
    ) {
        this(minimumPullbackPercent, maximumPullbackPercent, minimumAverageVolume, minimumStockPrice,
                minimumRelativeVolume, maximumStockPrice, trendFilter, stopLossPercent, targetProfitPercent,
                maxStocksToAdd, executionFrequency, mode, List.of());
    }

    public static SwingConfig defaults(StrategyMode mode) {
        return new SwingConfig(new BigDecimal("3"), new BigDecimal("15"), 500_000L, new BigDecimal("5"),
                new BigDecimal("0.8"), null, TrendFilter.ABOVE_MA_50_AND_200, new BigDecimal("6"),
                new BigDecimal("12"), 10, ExecutionFrequency.MANUAL, mode, List.of());
    }

    private static BigDecimal posOrDefault(BigDecimal value, String fallback) {
        BigDecimal v = value == null ? new BigDecimal(fallback) : value;
        return v.compareTo(BigDecimal.ZERO) <= 0 ? new BigDecimal(fallback) : v;
    }

    /**
     * Multi-week-trend confirmation. Swing setups want a stock that is still clearly trending up on the
     * daily chart, so the strongest filter requires the full moving-average stack to be aligned.
     */
    public enum TrendFilter { ABOVE_MA_50, ABOVE_MA_50_AND_200, STACKED_UPTREND, DISABLED }

    /** Swing Vault scans on daily bars, so its scheduled cadence is once per trading day, not intraday. */
    public enum ExecutionFrequency { MANUAL, ONCE_PER_DAY, MARKET_OPEN_ONLY }
}
