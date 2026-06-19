package com.neuralarc.vwap;

import com.neuralarc.model.StrategyMode;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

/**
 * Configuration for the VWAP Desk strategy: mean-reversion buys when a still-strong name trades at a
 * meaningful discount below its intraday volume-weighted average price (VWAP), expecting a reversion
 * back toward VWAP. Mirrors the shape of the other strategy configs so the dialog, codec, and schedule
 * stack stay consistent across strategies.
 *
 * <p>The compact constructor normalises null/invalid inputs to the documented defaults so persisted or
 * legacy configs never produce a broken scanner.
 */
public record VwapConfig(
        BigDecimal minimumDiscountPercent,
        BigDecimal maximumDiscountPercent,
        long minimumAverageVolume,
        BigDecimal minimumStockPrice,
        BigDecimal minimumRelativeVolume,
        BigDecimal maximumStockPrice,
        TrendFilter trendFilter,
        BigDecimal stopLossPercent,
        int maxStocksToAdd,
        ExecutionFrequency executionFrequency,
        StrategyMode mode,
        List<String> candidateSymbols
) {
    /** Regular-session scan window: VWAP reversion is an intraday setup, not a premarket gap. */
    public static final LocalTime PRIMARY_WINDOW_START_ET = LocalTime.of(10, 0);
    public static final LocalTime PRIMARY_WINDOW_END_ET = LocalTime.of(15, 30);

    public VwapConfig {
        minimumDiscountPercent = posOrDefault(minimumDiscountPercent, "1");
        maximumDiscountPercent = posOrDefault(maximumDiscountPercent, "8");
        if (maximumDiscountPercent.compareTo(minimumDiscountPercent) < 0) {
            maximumDiscountPercent = minimumDiscountPercent;
        }
        minimumStockPrice = posOrDefault(minimumStockPrice, "5");
        minimumRelativeVolume = posOrDefault(minimumRelativeVolume, "1.0");
        minimumAverageVolume = minimumAverageVolume <= 0 ? 500_000L : minimumAverageVolume;
        stopLossPercent = posOrDefault(stopLossPercent, "4");
        trendFilter = trendFilter == null ? TrendFilter.ABOVE_MA_50 : trendFilter;
        maxStocksToAdd = maxStocksToAdd <= 0 ? 10 : maxStocksToAdd;
        executionFrequency = executionFrequency == null ? ExecutionFrequency.MANUAL : executionFrequency;
        mode = mode == null ? StrategyMode.PAPER : mode;
        candidateSymbols = candidateSymbols == null ? List.of() : List.copyOf(candidateSymbols);
    }

    public VwapConfig(
            BigDecimal minimumDiscountPercent, BigDecimal maximumDiscountPercent, long minimumAverageVolume,
            BigDecimal minimumStockPrice, BigDecimal minimumRelativeVolume, BigDecimal maximumStockPrice,
            TrendFilter trendFilter, BigDecimal stopLossPercent, int maxStocksToAdd,
            ExecutionFrequency executionFrequency, StrategyMode mode
    ) {
        this(minimumDiscountPercent, maximumDiscountPercent, minimumAverageVolume, minimumStockPrice,
                minimumRelativeVolume, maximumStockPrice, trendFilter, stopLossPercent, maxStocksToAdd,
                executionFrequency, mode, List.of());
    }

    public static VwapConfig defaults(StrategyMode mode) {
        return new VwapConfig(new BigDecimal("1"), new BigDecimal("8"), 500_000L, new BigDecimal("5"),
                new BigDecimal("1.0"), null, TrendFilter.ABOVE_MA_50, new BigDecimal("4"), 10,
                ExecutionFrequency.MANUAL, mode, List.of());
    }

    private static BigDecimal posOrDefault(BigDecimal value, String fallback) {
        BigDecimal v = value == null ? new BigDecimal(fallback) : value;
        return v.compareTo(BigDecimal.ZERO) <= 0 ? new BigDecimal(fallback) : v;
    }

    /** Broader-trend confirmation: a "strong name" trades above its longer moving average(s). */
    public enum TrendFilter { ABOVE_MA_50, ABOVE_MA_200, ABOVE_MA_50_OR_200, DISABLED }

    public enum ExecutionFrequency { MANUAL, EVERY_5_MINUTES, EVERY_15_MINUTES, MARKET_OPEN_ONLY }
}
