package com.neuralarc.service;

import com.neuralarc.api.AlpacaMarketDataApi;
import com.neuralarc.api.AlpacaMarketDataException;
import com.neuralarc.model.AutoAnalyzeBundle;
import com.neuralarc.model.AutoAnalyzeResult;
import com.neuralarc.model.MarketBar;
import com.neuralarc.model.StrategyRecommendation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Service that fetches historical market data from Alpaca and computes the five
 * Auto Analyze metrics for a given stock symbol.
 *
 * <p>Metrics computed:
 * <ul>
 *   <li>Average Daily Open  – mean of each trading day's open price</li>
 *   <li>Average Daily Close – mean of each trading day's close price</li>
 *   <li>Average Daily Low   – mean of each trading day's low price</li>
 *   <li>Average Daily High  – mean of each trading day's high price</li>
 *   <li>Threshold Number    – mean of all intraday bar <em>close</em> prices at the
 *       configured interval (default 15 min) over the same period.
 *       Bar close price ("c") is used because it represents the last traded price
 *       within each bar window and is the most reliable single-price field available
 *       from the Alpaca bars endpoint.</li>
 * </ul>
 * </p>
 */
public class AutoAnalyzeService {

    private static final Logger LOGGER = Logger.getLogger(AutoAnalyzeService.class.getName());
    private static final int PRICE_SCALE = 8;
    private static final int MIN_MONTHS_BACK = 1;
    private static final int MAX_MONTHS_BACK = 12;

    private final AlpacaMarketDataApi marketDataApi;
    private final HistoricalPriceService historicalPriceService;
    private final RecommendationEngine recommendationEngine;

    public AutoAnalyzeService(AlpacaMarketDataApi marketDataApi) {
        this(marketDataApi, new HistoricalPriceService(marketDataApi), new RecommendationEngine());
    }

    AutoAnalyzeService(
            AlpacaMarketDataApi marketDataApi,
            HistoricalPriceService historicalPriceService,
            RecommendationEngine recommendationEngine
    ) {
        if (marketDataApi == null) throw new IllegalArgumentException("marketDataApi must not be null");
        this.marketDataApi = marketDataApi;
        this.historicalPriceService = historicalPriceService;
        this.recommendationEngine = recommendationEngine;
    }

