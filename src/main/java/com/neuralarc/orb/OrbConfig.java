package com.neuralarc.orb;

import com.neuralarc.model.StrategyMode;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

public record OrbConfig(
        int rangeDurationMinutes,
        BigDecimal entryBufferPercent,
        StopMode stopMode,
        BigDecimal riskPercent,
        BigDecimal takeProfitPercent,
        int maxStocksToAdd,
        BigDecimal minimumPrice,
        BigDecimal maximumPrice,
        BigDecimal minimumRelativeVolume,
        BigDecimal minimumRangePercent,
        LocalTime latestEntryTimeEt,
        List<String> candidateSymbols,
        boolean autoDiscoverEnabled,
        boolean scheduleEnabled,
        StrategyMode mode
) {
    public OrbConfig {
        rangeDurationMinutes = switch (rangeDurationMinutes) {
            case 5, 15, 30 -> rangeDurationMinutes;
            default -> 15;
        };
        entryBufferPercent = posOrDefault(entryBufferPercent, "0.10");
        stopMode = stopMode == null ? StopMode.RANGE_LOW : stopMode;
        riskPercent = posOrDefault(riskPercent, "1.00");
        takeProfitPercent = posOrDefault(takeProfitPercent, "3.00");
        maxStocksToAdd = maxStocksToAdd <= 0 ? 10 : maxStocksToAdd;
        minimumPrice = posOrDefault(minimumPrice, "1.00");
        minimumRelativeVolume = posOrDefault(minimumRelativeVolume, "1.50");
        minimumRangePercent = posOrDefault(minimumRangePercent, "0.20");
        latestEntryTimeEt = latestEntryTimeEt == null ? LocalTime.of(11, 0) : latestEntryTimeEt;
        candidateSymbols = candidateSymbols == null ? List.of() : candidateSymbols.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.trim().toUpperCase())
                .distinct()
                .toList();
        mode = mode == null ? StrategyMode.PAPER : mode;
    }

    public static OrbConfig defaults(StrategyMode mode) {
        return new OrbConfig(15, new BigDecimal("0.10"), StopMode.RANGE_LOW, new BigDecimal("1.00"),
                new BigDecimal("3.00"), 10, new BigDecimal("1.00"), null, new BigDecimal("1.50"),
                new BigDecimal("0.20"), LocalTime.of(11, 0), List.of(), true, false, mode);
    }

    private static BigDecimal posOrDefault(BigDecimal value, String fallback) {
        BigDecimal v = value == null ? new BigDecimal(fallback) : value;
        return v.compareTo(BigDecimal.ZERO) <= 0 ? new BigDecimal(fallback) : v;
    }

    public enum StopMode { RANGE_LOW, MID_RANGE, ATR_ADJUSTED }
}
