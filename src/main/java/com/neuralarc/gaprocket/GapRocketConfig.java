package com.neuralarc.gaprocket;

import com.neuralarc.model.StrategyMode;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

public record GapRocketConfig(
        BigDecimal minimumPremarketGapPercent,
        long minimumPremarketVolume,
        BigDecimal minimumStockPrice,
        BigDecimal minimumRelativeVolume,
        BigDecimal maximumStockPrice,
        boolean newsCatalystRequired,
        Set<CatalystType> catalystTypes,
        MarketTrendFilter marketTrendFilter,
        EntryStyle entryStyle,
        OpeningRangeDuration openingRangeDuration,
        BigDecimal stopLossPercent,
        BigDecimal takeProfitPercent,
        int maxStocksToAdd,
        ExecutionFrequency executionFrequency,
        StrategyMode mode
) {
    public static final LocalTime PRIMARY_WINDOW_START_ET = LocalTime.of(9, 45);
    public static final LocalTime PRIMARY_WINDOW_END_ET = LocalTime.of(11, 0);

    public GapRocketConfig {
        minimumPremarketGapPercent = defaultIfNull(minimumPremarketGapPercent, "5");
        minimumStockPrice = defaultIfNull(minimumStockPrice, "5");
        minimumRelativeVolume = defaultIfNull(minimumRelativeVolume, "2");
        stopLossPercent = defaultIfNull(stopLossPercent, "1");
        takeProfitPercent = defaultIfNull(takeProfitPercent, "2");
        minimumPremarketVolume = minimumPremarketVolume <= 0 ? 1_000_000L : minimumPremarketVolume;
        catalystTypes = catalystTypes == null || catalystTypes.isEmpty()
                ? EnumSet.allOf(CatalystType.class)
                : EnumSet.copyOf(catalystTypes);
        marketTrendFilter = marketTrendFilter == null ? MarketTrendFilter.EITHER_SPY_OR_QQQ_GREEN : marketTrendFilter;
        entryStyle = entryStyle == null ? EntryStyle.BREAKOUT_RETEST : entryStyle;
        openingRangeDuration = openingRangeDuration == null ? OpeningRangeDuration.FIFTEEN_MINUTES : openingRangeDuration;
        maxStocksToAdd = maxStocksToAdd <= 0 ? 10 : maxStocksToAdd;
        executionFrequency = executionFrequency == null ? ExecutionFrequency.MANUAL : executionFrequency;
        mode = mode == null ? StrategyMode.PAPER : mode;
    }

    public static GapRocketConfig defaults(StrategyMode mode) {
        return new GapRocketConfig(new BigDecimal("5"), 1_000_000L, new BigDecimal("5"), new BigDecimal("2"),
                null, true, EnumSet.allOf(CatalystType.class), MarketTrendFilter.EITHER_SPY_OR_QQQ_GREEN,
                EntryStyle.BREAKOUT_RETEST, OpeningRangeDuration.FIFTEEN_MINUTES, new BigDecimal("1"),
                new BigDecimal("2"), 10, ExecutionFrequency.MANUAL, mode);
    }

    private static BigDecimal defaultIfNull(BigDecimal value, String fallback) {
        return value == null ? new BigDecimal(fallback) : value;
    }

    public enum CatalystType { EARNINGS, FDA_BIOTECH, ANALYST_UPGRADE, CONTRACT_PARTNERSHIP, GENERAL_BREAKING_NEWS }
    public enum MarketTrendFilter { SPY_GREEN, QQQ_GREEN, EITHER_SPY_OR_QQQ_GREEN, DISABLED }
    public enum EntryStyle { OPENING_RANGE_BREAKOUT, BREAKOUT_RETEST, PULLBACK_TO_VWAP, MANUAL_REVIEW_ONLY }
    public enum OpeningRangeDuration {
        FIVE_MINUTES(5), FIFTEEN_MINUTES(15), THIRTY_MINUTES(30);
        private final int minutes;
        OpeningRangeDuration(int minutes) { this.minutes = minutes; }
        public int minutes() { return minutes; }
    }
    public enum ExecutionFrequency { MANUAL, EVERY_5_MINUTES, EVERY_15_MINUTES, MARKET_OPEN_ONLY }
}
