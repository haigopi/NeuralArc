package com.neuralarc.vwap;

import com.neuralarc.model.StrategyMode;

import java.math.BigDecimal;
import java.time.Instant;

public record VwapRecommendation(
        String symbol, String companyName, BigDecimal currentPrice, BigDecimal vwap, BigDecimal discountPercent,
        BigDecimal previousClose, BigDecimal dayChangePercent, long averageVolume, BigDecimal relativeVolume,
        BigDecimal movingAverage50, BigDecimal movingAverage200, int strategyScore, BigDecimal plannedEntryPrice,
        BigDecimal stopLossPercent, BigDecimal stopLossPrice, BigDecimal targetPrice, BigDecimal reversionUpsidePercent,
        VwapStatus status, StrategyMode mode, Instant addedTime
) {}
