package com.neuralarc.diphunter;

import com.neuralarc.model.StrategyMode;

import java.math.BigDecimal;
import java.time.Instant;

public record DipHunterRecommendation(
        String symbol, String companyName, BigDecimal pullbackPercent, BigDecimal dayChangePercent,
        long averageVolume, BigDecimal relativeVolume, BigDecimal currentPrice, BigDecimal previousClose,
        BigDecimal recentHigh, BigDecimal movingAverage20, BigDecimal movingAverage50, int strategyScore,
        DipHunterConfig.BounceConfirmation bounceConfirmation, BigDecimal plannedEntryPrice,
        BigDecimal stopLossPercent, BigDecimal stopLossPrice, BigDecimal takeProfitPercent,
        BigDecimal takeProfitPrice, DipHunterStatus status, StrategyMode mode, Instant addedTime
) {}
