package com.neuralarc.service;

import com.neuralarc.gaprocket.GapRocketConfig;
import com.neuralarc.model.StrategyMode;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GapAndGoDiscoveryServiceTest {

    private static GapRocketConfig config(String minGap, String minPrice, String maxPrice, int maxStocks) {
        return new GapRocketConfig(
                new BigDecimal(minGap), 1_000_000L, new BigDecimal(minPrice), new BigDecimal("2"),
                maxPrice == null ? null : new BigDecimal(maxPrice), false, null,
                GapRocketConfig.MarketTrendFilter.DISABLED, GapRocketConfig.EntryStyle.BREAKOUT_RETEST,
                GapRocketConfig.OpeningRangeDuration.FIFTEEN_MINUTES, new BigDecimal("1"), new BigDecimal("2"),
                maxStocks, GapRocketConfig.ExecutionFrequency.MANUAL, StrategyMode.PAPER);
    }

    @Test
    void ranksGainersByGapAndCapsToMax() throws Exception {
        JSONObject movers = new JSONObject().put("gainers", new JSONArray()
                .put(gainer("AAA", "10.00", "6.0"))
                .put(gainer("BBB", "20.00", "12.0"))
                .put(gainer("CCC", "15.00", "8.0")));
        FakeScreener screener = new FakeScreener(movers, new JSONObject());

        List<String> result = new GapAndGoDiscoveryService(screener)
                .discoverCandidates(config("5", "5", null, 2), 2);

        assertEquals(List.of("BBB", "CCC"), result);
    }

    @Test
    void filtersOutOfPriceRangeButKeepsLowGapGainers() throws Exception {
        // Discovery no longer pre-filters by gap — that is the scanner's job. Only price bounds apply.
        JSONObject movers = new JSONObject().put("gainers", new JSONArray()
                .put(gainer("LOWGAP", "30.00", "3.0"))   // below min gap, but within price range → kept
                .put(gainer("CHEAP", "2.00", "9.0"))      // below min price → filtered
                .put(gainer("PRICEY", "500.00", "9.0"))   // above max price → filtered
                .put(gainer("GOOD", "25.00", "7.0")));
        FakeScreener screener = new FakeScreener(movers, new JSONObject());

        List<String> result = new GapAndGoDiscoveryService(screener)
                .discoverCandidates(config("5", "5", "100", 10), 10);

        // Sorted by changePercent descending: GOOD (7%) before LOWGAP (3%)
        assertEquals(List.of("GOOD", "LOWGAP"), result);
    }

    @Test
    void fillsRemainingSlotsWithMostActivesWithoutDuplicates() throws Exception {
        JSONObject movers = new JSONObject().put("gainers", new JSONArray()
                .put(gainer("NVDA", "120.00", "7.0")));
        JSONObject actives = new JSONObject().put("most_actives", new JSONArray()
                .put(active("NVDA"))   // duplicate of a gainer
                .put(active("AAPL"))
                .put(active("MSFT")));
        FakeScreener screener = new FakeScreener(movers, actives);

        List<String> result = new GapAndGoDiscoveryService(screener)
                .discoverCandidates(config("5", "5", null, 10), 3);

        assertEquals(List.of("NVDA", "AAPL", "MSFT"), result);
        assertEquals(1, result.stream().filter("NVDA"::equals).count());
    }

    @Test
    void includesGainerWhenScreenerOmitsPrice() throws Exception {
        // Some payloads omit price; we cannot price-filter, so keep it for the bar-based recompute later.
        JSONObject movers = new JSONObject().put("gainers", new JSONArray()
                .put(new JSONObject().put("symbol", "NPRC").put("percent_change", "9.0")));
        FakeScreener screener = new FakeScreener(movers, new JSONObject());

        List<String> result = new GapAndGoDiscoveryService(screener)
                .discoverCandidates(config("5", "5", "100", 10), 10);

        assertTrue(result.contains("NPRC"));
    }

    @Test
    void returnsEmptyForEmptyScreenerResponses() throws Exception {
        FakeScreener screener = new FakeScreener(new JSONObject(), new JSONObject());

        List<String> result = new GapAndGoDiscoveryService(screener)
                .discoverCandidates(config("5", "5", null, 10), 10);

        assertTrue(result.isEmpty());
    }

    @Test
    void doesNotQueryMostActivesWhenGainersAlreadyFillTheLimit() throws Exception {
        JSONObject movers = new JSONObject().put("gainers", new JSONArray()
                .put(gainer("AAA", "10.00", "9.0"))
                .put(gainer("BBB", "10.00", "8.0")));
        FakeScreener screener = new FakeScreener(movers, new JSONObject());

        new GapAndGoDiscoveryService(screener).discoverCandidates(config("5", "5", null, 10), 2);

        assertFalse(screener.mostActivesQueried);
    }

    @Test
    void propagatesScreenerExceptions() {
        AlpacaScreenerClient screener = new AlpacaScreenerClient() {
            @Override
            public JSONObject getMarketMovers(int top) throws AlpacaScreenerException {
                throw new AlpacaScreenerException("boom");
            }

            @Override
            public JSONObject getMostActives(String by, int top) {
                return new JSONObject();
            }
        };

        assertThrows(AlpacaScreenerException.class,
                () -> new GapAndGoDiscoveryService(screener).discoverCandidates(config("5", "5", null, 10), 10));
    }

    private static JSONObject gainer(String symbol, String price, String percentChange) {
        return new JSONObject().put("symbol", symbol).put("price", price).put("percent_change", percentChange);
    }

    private static JSONObject active(String symbol) {
        return new JSONObject().put("symbol", symbol).put("volume", "5000000").put("trade_count", "40000");
    }

    private static final class FakeScreener implements AlpacaScreenerClient {
        private final JSONObject movers;
        private final JSONObject mostActives;
        private boolean mostActivesQueried;

        private FakeScreener(JSONObject movers, JSONObject mostActives) {
            this.movers = movers;
            this.mostActives = mostActives;
        }

        @Override
        public JSONObject getMarketMovers(int top) {
            return movers;
        }

        @Override
        public JSONObject getMostActives(String by, int top) {
            mostActivesQueried = true;
            return mostActives;
        }
    }
}
