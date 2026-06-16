package com.neuralarc.orb;

import com.neuralarc.service.AlpacaScreenerClient;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrbDiscoveryServiceTest {
    @Test
    void discoversDistinctLiveCandidatesFromMoversAndActives() throws Exception {
        FakeScreener screener = new FakeScreener(
                new JSONObject().put("gainers", new JSONArray()
                        .put(new JSONObject().put("symbol", "nvda").put("price", "120").put("percent_change", "4"))),
                new JSONObject().put("most_actives", new JSONArray()
                        .put(new JSONObject().put("symbol", "NVDA").put("volume", "1000"))
                        .put(new JSONObject().put("symbol", "AMD").put("price", "80"))));

        List<OrbCandidate> candidates = new OrbDiscoveryService(screener)
                .discoverCandidates(OrbConfig.defaults(null), 3);

        assertEquals(List.of("NVDA", "AMD"), candidates.stream().map(OrbCandidate::symbol).toList());
        assertEquals(new BigDecimal("120"), candidates.getFirst().latestPrice());
    }

    @Test
    void manualSymbolsBypassScreener() {
        OrbConfig config = new OrbConfig(15, null, null, null, null, 10, null, null, null, null,
                null, List.of(" amd ", "AMD", "msft"), false, false, null);
        List<OrbCandidate> candidates = new OrbDiscoveryService(new FakeScreener(new JSONObject(), new JSONObject()))
                .manualCandidates(config);
        assertEquals(List.of("AMD", "MSFT"), candidates.stream().map(OrbCandidate::symbol).toList());
    }

    private record FakeScreener(JSONObject movers, JSONObject actives) implements AlpacaScreenerClient {
        @Override public JSONObject getMarketMovers(int top) { return movers; }
        @Override public JSONObject getMostActives(String by, int top) { return actives; }
    }
}
