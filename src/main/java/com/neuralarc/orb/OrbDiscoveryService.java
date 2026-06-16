package com.neuralarc.orb;

import com.neuralarc.service.AlpacaScreenerClient;
import com.neuralarc.service.AlpacaScreenerException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

public final class OrbDiscoveryService {
    private static final Logger LOGGER = Logger.getLogger(OrbDiscoveryService.class.getName());
    private final AlpacaScreenerClient screener;

    public OrbDiscoveryService(AlpacaScreenerClient screener) {
        this.screener = Objects.requireNonNull(screener, "screener");
    }

    public List<OrbCandidate> discoverCandidates(OrbConfig config, int maxSymbols) throws AlpacaScreenerException {
        OrbConfig safeConfig = config == null ? OrbConfig.defaults(null) : config;
        int limit = Math.max(1, maxSymbols);
        Map<String, OrbCandidate> selected = new LinkedHashMap<>();

        JSONObject movers = screener.getMarketMovers(Math.max(20, limit * 3));
        parseMovers(movers).stream()
                .filter(candidate -> passes(candidate, safeConfig))
                .sorted(Comparator.comparing(OrbCandidate::relativeVolume, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .forEach(candidate -> add(selected, candidate, limit));

        if (selected.size() < limit) {
            JSONObject activeByVolume = screener.getMostActives("volume", Math.max(20, limit * 4));
            parseActives(activeByVolume).stream()
                    .filter(candidate -> passes(candidate, safeConfig))
                    .forEach(candidate -> add(selected, candidate, limit));
        }

        List<OrbCandidate> result = new ArrayList<>(selected.values());
        LOGGER.info(() -> "ORB discovery selected " + result.size() + " candidate(s): "
                + result.stream().map(OrbCandidate::symbol).toList());
        return result;
    }

    public List<OrbCandidate> manualCandidates(OrbConfig config) {
        OrbConfig safeConfig = config == null ? OrbConfig.defaults(null) : config;
        return safeConfig.candidateSymbols().stream()
                .map(symbol -> new OrbCandidate(symbol, null, null, null, null, null, "manual"))
                .toList();
    }

    private static void add(Map<String, OrbCandidate> selected, OrbCandidate candidate, int limit) {
        if (selected.size() >= limit || candidate.symbol().isBlank()) return;
        selected.putIfAbsent(candidate.symbol(), candidate);
    }

    private static boolean passes(OrbCandidate candidate, OrbConfig config) {
        BigDecimal price = candidate.latestPrice();
        if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
            if (price.compareTo(config.minimumPrice()) < 0) return false;
            if (config.maximumPrice() != null && price.compareTo(config.maximumPrice()) > 0) return false;
        }
        return true;
    }

    private static List<OrbCandidate> parseMovers(JSONObject body) {
        if (body == null) return List.of();
        List<OrbCandidate> result = new ArrayList<>();
        parseMoverArray(body.optJSONArray("gainers"), "top mover gainer", result);
        parseMoverArray(body.optJSONArray("losers"), "top mover loser", result);
        return result;
    }

    private static void parseMoverArray(JSONArray array, String source, List<OrbCandidate> result) {
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            String symbol = symbol(item);
            if (symbol.isBlank()) continue;
            BigDecimal change = decimal(item, "percent_change", "change_percent", "change_pct").abs();
            result.add(new OrbCandidate(symbol, decimal(item, "price", "latest_price", "close"), null,
                    change.compareTo(BigDecimal.ZERO) > 0 ? change : null, null, null, source));
        }
    }

    private static List<OrbCandidate> parseActives(JSONObject body) {
        if (body == null) return List.of();
        JSONArray array = body.optJSONArray("most_actives");
        if (array == null) array = body.optJSONArray("mostActives");
        if (array == null) return List.of();
        List<OrbCandidate> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            String symbol = symbol(item);
            if (symbol.isBlank()) continue;
            result.add(new OrbCandidate(symbol, decimal(item, "price", "latest_price", "close"), null,
                    null, decimal(item, "volume"), null, "most active by volume"));
        }
        return result;
    }

    private static String symbol(JSONObject item) {
        String symbol = item.optString("symbol", item.optString("ticker", ""));
        return symbol == null ? "" : symbol.trim().toUpperCase();
    }

    private static BigDecimal decimal(JSONObject item, String... names) {
        for (String name : names) {
            Object value = item.opt(name);
            if (value == null || JSONObject.NULL.equals(value)) continue;
            try { return new BigDecimal(String.valueOf(value)); } catch (NumberFormatException ignored) { }
        }
        return BigDecimal.ZERO;
    }
}
