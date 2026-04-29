package com.neuralarc.api;

import com.neuralarc.model.MarketBar;

import java.time.LocalDate;
import java.util.List;

/**
 * Market data API boundary for fetching historical OHLCV bars from Alpaca.
 * Base URL: https://data.alpaca.markets
 */
public interface AlpacaMarketDataApi {

    /**
     * Fetch daily (1Day timeframe) bars for the given symbol between startDate (inclusive)
     * and endDate (inclusive).
     *
     * @param symbol    stock ticker symbol (e.g. "AAPL")
     * @param startDate start of the date range
     * @param endDate   end of the date range
     * @return list of daily bars, may be empty but never null
     * @throws AlpacaMarketDataException on API or network error
     */
    List<MarketBar> getDailyBars(String symbol, LocalDate startDate, LocalDate endDate)
            throws AlpacaMarketDataException;

    /**
     * Fetch intraday bars for the given symbol between startDate and endDate using
     * the specified interval in minutes. Handles Alpaca pagination automatically.
     *
     * @param symbol          stock ticker symbol
     * @param startDate       start of the date range
     * @param endDate         end of the date range
     * @param intervalMinutes bar interval in minutes (e.g. 15)
     * @return list of intraday bars, may be empty but never null
     * @throws AlpacaMarketDataException on API or network error
     */
    List<MarketBar> getIntradayBars(String symbol, LocalDate startDate, LocalDate endDate, int intervalMinutes)
            throws AlpacaMarketDataException;
}

