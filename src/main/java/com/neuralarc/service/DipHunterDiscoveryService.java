package com.neuralarc.service;

import com.neuralarc.diphunter.DipHunterConfig;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Discovers live Dip Hunter candidate symbols from Alpaca's screener so the operator no longer has to
 * type tickers by hand. Pullbacks surface primarily among the day's decliners ("losers"), padded with
 * the most-active names for liquidity. Uses only live broker/market-data endpoints — never hardcoded
 * tickers, canned prices, or synthetic candidates.
 *
 * <p>The precise bar-based pullback/trend/relative-volume recompute and final scoring happen later in
 * {@code DipHunterLiveScanner} + {@code DipHunterAnalyzer}; this service only applies price bounds so
 * the downstream scanner stays the arbiter of which dips actually qualify.
 */
public final class DipHunterDiscoveryService {
    private static final Logger LOGGER = Logger.getLogger(DipHunterDiscoveryService.class.getName());

    private final AlpacaScreenerClient screener;

    public DipHunterDiscoveryService(AlpacaScreenerClient screener) {
        this.screener = Objects.requireNonNull(screener, "screener");
    }

    /**
     * Discover up to {@code maxSymbols} candidate tickers: the day's biggest decliners first (the
     * deepest pullbacks), then high-volume actives to fill remaining slots.
     *
     * @throws AlpacaScreenerException on API or network error
     */
    public List<String> discoverCandidates(DipHunterConfig config, int maxSymbols) throws AlpacaScreenerException {
        DipHunterConfig safeConfig = config == null ? DipHunterConfig.defaults(null) : config;
        int limit = Math.max(1, maxSymbols);

        LinkedHashSet<String> selected = new LinkedHashSet<>();

        // Primary source: the day's decliners — these are the names that have pulled back. The scanner
        // confirms which remain in an uptrend, so no gap/percent pre-filter is applied here.
        JSONObject movers = screener.getMarketMovers(Math.max(10, limit * 2));
        List<Loser> losers = parseLosers(movers).stream()
                .filter(l -> !l.symbol.isBlank())
                .filter(l -> passesPriceBoundsFilter(l, safeConfig))
                .toList();
        for (Loser loser : losers) {
            if (selected.size() >= limit) {
                break;
            }
            selected.add(loser.symbol);
        }

        // Secondary source: most actives by volume, to confirm liquidity and fill remaining slots.
        if (selected.size() < limit) {
            JSONObject actives = screener.getMostActives("volume", Math.max(20, limit * 4));
            for (String symbol : parseActiveSymbols(actives)) {
                if (selected.size() >= limit) {
                    break;
                }
                selected.add(symbol);
            }
        }

        List<String> result = new ArrayList<>(selected);
        LOGGER.info(() -> "Dip Hunter discovery selected " + result.size() + " candidate(s): " + result);
        return result;
    }

    private static boolean passesPriceBoundsFilter(Loser loser, DipHunterConfig config) {
        if (loser.price.compareTo(BigDecimal.ZERO) <= 0) {
            return true; // no price data — let the scanner decide
        }
        if (loser.price.compareTo(config.minimumStockPrice()) < 0) {
            return false;
        }
        return config.maximumStockPrice() == null
                || loser.price.compareTo(config.maximumStockPrice()) <= 0;
    }

    private static List<Loser> parseLosers(JSONObject body) {
        if (body == null) {
            return List.of();
        }
        JSONArray array = body.optJSONArray("losers");
        if (array == null) {
            return List.of();
        }
        List<Loser> losers = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String symbol = symbol(item);
            if (symbol.isBlank()) {
                continue;
            }
            losers.add(new Loser(symbol, decimal(item, "price", "latest_price", "close")));
        }
        return losers;
    }

    private static List<String> parseActiveSymbols(JSONObject body) {
        if (body == null) {
            return List.of();
        }
        JSONArray array = body.optJSONArray("most_actives");
        if (array == null) {
            array = body.optJSONArray("mostActives");
        }
        if (array == null) {
            return List.of();
        }
        List<String> symbols = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String symbol = symbol(item);
            if (!symbol.isBlank()) {
                symbols.add(symbol);
            }
        }
        return symbols;
    }

    private static String symbol(JSONObject item) {
        return item.optString("symbol", item.optString("S", "")).trim().toUpperCase(Locale.ROOT);
    }

    private static BigDecimal decimal(JSONObject item, String... keys) {
        for (String key : keys) {
            Object value = item.opt(key);
            if (value == null) {
                continue;
            }
            try {
                return new BigDecimal(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                // try the next key
            }
        }
        return BigDecimal.ZERO;
    }

    private record Loser(String symbol, BigDecimal price) {
    }
}
