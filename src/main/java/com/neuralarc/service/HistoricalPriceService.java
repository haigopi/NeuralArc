package com.neuralarc.service;

import com.neuralarc.api.AlpacaMarketDataApi;
import com.neuralarc.api.AlpacaMarketDataException;
import com.neuralarc.model.MarketBar;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class HistoricalPriceService {
    private final AlpacaMarketDataApi marketDataApi;

    public HistoricalPriceService(AlpacaMarketDataApi marketDataApi) {
        if (marketDataApi == null) {
            throw new IllegalArgumentException("marketDataApi must not be null");
        }
        this.marketDataApi = marketDataApi;
    }

    public List<MarketBar> getHistoricalPrices(String symbol, int days) throws AutoAnalyzeException {
        if (symbol == null || symbol.isBlank()) {
            throw new AutoAnalyzeException("Symbol must not be blank.");
        }
        if (days <= 0) {
            throw new AutoAnalyzeException("Historical day window must be greater than zero.");
        }
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(Math.max(days * 2L, days + 30L));
        try {
            List<MarketBar> bars = new ArrayList<>(marketDataApi.getDailyBars(symbol.trim().toUpperCase(), startDate, endDate));
            bars.sort(Comparator.comparing(MarketBar::timestamp, Comparator.nullsLast(String::compareTo)));
            return bars;
        } catch (AlpacaMarketDataException ex) {
            throw new AutoAnalyzeException("Failed to fetch historical prices: " + ex.getMessage(), ex);
        }
    }
}
