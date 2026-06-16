package com.neuralarc.orb;

import com.neuralarc.model.StrategyMode;

import java.math.BigDecimal;
import java.time.Instant;

public record OrbRecommendation(
        String symbol,
        BigDecimal rangeHigh,
        BigDecimal rangeLow,
        BigDecimal plannedEntry,
        BigDecimal stop,
        BigDecimal target,
        int score,
        String rationale,
        BigDecimal riskPercent,
        BigDecimal rangePercent,
        int rangeDurationMinutes,
        OrbStatus status,
        StrategyMode mode,
        Instant addedTime
) {}
