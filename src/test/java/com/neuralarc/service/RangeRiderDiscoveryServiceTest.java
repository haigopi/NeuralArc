package com.neuralarc.service;

import com.neuralarc.model.StrategyMode;
import com.neuralarc.rangerider.RangeRiderConfig;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RangeRiderDiscoveryServiceTest {

    private static RangeRiderConfig config(String minPrice, String maxPrice, int maxStocks) {
        return new RangeRiderConfig(15, new BigDecimal("1"), new BigDecimal("12"), new BigDecimal("50"),
                new BigDecimal("40"), 1_000_000L, new BigDecimal(minPrice),
                maxPrice == null ? null : new BigDecimal(maxPrice),
                new BigDecimal("50"), new BigDecimal("0.5"), new BigDecimal("2"), maxStocks,
                RangeRiderConfig.ExecutionFrequency.MANUAL, StrategyMode.PAPER, List.of());
    }

    @Test
    void selectsMostActivesFirstThenCapsToMax() throws Exception {
        JSONObject actives = new JSONObject().put("most_actives", new JSONArray()
                .put(active("AAA"))
                .put(active("BBB"))
                .put(active("CCC")));
        FakeScreener screener = new FakeScreener(new JSONObject(), actives);

        List<String> result = new RangeRiderDiscoveryService(screener)
                .discoverCandidates(config("10", null, 2), 2);

        assertEquals(List.of("AAA", "BBB"), result);
    }

    @Test
    void doesNotQueryMoversWhenActivesAlreadyFillTheLimit() throws Exception {
        JSONObject actives = new JSONObject().put("most_actives", new JSONArray()
                .put(active("AAA"))
                .put(active("BBB")));
        FakeScreener screener = new FakeScreener(new JSONObject(), actives);

        new RangeRiderDiscoveryService(screener).discoverCandidates(config("10", null, 10), 2);

        assertFalse(screener.moversQueried);
    }

    @Test
    void fillsRemainingSlotsWithGainersAndLosersWithoutDuplicates() throws Exception {
        JSONObject actives = new JSONObject().put("most_actives", new JSONArray().put(active("NVDA")));
        JSONObject movers = new JSONObject()
                .put("gainers", new JSONArray().put(mover("NVDA", "120.00")).put(mover("AAPL", "200.00")))
                .put("losers", new JSONArray().put(mover("MSFT", "400.00")));
        FakeScreener screener = new FakeScreener(movers, actives);

        List<String> result = new RangeRiderDiscoveryService(screener)
                .discoverCandidates(config("10", null, 10), 3);

        assertEquals(List.of("NVDA", "AAPL", "MSFT"), result);
        assertEquals(1, result.stream().filter("NVDA"::equals).count());
    }

    @Test
    void filtersOutOfPriceRangeMovers() throws Exception {
        JSONObject movers = new JSONObject().put("gainers", new JSONArray()
                .put(mover("CHEAP", "2.00"))       // below min price
                .put(mover("PRICEY", "900.00"))    // above max price
                .put(mover("GOOD", "25.00")));
        FakeScreener screener = new FakeScreener(movers, new JSONObject());

        List<String> result = new RangeRiderDiscoveryService(screener)
                .discoverCandidates(config("10", "100", 10), 10);

        assertEquals(List.of("GOOD"), result);
    }

    @Test
    void includesMoverWhenScreenerOmitsPrice() throws Exception {
        JSONObject movers = new JSONObject().put("losers", new JSONArray()
                .put(new JSONObject().put("symbol", "NPRC")));
        FakeScreener screener = new FakeScreener(movers, new JSONObject());

        List<String> result = new RangeRiderDiscoveryService(screener)
                .discoverCandidates(config("10", "100", 10), 10);

        assertTrue(result.contains("NPRC"));
    }

    @Test
    void returnsEmptyForEmptyScreenerResponses() throws Exception {
        FakeScreener screener = new FakeScreener(new JSONObject(), new JSONObject());

        List<String> result = new RangeRiderDiscoveryService(screener)
                .discoverCandidates(config("10", null, 10), 10);

        assertTrue(result.isEmpty());
    }

    @Test
    void propagatesScreenerExceptions() {
        AlpacaScreenerClient screener = new AlpacaScreenerClient() {
            @Override
            public JSONObject getMarketMovers(int top) {
                return new JSONObject();
            }

            @Override
            public JSONObject getMostActives(String by, int top) throws AlpacaScreenerException {
                throw new AlpacaScreenerException("boom");
            }
        };

        assertThrows(AlpacaScreenerException.class,
                () -> new RangeRiderDiscoveryService(screener).discoverCandidates(config("10", null, 10), 10));
    }

    private static JSONObject mover(String symbol, String price) {
        return new JSONObject().put("symbol", symbol).put("price", price).put("percent_change", "3.0");
    }

    private static JSONObject active(String symbol) {
        return new JSONObject().put("symbol", symbol).put("volume", "5000000").put("trade_count", "40000");
    }

    private static final class FakeScreener implements AlpacaScreenerClient {
        private final JSONObject movers;
        private final JSONObject mostActives;
        private boolean moversQueried;

        private FakeScreener(JSONObject movers, JSONObject mostActives) {
            this.movers = movers;
            this.mostActives = mostActives;
        }

        @Override
        public JSONObject getMarketMovers(int top) {
            moversQueried = true;
            return movers;
        }

        @Override
        public JSONObject getMostActives(String by, int top) {
            return mostActives;
        }
    }
}
