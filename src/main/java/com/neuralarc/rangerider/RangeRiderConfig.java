package com.neuralarc.rangerider;

import com.neuralarc.model.StrategyMode;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

/**
 * Configuration for the Range Rider strategy: a same-day income plan built from the last few weeks of
 * daily bars. For each candidate the scanner records every completed session's open, high, and low and
 * averages them; the analyzer converts those averages into the typical dip below the open and the
 * typical rally above it, then plans a buy at the dip and a sell at the rally — both intended to
 * complete inside the same trading session.
 *
 * <p>Mirrors the shape of the other strategy configs so the dialog, codec, and schedule stack stay
 * consistent across strategies. The compact constructor normalises null/invalid inputs to the
 * documented defaults so persisted or legacy configs never produce a broken scanner.
 *
 * @param lookbackSessions              trading sessions to analyze; 15 ≈ the last three weeks
 * @param targetCapturePercent          what share of the typical dip and rally the plan aims to take.
 *                                      This is the strategy's central trade-off. Aiming for the full
 *                                      move (100) only completes on unusually wide, perfectly
 *                                      positioned sessions — measured fill rates of roughly 7–13%.
 *                                      Taking half of it (the 50 default) gives up per-trade size but
 *                                      lifts same-day completion to roughly 50–75%, which is what a
 *                                      daily-income plan actually needs.
 * @param minimumExpectedGainPercent    floor on the planned buy-to-sell gain, so a stock whose typical
 *                                      day is too quiet to cover spread and commission is not offered
 * @param minimumSameDayFillRatePercent minimum share of lookback sessions in which the planned buy and
 *                                      the planned sell would both have been reached on the same day
 * @param minimumEntryTouchRatePercent  minimum share of lookback sessions in which the planned entry
 *                                      price was reached at all — separate from whether the exit also
 *                                      filled. Filters stocks that almost never dip to the buy level.
 */
public record RangeRiderConfig(
        int lookbackSessions,
        BigDecimal minimumAverageRangePercent,
        BigDecimal maximumAverageRangePercent,
        BigDecimal minimumSameDayFillRatePercent,
        BigDecimal minimumEntryTouchRatePercent,
        long minimumAverageVolume,
        BigDecimal minimumStockPrice,
        BigDecimal maximumStockPrice,
        BigDecimal targetCapturePercent,
        BigDecimal minimumExpectedGainPercent,
        BigDecimal stopLossPercent,
        int maxStocksToAdd,
        ExecutionFrequency executionFrequency,
        StrategyMode mode,
        List<String> candidateSymbols
) {
    /**
     * Regular-session scan window. Range Rider needs the session to be open (so the planned low can
     * actually be touched) and stops well before the close so the same-day sell still has room to fill.
     */
    public static final LocalTime PRIMARY_WINDOW_START_ET = LocalTime.of(9, 45);
    public static final LocalTime PRIMARY_WINDOW_END_ET = LocalTime.of(15, 30);

    /** Three weeks of trading ≈ 15 sessions. */
    public static final int DEFAULT_LOOKBACK_SESSIONS = 15;

    public RangeRiderConfig {
        lookbackSessions = lookbackSessions <= 0 ? DEFAULT_LOOKBACK_SESSIONS : Math.min(lookbackSessions, 120);
        minimumAverageRangePercent = posOrDefault(minimumAverageRangePercent, "1");
        maximumAverageRangePercent = posOrDefault(maximumAverageRangePercent, "12");
        if (maximumAverageRangePercent.compareTo(minimumAverageRangePercent) < 0) {
            maximumAverageRangePercent = minimumAverageRangePercent;
        }
        minimumSameDayFillRatePercent = nonNegativeOrDefault(minimumSameDayFillRatePercent, "35");
        if (minimumSameDayFillRatePercent.compareTo(BigDecimal.valueOf(100)) > 0) {
            minimumSameDayFillRatePercent = BigDecimal.valueOf(100);
        }
        minimumEntryTouchRatePercent = nonNegativeOrDefault(minimumEntryTouchRatePercent, "40");
        if (minimumEntryTouchRatePercent.compareTo(BigDecimal.valueOf(100)) > 0) {
            minimumEntryTouchRatePercent = BigDecimal.valueOf(100);
        }
        minimumAverageVolume = minimumAverageVolume <= 0 ? 1_000_000L : minimumAverageVolume;
        minimumStockPrice = posOrDefault(minimumStockPrice, "10");
        targetCapturePercent = posOrDefault(targetCapturePercent, "50");
        if (targetCapturePercent.compareTo(BigDecimal.valueOf(100)) > 0) {
            targetCapturePercent = BigDecimal.valueOf(100);
        }
        minimumExpectedGainPercent = nonNegativeOrDefault(minimumExpectedGainPercent, "0.5");
        stopLossPercent = posOrDefault(stopLossPercent, "2");
        maxStocksToAdd = maxStocksToAdd <= 0 ? 10 : maxStocksToAdd;
        executionFrequency = executionFrequency == null ? ExecutionFrequency.MANUAL : executionFrequency;
        mode = mode == null ? StrategyMode.PAPER : mode;
        candidateSymbols = candidateSymbols == null ? List.of() : List.copyOf(candidateSymbols);
    }

    public RangeRiderConfig(
            int lookbackSessions, BigDecimal minimumAverageRangePercent, BigDecimal maximumAverageRangePercent,
            BigDecimal minimumSameDayFillRatePercent, long minimumAverageVolume, BigDecimal minimumStockPrice,
            BigDecimal maximumStockPrice, BigDecimal targetCapturePercent, BigDecimal minimumExpectedGainPercent,
            BigDecimal stopLossPercent, int maxStocksToAdd, ExecutionFrequency executionFrequency, StrategyMode mode
    ) {
        this(lookbackSessions, minimumAverageRangePercent, maximumAverageRangePercent,
                minimumSameDayFillRatePercent, new BigDecimal("40"), minimumAverageVolume, minimumStockPrice,
                maximumStockPrice, targetCapturePercent, minimumExpectedGainPercent,
                stopLossPercent, maxStocksToAdd, executionFrequency, mode, List.of());
    }

    public static RangeRiderConfig defaults(StrategyMode mode) {
        return new RangeRiderConfig(DEFAULT_LOOKBACK_SESSIONS, new BigDecimal("1"), new BigDecimal("12"),
                new BigDecimal("35"), new BigDecimal("40"), 1_000_000L, new BigDecimal("10"), null,
                new BigDecimal("50"), new BigDecimal("0.5"), new BigDecimal("2"), 10,
                ExecutionFrequency.MANUAL, mode, List.of());
    }

    private static BigDecimal posOrDefault(BigDecimal value, String fallback) {
        BigDecimal v = value == null ? new BigDecimal(fallback) : value;
        return v.compareTo(BigDecimal.ZERO) <= 0 ? new BigDecimal(fallback) : v;
    }

    private static BigDecimal nonNegativeOrDefault(BigDecimal value, String fallback) {
        BigDecimal v = value == null ? new BigDecimal(fallback) : value;
        return v.compareTo(BigDecimal.ZERO) < 0 ? new BigDecimal(fallback) : v;
    }

    /**
     * Range Rider plans off daily bars, so the plan itself does not change intraday; re-scanning only
     * picks up names that have newly traded down into their planned entry.
     */
    public enum ExecutionFrequency { MANUAL, EVERY_15_MINUTES, EVERY_30_MINUTES, MARKET_OPEN_ONLY }
}
