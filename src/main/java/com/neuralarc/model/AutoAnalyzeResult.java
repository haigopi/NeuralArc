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
        BigDecimal oneWeekLow,
        BigDecimal oneWeekHigh,
        BigDecimal twoWeekLow,
        BigDecimal twoWeekHigh,
        BigDecimal oneMonthLow,
        BigDecimal oneMonthHigh,
        BigDecimal twoMonthLow,
        BigDecimal twoMonthHigh,
        BigDecimal fourMonthLow,
        BigDecimal fourMonthHigh,
        BigDecimal sixMonthLow,
        BigDecimal sixMonthHigh,
        BigDecimal eightMonthLow,
        BigDecimal eightMonthHigh,
        BigDecimal oneYearLow,
        BigDecimal oneYearHigh,
        BigDecimal todayStockPrice,
        BigDecimal todayOpen,
        BigDecimal todayHighSoFar,
        BigDecimal todayLowSoFar,
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