    /**
     * Analyze the given symbol over the last {@code monthsBack} months using
     * {@code intervalMinutes} for intraday threshold calculation.
     *
     * @param symbol          stock ticker, will be upper-cased
     * @param monthsBack      number of calendar months to look back (1..12)
     * @param intervalMinutes intraday bar interval in minutes (must be &gt; 0)
     * @return completed {@link AutoAnalyzeResult}
     * @throws AutoAnalyzeException if data fetch or computation fails
     */
    public AutoAnalyzeResult analyze(String symbol, int monthsBack, int intervalMinutes)
            throws AutoAnalyzeException {

        if (symbol == null || symbol.isBlank()) {
            throw new AutoAnalyzeException("Symbol must not be blank.");
        }
        if (monthsBack < MIN_MONTHS_BACK || monthsBack > MAX_MONTHS_BACK) {
            throw new AutoAnalyzeException("Months back must be between 1 and 12.");
        }
        if (intervalMinutes <= 0) {
            throw new AutoAnalyzeException("Interval must be a positive number of minutes.");
        }
        String upperSymbol = symbol.trim().toUpperCase();
        LocalDate endDate   = LocalDate.now();
        LocalDate startDate = endDate.minusMonths(monthsBack);

        LOGGER.info(() -> "AutoAnalyze: symbol=" + upperSymbol
                + " start=" + startDate + " end=" + endDate
                + " intervalMinutes=" + intervalMinutes);

        // --- Fetch daily bars ---
        List<MarketBar> dailyBars;
        try {
            dailyBars = marketDataApi.getDailyBars(upperSymbol, startDate, endDate);
        } catch (AlpacaMarketDataException ex) {
            throw new AutoAnalyzeException("Failed to fetch daily bars: " + ex.getMessage(), ex);
        }

        if (dailyBars.isEmpty()) {
            throw new AutoAnalyzeException("No daily market data returned for " + upperSymbol
                    + " between " + startDate + " and " + endDate
                    + ". The symbol may be invalid or the market was closed for the entire period.");
        }

        // --- Fetch intraday bars ---
        List<MarketBar> intradayBars;
        try {
            intradayBars = marketDataApi.getIntradayBars(upperSymbol, startDate, endDate, intervalMinutes);
        } catch (AlpacaMarketDataException ex) {
            throw new AutoAnalyzeException("Failed to fetch intraday bars: " + ex.getMessage(), ex);
        }

        // --- Compute metrics ---
        BigDecimal avgOpen      = averageOf(dailyBars, "open");
        BigDecimal avgClose     = averageOf(dailyBars, "close");
        BigDecimal avgLow       = averageOf(dailyBars, "low");
        BigDecimal avgHigh      = averageOf(dailyBars, "high");
        BigDecimal threshold    = intradayBars.isEmpty()
                ? BigDecimal.ZERO
                : averageOf(intradayBars, "close");

        // --- Price ranges (1w -> 1y) ---
        BigDecimal fallbackLow = minOf(dailyBars, "low");
        BigDecimal fallbackHigh = maxOf(dailyBars, "high");
        List<MarketBar> oneYearBars;
        try {
            oneYearBars = marketDataApi.getDailyBars(upperSymbol, endDate.minusYears(1), endDate);
            if (oneYearBars.isEmpty()) {
                oneYearBars = dailyBars;
            }
        } catch (AlpacaMarketDataException ex) {
            LOGGER.warning("Could not fetch 1-year bars for " + upperSymbol + "; falling back to analysis range.");
            oneYearBars = dailyBars;
        }

        PriceRange oneWeekRange = rangeForWindow(oneYearBars, endDate.minusWeeks(1), fallbackLow, fallbackHigh);
        PriceRange twoWeekRange = rangeForWindow(oneYearBars, endDate.minusWeeks(2), fallbackLow, fallbackHigh);
        PriceRange oneMonthRange = rangeForWindow(oneYearBars, endDate.minusMonths(1), fallbackLow, fallbackHigh);
        PriceRange twoMonthRange = rangeForWindow(oneYearBars, endDate.minusMonths(2), fallbackLow, fallbackHigh);
        PriceRange fourMonthRange = rangeForWindow(oneYearBars, endDate.minusMonths(4), fallbackLow, fallbackHigh);
        PriceRange sixMonthRange = rangeForWindow(oneYearBars, endDate.minusMonths(6), fallbackLow, fallbackHigh);
        PriceRange eightMonthRange = rangeForWindow(oneYearBars, endDate.minusMonths(8), fallbackLow, fallbackHigh);
        PriceRange oneYearRange = rangeForWindow(oneYearBars, endDate.minusYears(1), fallbackLow, fallbackHigh);

        // --- Today's snapshot ---
        BigDecimal todayStockPrice = BigDecimal.ZERO;
        BigDecimal todayOpen = BigDecimal.ZERO;
        BigDecimal todayHighSoFar = BigDecimal.ZERO;
        BigDecimal todayLowSoFar = BigDecimal.ZERO;
        boolean todayCloseAvailable = false;
        BigDecimal todayClose = BigDecimal.ZERO;
        try {
            List<MarketBar> todayDailyBars = marketDataApi.getDailyBars(upperSymbol, endDate, endDate);
            if (!todayDailyBars.isEmpty()) {
                MarketBar todayBar = todayDailyBars.get(todayDailyBars.size() - 1);
                todayOpen = todayBar.open();
                todayHighSoFar = todayBar.high();
                todayLowSoFar = todayBar.low();
                todayClose = todayBar.close();
                todayCloseAvailable = todayClose.compareTo(BigDecimal.ZERO) > 0;
            }

            List<MarketBar> todayIntradayBars = marketDataApi.getIntradayBars(upperSymbol, endDate, endDate, 1);
            if (!todayIntradayBars.isEmpty()) {
                todayStockPrice = todayIntradayBars.get(todayIntradayBars.size() - 1).close();
                if (todayHighSoFar.compareTo(BigDecimal.ZERO) <= 0) {
                    todayHighSoFar = maxOf(todayIntradayBars, "high");
                }
                if (todayLowSoFar.compareTo(BigDecimal.ZERO) <= 0) {
                    todayLowSoFar = minOf(todayIntradayBars, "low");
                }
                if (todayOpen.compareTo(BigDecimal.ZERO) <= 0) {
                    todayOpen = todayIntradayBars.get(0).open();
                }
            } else if (todayCloseAvailable) {
                todayStockPrice = todayClose;
            }
        } catch (AlpacaMarketDataException ex) {
            LOGGER.warning("Could not fetch today's snapshot for " + upperSymbol + ": " + ex.getMessage());
        }

        return new AutoAnalyzeResult(
                upperSymbol,
                startDate,
                endDate,
                intervalMinutes,
                round2(avgOpen),
                round2(avgClose),
                round2(avgLow),
                round2(avgHigh),
                round2(oneWeekRange.low()),
                round2(oneWeekRange.high()),
                round2(twoWeekRange.low()),
                round2(twoWeekRange.high()),
                round2(oneMonthRange.low()),
                round2(oneMonthRange.high()),
                round2(twoMonthRange.low()),
                round2(twoMonthRange.high()),
                round2(fourMonthRange.low()),
                round2(fourMonthRange.high()),
                round2(sixMonthRange.low()),
                round2(sixMonthRange.high()),
                round2(eightMonthRange.low()),
                round2(eightMonthRange.high()),
                round2(oneYearRange.low()),
                round2(oneYearRange.high()),
                round2(todayStockPrice),
                round2(todayOpen),
                round2(todayHighSoFar),
                round2(todayLowSoFar),
                todayCloseAvailable,
                round2(todayClose),
                round2(threshold),
                dailyBars.size(),
                intradayBars.size(),
                Instant.now()
        );
    }

