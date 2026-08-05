package com.neuralarc.earningshunter;

import com.neuralarc.model.StrategyMode;

import java.math.BigDecimal;
import java.time.Instant;

public record EarningsHunterRecommendation(
        String symbol,
        String companyName,
        BigDecimal currentPrice,
        BigDecimal previousClose,
        BigDecimal dayChangePercent,
        long averageVolume,
        BigDecimal relativeVolume,
        String catalystSummary,
        int catalystScore,
        int strategyScore,
        BigDecimal plannedEntryPrice,
        BigDecimal stopLossPercent,
        BigDecimal stopLossPrice,
        BigDecimal targetProfitPercent,
        BigDecimal targetPrice,
        EarningsHunterStatus status,
        StrategyMode mode,
        Instant addedTime
) {}
