package com.neuralarc.earningshunter;

import com.neuralarc.model.StrategyMode;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

public record EarningsHunterConfig(
        int earningsWindowDays,
        long minimumAverageVolume,
        BigDecimal minimumStockPrice,
        BigDecimal minimumRelativeVolume,
        BigDecimal maximumStockPrice,
        BigDecimal minimumNewsScore,
        BigDecimal stopLossPercent,
        BigDecimal targetProfitPercent,
        int maxStocksToAdd,
        StrategyMode mode,
        List<String> candidateSymbols
) {
    public static final LocalTime PRIMARY_WINDOW_START_ET = LocalTime.of(9, 45);
    public static final LocalTime PRIMARY_WINDOW_END_ET = LocalTime.of(15, 45);

    public EarningsHunterConfig {
        earningsWindowDays = earningsWindowDays <= 0 ? 7 : Math.min(30, earningsWindowDays);
        minimumAverageVolume = minimumAverageVolume <= 0 ? 100_000L : minimumAverageVolume;
        minimumStockPrice = posOrDefault(minimumStockPrice, "0.5");
        minimumRelativeVolume = posOrDefault(minimumRelativeVolume, "0.5");
        minimumNewsScore = posOrDefault(minimumNewsScore, "50");
        stopLossPercent = posOrDefault(stopLossPercent, "5");
        targetProfitPercent = posOrDefault(targetProfitPercent, "10");
        maxStocksToAdd = maxStocksToAdd <= 0 ? 10 : maxStocksToAdd;
        mode = mode == null ? StrategyMode.PAPER : mode;
        candidateSymbols = candidateSymbols == null ? List.of() : List.copyOf(candidateSymbols);
    }

    public static EarningsHunterConfig defaults(StrategyMode mode) {
        return new EarningsHunterConfig(7, 100_000L, new BigDecimal("0.5"), new BigDecimal("0.5"),
                null, new BigDecimal("50"), new BigDecimal("5"), new BigDecimal("10"), 10, mode, List.of());
    }

    private static BigDecimal posOrDefault(BigDecimal value, String fallback) {
        BigDecimal v = value == null ? new BigDecimal(fallback) : value;
        return v.compareTo(BigDecimal.ZERO) <= 0 ? new BigDecimal(fallback) : v;
    }
}