    public AutoAnalyzeBundle analyzeBundle(String symbol, int monthsBack, int intervalMinutes)
            throws AutoAnalyzeException {
        AutoAnalyzeResult result = analyze(symbol, monthsBack, intervalMinutes);
        List<MarketBar> history = historicalPriceService.getHistoricalPrices(result.symbol(), 365);
        BigDecimal currentPrice = result.todayStockPrice().compareTo(BigDecimal.ZERO) > 0
                ? result.todayStockPrice()
                : latestClose(history);
        StrategyRecommendation shortTerm = recommendationEngine.generateShortTermRecommendation(result.symbol(), history, currentPrice);
        StrategyRecommendation longTerm = recommendationEngine.generateLongTermRecommendation(result.symbol(), history, currentPrice);
        return new AutoAnalyzeBundle(result, shortTerm, longTerm);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Average the requested price field across all bars.
     * @param field one of "open", "close", "low", "high"
     */
    private BigDecimal averageOf(List<MarketBar> bars, String field) {
        if (bars.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = BigDecimal.ZERO;
        for (MarketBar bar : bars) {
            sum = sum.add(priceField(bar, field));
        }
        return sum.divide(new BigDecimal(bars.size()), PRICE_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal minOf(List<MarketBar> bars, String field) {
        if (bars.isEmpty()) return BigDecimal.ZERO;
        BigDecimal min = priceField(bars.get(0), field);
        for (MarketBar bar : bars) {
            BigDecimal val = priceField(bar, field);
            if (val.compareTo(min) < 0) min = val;
        }
        return min;
    }

    private BigDecimal maxOf(List<MarketBar> bars, String field) {
        if (bars.isEmpty()) return BigDecimal.ZERO;
        BigDecimal max = priceField(bars.get(0), field);
        for (MarketBar bar : bars) {
            BigDecimal val = priceField(bar, field);
            if (val.compareTo(max) > 0) max = val;
        }
        return max;
    }

    private PriceRange rangeForWindow(List<MarketBar> bars, LocalDate startDateInclusive,
                                      BigDecimal fallbackLow, BigDecimal fallbackHigh) {
        List<MarketBar> windowBars = filterByStartDate(bars, startDateInclusive);
        if (windowBars.isEmpty()) {
            return new PriceRange(fallbackLow, fallbackHigh);
        }
        return new PriceRange(minOf(windowBars, "low"), maxOf(windowBars, "high"));
    }

    private List<MarketBar> filterByStartDate(List<MarketBar> bars, LocalDate startDateInclusive) {
        List<MarketBar> filtered = new ArrayList<>();
        for (MarketBar bar : bars) {
            LocalDate barDate = toBarDate(bar);
            if (barDate != null && !barDate.isBefore(startDateInclusive)) {
                filtered.add(bar);
            }
        }
        return filtered;
    }

    private LocalDate toBarDate(MarketBar bar) {
        if (bar == null || bar.timestamp() == null || bar.timestamp().isBlank()) {
            return null;
        }
        String ts = bar.timestamp().trim();
        try {
            return Instant.parse(ts).atZone(ZoneOffset.UTC).toLocalDate();
        } catch (Exception ignored) {
            try {
                if (ts.length() >= 10) {
                    return LocalDate.parse(ts.substring(0, 10));
                }
            } catch (Exception ignored2) {
                return null;
            }
        }
        return null;
    }

    private BigDecimal priceField(MarketBar bar, String field) {
        return switch (field) {
            case "open"  -> bar.open();
            case "close" -> bar.close();
            case "low"   -> bar.low();
            case "high"  -> bar.high();
            default      -> BigDecimal.ZERO;
        };
    }

    private BigDecimal round2(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal latestClose(List<MarketBar> bars) {
        if (bars == null || bars.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return round2(bars.get(bars.size() - 1).close());
    }

    private record PriceRange(BigDecimal low, BigDecimal high) {}
}
