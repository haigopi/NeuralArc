package com.neuralarc.profitshield;

import com.neuralarc.model.StrategyMode;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

/**
 * Configuration for the Profit Shield strategy: the defensive book. Where the other scanners hunt for
 * upside, Profit Shield hunts for names that <em>keep</em> what they have — quiet daily ranges,
 * shallow historical drawdowns, still trading near their own lookback high, and backed by a rising
 * long-term trend. Entries are planned with a deliberately tight protective stop and a modest target
 * so realized gains rotated into this book stay protected.
 *
 * <p>Mirrors the shape of the other strategy configs so the dialog, codec, and schedule stack stay
 * consistent across strategies. The compact constructor normalises null/invalid inputs to the
 * documented defaults so persisted or legacy configs never produce a broken scanner.
 */
public record ProfitShieldConfig(
        int drawdownLookbackSessions,
        BigDecimal maximumDailyVolatilityPercent,
        BigDecimal maximumDrawdownPercent,
        BigDecimal maximumDistanceFromHighPercent,
        long minimumAverageVolume,
        BigDecimal minimumStockPrice,
        BigDecimal maximumStockPrice,
        TrendFilter trendFilter,
        BigDecimal entryDiscountPercent,
        BigDecimal protectiveStopPercent,
        BigDecimal targetProfitPercent,
        int maxStocksToAdd,
        StrategyMode mode,
        List<String> candidateSymbols
) {
    /**
     * Regular-session scan window. Profit Shield reads daily bars, so it scans shortly after the open
     * (once the prior close is final) and stops well before the close.
     */
    public static final LocalTime PRIMARY_WINDOW_START_ET = LocalTime.of(9, 45);
    public static final LocalTime PRIMARY_WINDOW_END_ET = LocalTime.of(15, 45);

    public ProfitShieldConfig {
        drawdownLookbackSessions = drawdownLookbackSessions <= 0 ? 126 : Math.min(252, Math.max(20, drawdownLookbackSessions));
        maximumDailyVolatilityPercent = posOrDefault(maximumDailyVolatilityPercent, "3");
        maximumDrawdownPercent = posOrDefault(maximumDrawdownPercent, "20");
        maximumDistanceFromHighPercent = posOrDefault(maximumDistanceFromHighPercent, "12");
        minimumAverageVolume = minimumAverageVolume <= 0 ? 300_000L : minimumAverageVolume;
        minimumStockPrice = posOrDefault(minimumStockPrice, "5");
        trendFilter = trendFilter == null ? TrendFilter.ABOVE_MA_50_AND_200 : trendFilter;
        entryDiscountPercent = nonNegativeOrDefault(entryDiscountPercent, "1");
        protectiveStopPercent = posOrDefault(protectiveStopPercent, "3");
        targetProfitPercent = posOrDefault(targetProfitPercent, "6");
        maxStocksToAdd = maxStocksToAdd <= 0 ? 10 : maxStocksToAdd;
        mode = mode == null ? StrategyMode.PAPER : mode;
        candidateSymbols = candidateSymbols == null ? List.of() : List.copyOf(candidateSymbols);
    }

    public static ProfitShieldConfig defaults(StrategyMode mode) {
        return new ProfitShieldConfig(126, new BigDecimal("3"), new BigDecimal("20"), new BigDecimal("12"),
                300_000L, new BigDecimal("5"), null, TrendFilter.ABOVE_MA_50_AND_200,
                new BigDecimal("1"), new BigDecimal("3"), new BigDecimal("6"), 10, mode, List.of());
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
     * Long-term-trend confirmation. A defensive book only shelters gains in names whose larger trend is
     * still intact, so the default requires both the 50- and 200-day averages to be reclaimed.
     */
    public enum TrendFilter { ABOVE_MA_50, ABOVE_MA_50_AND_200, DISABLED }
}
