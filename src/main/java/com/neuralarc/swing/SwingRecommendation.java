package com.neuralarc.swing;

import com.neuralarc.model.StrategyMode;

import java.math.BigDecimal;
import java.time.Instant;

public record SwingRecommendation(
        String symbol, String companyName, BigDecimal currentPrice, BigDecimal recentHigh, BigDecimal pullbackPercent,
        BigDecimal previousClose, BigDecimal dayChangePercent, long averageVolume, BigDecimal relativeVolume,
        BigDecimal movingAverage20, BigDecimal movingAverage50, BigDecimal movingAverage200, int strategyScore,
        BigDecimal plannedEntryPrice, BigDecimal stopLossPercent, BigDecimal stopLossPrice,
        BigDecimal targetProfitPercent, BigDecimal targetPrice, BigDecimal rewardRiskRatio,
        SwingStatus status, StrategyMode mode, Instant addedTime
) {}
