package com.neuralarc.diphunter;

import com.neuralarc.model.StrategyMode;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

/**
 * Configuration for the Dip Hunter strategy: buy a pullback in a still-strong (up-trending) name and
 * ride the bounce. Mirrors the shape of {@code GapRocketConfig} so the dialog, codec, and schedule
 * stack stay consistent across strategies.
 *
 * <p>All thresholds are operator-tunable; the compact constructor normalises null/invalid inputs to
 * the documented defaults so persisted/legacy configs never produce a broken scanner.
 */
public record DipHunterConfig(
        BigDecimal minimumPullbackPercent,
        BigDecimal maximumPullbackPercent,
        long minimumAverageVolume,
        BigDecimal minimumStockPrice,
        BigDecimal minimumRelativeVolume,
        BigDecimal maximumStockPrice,
        TrendFilter trendFilter,
        BounceConfirmation bounceConfirmation,
        BigDecimal stopLossPercent,
        BigDecimal takeProfitPercent,
        int maxStocksToAdd,
        ExecutionFrequency executionFrequency,
        StrategyMode mode,
        List<String> candidateSymbols
) {
    /** Regular-session scan window: Dip Hunter looks for intraday pullbacks, not premarket gaps. */
    public static final LocalTime PRIMARY_WINDOW_START_ET = LocalTime.of(10, 0);
    public static final LocalTime PRIMARY_WINDOW_END_ET = LocalTime.of(15, 30);

    public DipHunterConfig {
        minimumPullbackPercent = posOrDefault(minimumPullbackPercent, "3");
        maximumPullbackPercent = posOrDefault(maximumPullbackPercent, "15");
        if (maximumPullbackPercent.compareTo(minimumPullbackPercent) < 0) {
            maximumPullbackPercent = minimumPullbackPercent;
        }
        minimumStockPrice = posOrDefault(minimumStockPrice, "5");
        minimumRelativeVolume = posOrDefault(minimumRelativeVolume, "1.2");
        minimumAverageVolume = minimumAverageVolume <= 0 ? 500_000L : minimumAverageVolume;
        stopLossPercent = posOrDefault(stopLossPercent, "5");
        takeProfitPercent = posOrDefault(takeProfitPercent, "10");
        trendFilter = trendFilter == null ? TrendFilter.ABOVE_MA_20_OR_50 : trendFilter;
        bounceConfirmation = bounceConfirmation == null ? BounceConfirmation.INTRADAY_REVERSAL : bounceConfirmation;
        maxStocksToAdd = maxStocksToAdd <= 0 ? 10 : maxStocksToAdd;
        executionFrequency = executionFrequency == null ? ExecutionFrequency.MANUAL : executionFrequency;
        mode = mode == null ? StrategyMode.PAPER : mode;
        candidateSymbols = candidateSymbols == null ? List.of() : List.copyOf(candidateSymbols);
    }

    public DipHunterConfig(
            BigDecimal minimumPullbackPercent, BigDecimal maximumPullbackPercent, long minimumAverageVolume,
            BigDecimal minimumStockPrice, BigDecimal minimumRelativeVolume, BigDecimal maximumStockPrice,
            TrendFilter trendFilter, BounceConfirmation bounceConfirmation, BigDecimal stopLossPercent,
            BigDecimal takeProfitPercent, int maxStocksToAdd, ExecutionFrequency executionFrequency, StrategyMode mode
    ) {
        this(minimumPullbackPercent, maximumPullbackPercent, minimumAverageVolume, minimumStockPrice,
                minimumRelativeVolume, maximumStockPrice, trendFilter, bounceConfirmation, stopLossPercent,
                takeProfitPercent, maxStocksToAdd, executionFrequency, mode, List.of());
    }

    public static DipHunterConfig defaults(StrategyMode mode) {
        return new DipHunterConfig(new BigDecimal("3"), new BigDecimal("15"), 500_000L, new BigDecimal("5"),
                new BigDecimal("1.2"), null, TrendFilter.ABOVE_MA_20_OR_50, BounceConfirmation.INTRADAY_REVERSAL,
                new BigDecimal("5"), new BigDecimal("10"), 10, ExecutionFrequency.MANUAL, mode, List.of());
    }

    private static BigDecimal posOrDefault(BigDecimal value, String fallback) {
        BigDecimal v = value == null ? new BigDecimal(fallback) : value;
        return v.compareTo(BigDecimal.ZERO) <= 0 ? new BigDecimal(fallback) : v;
    }

    /** Uptrend confirmation: a "strong name" trades above its moving average(s). */
    public enum TrendFilter { ABOVE_MA_20, ABOVE_MA_50, ABOVE_MA_20_OR_50, DISABLED }

    /** How the dip should confirm a bounce before it is marked Ready to Buy. */
    public enum BounceConfirmation { INTRADAY_REVERSAL, NEAR_SUPPORT, MANUAL_REVIEW }

    public enum ExecutionFrequency { MANUAL, EVERY_5_MINUTES, EVERY_15_MINUTES, MARKET_OPEN_ONLY }
}
