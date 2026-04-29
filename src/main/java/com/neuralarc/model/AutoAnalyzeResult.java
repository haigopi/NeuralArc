package com.neuralarc.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Result of an Auto Analyze computation over a symbol's historical market data.
 * All price averages are computed from Alpaca Market Data bars.
 */
public record AutoAnalyzeResult(
        String symbol,
        LocalDate startDate,
        LocalDate endDate,
        int intervalMinutes,
        BigDecimal averageDailyOpen,
        BigDecimal averageDailyClose,
        BigDecimal averageDailyLow,
        BigDecimal averageDailyHigh,
        // Lowest daily low across the analysis period (monthsBack months).
        BigDecimal sixMonthLow,
        // Highest daily high across the analysis period (monthsBack months).
        BigDecimal sixMonthHigh,
        BigDecimal fiftyTwoWeekLow,
        BigDecimal fiftyTwoWeekHigh,
        BigDecimal todayStockPrice,
        BigDecimal todayOpen,
        BigDecimal todayHighSoFar,
        boolean todayCloseAvailable,
        BigDecimal todayClose,
        /**
         * Threshold Number: average of all intraday bar close prices over the selected period.
         * Uses bar close price as the interval price (Alpaca bar 'c' field).
         */
        BigDecimal thresholdNumber,
        int dailyBarsProcessed,
        int intradayBarsProcessed,
        Instant analyzedAt
) {}

