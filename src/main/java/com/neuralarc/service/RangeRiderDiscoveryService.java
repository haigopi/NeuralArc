package com.neuralarc.service;

import com.neuralarc.rangerider.RangeRiderConfig;
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
 * Discovers live Range Rider candidate symbols from Alpaca's screener so the operator no longer has to
 * type tickers by hand. A same-day income plan needs stocks that trade heavily every session — a
 * repeatable daily range is only tradeable when there is size on both sides of it — so the day's
 * most-active names by volume are the primary source, padded with the day's biggest movers for
 * breadth. Uses only live broker/market-data endpoints — never hardcoded tickers, canned prices, or
 * synthetic candidates.
 *
 * <p>The multi-week open/high/low averaging, the same-day fill-rate replay, and the final scoring all
 * happen later in {@code RangeRiderLiveScanner} + {@code RangeRiderAnalyzer}; this service only applies
 * price bounds so the downstream scanner stays the arbiter of which daily ranges actually qualify.
 */
public final class RangeRiderDiscoveryService {
    private static final Logger LOGGER = Logger.getLogger(RangeRiderDiscoveryService.class.getName());

    private final AlpacaScreenerClient screener;

    public RangeRiderDiscoveryService(AlpacaScreenerClient screener) {
        this.screener = Objects.requireNonNull(screener, "screener");
    }

    /**
     * Discover up to {@code maxSymbols} candidate tickers: the most actively traded names first, then
     * the day's movers to fill remaining slots.
     *
     * @throws AlpacaScreenerException on API or network error
     */
    public List<String> discoverCandidates(RangeRiderConfig config, int maxSymbols) throws AlpacaScreenerException {
        RangeRiderConfig safeConfig = config == null ? RangeRiderConfig.defaults(null) : config;
        int limit = Math.max(1, maxSymbols);

        LinkedHashSet<String> selected = new LinkedHashSet<>();

        // Primary source: the day's most actively traded stocks. Volume is what makes a repeatable
        // daily range tradeable, and the scanner re-checks average volume against the configured floor.
        JSONObject actives = screener.getMostActives("volume", Math.max(20, limit * 2));
        for (String symbol : parseActiveSymbols(actives)) {
            if (selected.size() >= limit) {
                break;
            }
            selected.add(symbol);
        }

        // Secondary source: the day's movers, for breadth beyond the usual most-active roster.
        if (selected.size() < limit) {
            JSONObject movers = screener.getMarketMovers(Math.max(10, limit * 2));
            for (Mover mover : parseMovers(movers)) {
                if (selected.size() >= limit) {
                    break;
                }
                if (passesPriceBoundsFilter(mover, safeConfig)) {
                    selected.add(mover.symbol);
                }
            }
        }

        List<String> result = new ArrayList<>(selected);
        LOGGER.info(() -> "Range Rider discovery selected " + result.size() + " candidate(s): " + result);
        return result;
    }

    private static boolean passesPriceBoundsFilter(Mover mover, RangeRiderConfig config) {
        if (mover.price.compareTo(BigDecimal.ZERO) <= 0) {
            return true; // no price data — let the scanner decide
        }
        if (mover.price.compareTo(config.minimumStockPrice()) < 0) {
            return false;
        }
        return config.maximumStockPrice() == null
                || mover.price.compareTo(config.maximumStockPrice()) <= 0;
    }

    /** Gainers and losers alike: both describe stocks that are moving enough to have a daily range. */
    private static List<Mover> parseMovers(JSONObject body) {
        if (body == null) {
            return List.of();
        }
        List<Mover> movers = new ArrayList<>();
        for (String key : List.of("gainers", "losers")) {
            JSONArray array = body.optJSONArray(key);
            if (array == null) {
                continue;
            }
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String symbol = symbol(item);
                if (!symbol.isBlank()) {
                    movers.add(new Mover(symbol, decimal(item, "price", "latest_price", "close")));
                }
            }
        }
        return movers;
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

    private record Mover(String symbol, BigDecimal price) {
    }
}
