package com.neuralarc.ui;

import com.neuralarc.api.AlpacaMarketDataApi;
import com.neuralarc.model.MarketBar;
import com.neuralarc.model.TrendingStock;
import com.neuralarc.model.TrendingStockGroups;
import com.neuralarc.service.HttpAlpacaScreenerClient;
import com.neuralarc.service.TrendingStocksService;
import com.neuralarc.service.WeekendReboundScoreService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Fetches the live candidate list for a Smart Picks strategy — the day's movers, the diversified
 * leaders, or Friday's rebound candidates. Shared by the scheduled Smart Picks workspace scans and the
 * liquidation re-entry, so both pick from exactly the same universe as the interactive dialog.
 */
final class SmartPicksUniverseLoader {
    private SmartPicksUniverseLoader() {
    }

    static List<TrendingStock> load(
            PortfolioCaptureSmartPicksStrategy strategy,
            String apiKey,
            String apiSecret,
            AlpacaMarketDataApi marketDataApi,
            String logTag,
            Consumer<String> log
    ) throws Exception {
        if (strategy == PortfolioCaptureSmartPicksStrategy.DIVERSIFIED_TOP_20) {
            List<TrendingStock> stocks = SmartPicksTrendingStocksDialog.diversifiedTop20Stocks(
                    symbol -> latestPrice(symbol, marketDataApi, logTag, log));
            log.accept(logTag + " Using Diversified Leaders (Top 20). symbols="
                    + stocks.stream().map(TrendingStock::symbol).toList());
            return stocks;
        }
        if (strategy == PortfolioCaptureSmartPicksStrategy.WEEKEND_REBOUND) {
            TrendingStocksService trendingService = new TrendingStocksService(new HttpAlpacaScreenerClient(apiKey, apiSecret));
            List<TrendingStock> stocks = new WeekendReboundScoreService().topStocks(trendingService, marketDataApi, 20);
            log.accept(logTag + " Using Weekend Rebound. symbols=" + stocks.stream().map(TrendingStock::symbol).toList());
            return stocks;
        }
        TrendingStockGroups groups = new TrendingStocksService(new HttpAlpacaScreenerClient(apiKey, apiSecret))
                .topGainersAndLosers(10);
        List<TrendingStock> stocks = new ArrayList<>();
        stocks.addAll(groups.gainers());
        stocks.addAll(groups.losers());
        log.accept(logTag + " Using High Volatility Movers. gainers="
                + groups.gainers().stream().map(TrendingStock::symbol).toList()
                + " losers=" + groups.losers().stream().map(TrendingStock::symbol).toList());
        return stocks;
    }

    private static BigDecimal latestPrice(String symbol, AlpacaMarketDataApi marketDataApi, String logTag,
                                          Consumer<String> log) {
        try {
            List<MarketBar> bars = marketDataApi.getIntradayBars(symbol, LocalDate.now().minusDays(5), LocalDate.now(), 15);
            if (bars == null || bars.isEmpty()) {
                return BigDecimal.ZERO;
            }
            return bars.get(bars.size() - 1).close();
        } catch (Exception ex) {
            log.accept(logTag + " Price fetch fallback used for " + symbol + ": " + ex.getMessage());
            return BigDecimal.ZERO;
        }
    }
}
