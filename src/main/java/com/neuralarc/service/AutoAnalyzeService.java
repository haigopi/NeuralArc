package com.neuralarc.service;

import com.neuralarc.api.AlpacaMarketDataApi;
import com.neuralarc.api.AlpacaMarketDataException;
import com.neuralarc.model.AutoAnalyzeResult;
import com.neuralarc.model.MarketBar;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
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

    private final AlpacaMarketDataApi marketDataApi;

    public AutoAnalyzeService(AlpacaMarketDataApi marketDataApi) {
        if (marketDataApi == null) throw new IllegalArgumentException("marketDataApi must not be null");
        this.marketDataApi = marketDataApi;
    }

    /**
     * Analyze the given symbol over the last {@code monthsBack} months using
     * {@code intervalMinutes} for intraday threshold calculation.
     *
     * @param symbol          stock ticker, will be upper-cased
     * @param monthsBack      number of calendar months to look back (e.g. 6)
     * @param intervalMinutes intraday bar interval in minutes (must be &gt; 0)
     * @return completed {@link AutoAnalyzeResult}
     * @throws AutoAnalyzeException if data fetch or computation fails
     */
    public AutoAnalyzeResult analyze(String symbol, int monthsBack, int intervalMinutes)
            throws AutoAnalyzeException {

        if (symbol == null || symbol.isBlank()) {
            throw new AutoAnalyzeException("Symbol must not be blank.");
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

        // --- 6-month range (min/max of already-fetched daily bars) ---
        BigDecimal sixMonthLow  = minOf(dailyBars, "low");
        BigDecimal sixMonthHigh = maxOf(dailyBars, "high");

        // --- 52-week range (separate fetch, always 52 weeks back) ---
        LocalDate fiftyTwoWeekStart = endDate.minusWeeks(52);
        BigDecimal fiftyTwoWeekLow;
        BigDecimal fiftyTwoWeekHigh;
        try {
            List<MarketBar> yearlyBars = marketDataApi.getDailyBars(upperSymbol, fiftyTwoWeekStart, endDate);
            if (yearlyBars.isEmpty()) {
                fiftyTwoWeekLow  = sixMonthLow;
                fiftyTwoWeekHigh = sixMonthHigh;
            } else {
                fiftyTwoWeekLow  = minOf(yearlyBars, "low");
                fiftyTwoWeekHigh = maxOf(yearlyBars, "high");
            }
        } catch (AlpacaMarketDataException ex) {
            LOGGER.warning("Could not fetch 52-week bars for " + upperSymbol + "; falling back to 6-month range.");
            fiftyTwoWeekLow  = sixMonthLow;
            fiftyTwoWeekHigh = sixMonthHigh;
        }

        // --- Today's snapshot ---
        BigDecimal todayStockPrice = BigDecimal.ZERO;
        BigDecimal todayOpen = BigDecimal.ZERO;
        BigDecimal todayHighSoFar = BigDecimal.ZERO;
        boolean todayCloseAvailable = false;
        BigDecimal todayClose = BigDecimal.ZERO;
        try {
            List<MarketBar> todayDailyBars = marketDataApi.getDailyBars(upperSymbol, endDate, endDate);
            if (!todayDailyBars.isEmpty()) {
                MarketBar todayBar = todayDailyBars.get(todayDailyBars.size() - 1);
                todayOpen = todayBar.open();
                todayHighSoFar = todayBar.high();
                todayClose = todayBar.close();
                todayCloseAvailable = todayClose.compareTo(BigDecimal.ZERO) > 0;
            }

            List<MarketBar> todayIntradayBars = marketDataApi.getIntradayBars(upperSymbol, endDate, endDate, 1);
            if (!todayIntradayBars.isEmpty()) {
                todayStockPrice = todayIntradayBars.get(todayIntradayBars.size() - 1).close();
                if (todayHighSoFar.compareTo(BigDecimal.ZERO) <= 0) {
                    todayHighSoFar = maxOf(todayIntradayBars, "high");
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
                round2(sixMonthLow),
                round2(sixMonthHigh),
                round2(fiftyTwoWeekLow),
                round2(fiftyTwoWeekHigh),
                round2(todayStockPrice),
                round2(todayOpen),
                round2(todayHighSoFar),
                todayCloseAvailable,
                round2(todayClose),
                round2(threshold),
                dailyBars.size(),
                intradayBars.size(),
                Instant.now()
        );
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
}

