package com.neuralarc.service;

import com.neuralarc.model.StrategyMode;
import com.neuralarc.vwap.VwapConfig;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VwapDiscoveryServiceTest {

    private static VwapConfig config(String minPrice, String maxPrice, int maxStocks) {
        return new VwapConfig(new BigDecimal("1"), new BigDecimal("8"), 500_000L, new BigDecimal(minPrice),
                new BigDecimal("1.0"), maxPrice == null ? null : new BigDecimal(maxPrice),
                VwapConfig.TrendFilter.ABOVE_MA_50, new BigDecimal("4"), maxStocks,
                VwapConfig.ExecutionFrequency.MANUAL, StrategyMode.PAPER, List.of());
    }

    @Test
    void selectsLosersFirstThenCapsToMax() throws Exception {
        JSONObject movers = new JSONObject().put("losers", new JSONArray()
                .put(loser("AAA", "10.00"))
                .put(loser("BBB", "20.00"))
                .put(loser("CCC", "15.00")));
        FakeScreener screener = new FakeScreener(movers, new JSONObject());

        List<String> result = new VwapDiscoveryService(screener)
                .discoverCandidates(config("5", null, 2), 2);

        assertEquals(List.of("AAA", "BBB"), result);
    }

    @Test
    void filtersOutOfPriceRangeLosersButKeepsTheRest() throws Exception {
        JSONObject movers = new JSONObject().put("losers", new JSONArray()
                .put(loser("CHEAP", "2.00"))      // below min price
                .put(loser("PRICEY", "500.00"))   // above max price
                .put(loser("GOOD", "25.00")));
        FakeScreener screener = new FakeScreener(movers, new JSONObject());

        List<String> result = new VwapDiscoveryService(screener)
                .discoverCandidates(config("5", "100", 10), 10);

        assertEquals(List.of("GOOD"), result);
    }

    @Test
    void fillsRemainingSlotsWithMostActivesWithoutDuplicates() throws Exception {
        JSONObject movers = new JSONObject().put("losers", new JSONArray()
                .put(loser("NVDA", "120.00")));
        JSONObject actives = new JSONObject().put("most_actives", new JSONArray()
                .put(active("NVDA"))   // duplicate of a loser
                .put(active("AAPL"))
                .put(active("MSFT")));
        FakeScreener screener = new FakeScreener(movers, actives);

        List<String> result = new VwapDiscoveryService(screener)
                .discoverCandidates(config("5", null, 10), 3);

        assertEquals(List.of("NVDA", "AAPL", "MSFT"), result);
        assertEquals(1, result.stream().filter("NVDA"::equals).count());
    }

    @Test
    void includesLoserWhenScreenerOmitsPrice() throws Exception {
        JSONObject movers = new JSONObject().put("losers", new JSONArray()
                .put(new JSONObject().put("symbol", "NPRC")));
        FakeScreener screener = new FakeScreener(movers, new JSONObject());

        List<String> result = new VwapDiscoveryService(screener)
                .discoverCandidates(config("5", "100", 10), 10);

        assertTrue(result.contains("NPRC"));
    }

    @Test
    void returnsEmptyForEmptyScreenerResponses() throws Exception {
        FakeScreener screener = new FakeScreener(new JSONObject(), new JSONObject());

        List<String> result = new VwapDiscoveryService(screener)
                .discoverCandidates(config("5", null, 10), 10);

        assertTrue(result.isEmpty());
    }

    @Test
    void doesNotQueryMostActivesWhenLosersAlreadyFillTheLimit() throws Exception {
        JSONObject movers = new JSONObject().put("losers", new JSONArray()
                .put(loser("AAA", "10.00"))
                .put(loser("BBB", "10.00")));
        FakeScreener screener = new FakeScreener(movers, new JSONObject());

        new VwapDiscoveryService(screener).discoverCandidates(config("5", null, 10), 2);

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
                () -> new VwapDiscoveryService(screener).discoverCandidates(config("5", null, 10), 10));
    }

    private static JSONObject loser(String symbol, String price) {
        return new JSONObject().put("symbol", symbol).put("price", price).put("percent_change", "-3.0");
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
