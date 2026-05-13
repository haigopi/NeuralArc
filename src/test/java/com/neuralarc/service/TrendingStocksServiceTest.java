package com.neuralarc.service;

import com.neuralarc.model.TrendingStock;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrendingStocksServiceTest {
    @Test
    void combinesMoversAndMostActivesWithoutDuplicateSymbols() {
        JSONObject movers = new JSONObject()
                .put("gainers", new JSONArray()
                        .put(new JSONObject()
                                .put("symbol", "NVDA")
                                .put("price", "125.42")
                                .put("percent_change", "7.5")))
                .put("losers", new JSONArray()
                        .put(new JSONObject()
                                .put("symbol", "TSLA")
                                .put("price", "210.00")
                                .put("percent_change", "-4.0")));
        JSONObject activeByVolume = new JSONObject()
                .put("most_actives", new JSONArray()
                        .put(new JSONObject().put("symbol", "NVDA").put("volume", "2000000").put("trade_count", "50000"))
                        .put(new JSONObject().put("symbol", "AAPL").put("volume", "1200000").put("trade_count", "30000")));
        JSONObject activeByTrades = new JSONObject()
                .put("most_actives", new JSONArray()
                        .put(new JSONObject().put("symbol", "AAPL").put("volume", "1200000").put("trade_count", "80000"))
                        .put(new JSONObject().put("symbol", "MSFT").put("volume", "900000").put("trade_count", "60000")));

        List<TrendingStock> result = TrendingStocksService.parseCandidates(movers, activeByVolume, activeByTrades);

        assertEquals(4, result.size());
        assertEquals(1, result.stream().filter(stock -> stock.symbol().equals("NVDA")).count());
        assertTrue(result.stream().anyMatch(stock -> stock.symbol().equals("AAPL")
                && stock.reason().contains("most active by volume")
                && stock.reason().contains("most active by trades")));
    }

    @Test
    void selectsTopFiveByBlendedScore() {
        List<TrendingStock> candidates = List.of(
                stock("AAA", "1"),
                stock("BBB", "6"),
                stock("CCC", "3"),
                stock("DDD", "10"),
                stock("EEE", "4"),
                stock("FFF", "8")
        );

        List<TrendingStock> selected = TrendingStocksService.selectTop(candidates, 5);

        assertEquals(List.of("DDD", "FFF", "BBB", "EEE", "CCC"),
                selected.stream().map(TrendingStock::symbol).toList());
    }

    @Test
    void topTrendingStocksHandlesEmptyScreenerResponses() throws Exception {
        TrendingStocksService service = new TrendingStocksService(new AlpacaScreenerClient() {
            @Override
            public JSONObject getMarketMovers(int top) {
                return new JSONObject();
            }

            @Override
            public JSONObject getMostActives(String by, int top) {
                return new JSONObject();
            }
        });

        assertTrue(service.topTrendingStocks(5).isEmpty());
    }

    @Test
    void topGainersAndLosersPreferNonPennyMovers() throws Exception {
        TrendingStocksService service = new TrendingStocksService(new AlpacaScreenerClient() {
            @Override
            public JSONObject getMarketMovers(int top) {
                return new JSONObject()
                        .put("gainers", new JSONArray()
                                .put(new JSONObject().put("symbol", "PENNY").put("price", "1.25").put("percent_change", "40").put("volume", "50000"))
                                .put(new JSONObject().put("symbol", "NVDA").put("price", "125").put("percent_change", "8").put("volume", "500000")))
                        .put("losers", new JSONArray()
                                .put(new JSONObject().put("symbol", "CHEAP").put("price", "2.00").put("percent_change", "-30").put("volume", "75000"))
                                .put(new JSONObject().put("symbol", "MSFT").put("price", "410").put("percent_change", "-5").put("volume", "300000")));
            }

            @Override
            public JSONObject getMostActives(String by, int top) {
                return new JSONObject();
            }
        });

        var groups = service.topGainersAndLosers(10);

        assertEquals(List.of("NVDA"), groups.gainers().stream().map(TrendingStock::symbol).toList());
        assertEquals(List.of("MSFT"), groups.losers().stream().map(TrendingStock::symbol).toList());
    }

    @Test
    void topGainersAndLosersFiltersStocksBelowMinimumVolumeThreshold() throws Exception {
        TrendingStocksService service = new TrendingStocksService(new AlpacaScreenerClient() {
            @Override
            public JSONObject getMarketMovers(int top) {
                return new JSONObject()
                        .put("gainers", new JSONArray()
                                .put(new JSONObject().put("symbol", "LOWVOL").put("price", "50.00").put("percent_change", "10").put("volume", "100000"))
                                .put(new JSONObject().put("symbol", "HIGHVOL").put("price", "150.00").put("percent_change", "5").put("volume", "500000")))
                        .put("losers", new JSONArray()
                                .put(new JSONObject().put("symbol", "LOWVOL2").put("price", "40.00").put("percent_change", "-8").put("volume", "150000"))
                                .put(new JSONObject().put("symbol", "HIGHVOL2").put("price", "200.00").put("percent_change", "-3").put("volume", "600000")));
            }

            @Override
            public JSONObject getMostActives(String by, int top) {
                return new JSONObject();
            }
        });

        var groups = service.topGainersAndLosers(10);

        assertEquals(List.of("HIGHVOL"), groups.gainers().stream().map(TrendingStock::symbol).toList());
        assertEquals(List.of("HIGHVOL2"), groups.losers().stream().map(TrendingStock::symbol).toList());
    }

    private TrendingStock stock(String symbol, String score) {
        return new TrendingStock(
                symbol,
                "",
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO,
                "",
                new java.math.BigDecimal(score)
        );
    }
}
