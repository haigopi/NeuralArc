package com.neuralarc.gaprocket;

import com.neuralarc.model.StrategyMode;

import java.math.BigDecimal;
import java.time.Instant;

public record GapRocketRecommendation(
        String symbol, String companyName, BigDecimal gapPercent, long premarketVolume,
        BigDecimal relativeVolume, BigDecimal currentPrice, BigDecimal previousClose,
        BigDecimal premarketHigh, BigDecimal premarketLow, GapRocketConfig.CatalystType catalystType,
        String catalystSummary, int strategyScore, GapRocketConfig.EntryStyle entryStyle,
        GapRocketConfig.OpeningRangeDuration openingRangeDuration, BigDecimal plannedEntryPrice,
        BigDecimal stopLossPercent, BigDecimal stopLossPrice, BigDecimal takeProfitPercent,
        BigDecimal takeProfitPrice, GapRocketStatus status, StrategyMode mode, Instant addedTime
) {}
