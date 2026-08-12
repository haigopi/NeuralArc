package com.neuralarc.rangerider;

import com.neuralarc.model.StrategyMode;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A scored Range Rider plan for one symbol: buy the typical daily dip, sell the typical daily rally,
 * and close the position the same session.
 *
 * @param referencePrice           the last completed session's close, which the planned prices are
 *                                 anchored to — today's partial bar never enters the plan
 * @param entryTouchRatePercent    share of lookback sessions that traded down to the planned dip
 * @param sameDayFillRatePercent   share of lookback sessions that traded down to the planned dip
 *                                 <em>and</em> back up to the planned rally — the plan's historical
 *                                 same-day completion rate
 * @param expectedGainPercent      gross percentage between the planned buy and planned sell prices
 */
public record RangeRiderRecommendation(
        String symbol, String companyName, BigDecimal referencePrice,
        BigDecimal averageOpen, BigDecimal averageHigh, BigDecimal averageLow,
        BigDecimal averageRangePercent, BigDecimal averageDipPercent, BigDecimal averageRallyPercent,
        BigDecimal rangeStabilityPercent,
        BigDecimal entryTouchRatePercent, BigDecimal sameDayFillRatePercent, int sessionsAnalyzed,
        BigDecimal previousClose, BigDecimal dayChangePercent, long averageVolume, BigDecimal relativeVolume,
        int strategyScore, BigDecimal plannedEntryPrice, BigDecimal targetPrice, BigDecimal expectedGainPercent,
        BigDecimal stopLossPercent, BigDecimal stopLossPrice,
        RangeRiderStatus status, StrategyMode mode, Instant addedTime
) {}
