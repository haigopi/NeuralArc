package com.neuralarc.profitshield;

import com.neuralarc.model.StrategyMode;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A ranked Profit Shield plan for one symbol. {@code protectionScore} is the purely defensive
 * sub-score (quiet range, shallow drawdown, resilience); {@code strategyScore} adds trend and
 * liquidity on top and is what the grid ranks on.
 */
public record ProfitShieldRecommendation(
        String symbol,
        String companyName,
        BigDecimal currentPrice,
        BigDecimal previousClose,
        BigDecimal dayChangePercent,
        long averageVolume,
        BigDecimal relativeVolume,
        BigDecimal atrPercent,
        BigDecimal maxDrawdownPercent,
        BigDecimal distanceFromHighPercent,
        String protectionSummary,
        int protectionScore,
        int strategyScore,
        BigDecimal plannedEntryPrice,
        BigDecimal stopLossPercent,
        BigDecimal stopLossPrice,
        BigDecimal targetProfitPercent,
        BigDecimal targetPrice,
        ProfitShieldStatus status,
        StrategyMode mode,
        Instant addedTime
) {}
