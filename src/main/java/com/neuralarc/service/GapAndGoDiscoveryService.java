package com.neuralarc.service;

import com.neuralarc.gaprocket.GapRocketConfig;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Discovers live gap-and-go candidate symbols from Alpaca's screener so the operator no longer has
 * to type tickers by hand. Uses only live broker/market-data endpoints — never hardcoded tickers,
 * canned prices, or synthetic candidates.
 *
 * <p>The precise bar-based gap/relative-volume recompute and final scoring happen later in
 * {@code GapRocketLiveScanner} + {@code GapRocketAnalyzer}; this service deliberately over-fetches a
 * broader candidate set so that downstream filtering still lands a full top-N.
 */
public final class GapAndGoDiscoveryService {
    private static final Logger LOGGER = Logger.getLogger(GapAndGoDiscoveryService.class.getName());

    private final AlpacaScreenerClient screener;

    public GapAndGoDiscoveryService(AlpacaScreenerClient screener) {
        this.screener = Objects.requireNonNull(screener, "screener");
    }

    /**
     * Discover up to {@code maxSymbols} candidate tickers ranked by premarket gap strength, padded
     * with high-volume names so downstream scanning has enough to work with.
     *
     * @param config     the gap-and-go configuration providing price/gap pre-filter thresholds
     * @param maxSymbols upper bound on the number of symbols returned
     * @return distinct, upper-cased symbols, never null
     * @throws AlpacaScreenerException on API or network error
     */
    public List<String> discoverCandidates(GapRocketConfig config, int maxSymbols) throws AlpacaScreenerException {
        GapRocketConfig safeConfig = config == null ? GapRocketConfig.defaults(null) : config;
        int limit = Math.max(1, maxSymbols);

        LinkedHashSet<String> selected = new LinkedHashSet<>();

        // Primary source: market movers (gainers carry price + percent_change for a real gap filter).
        JSONObject movers = screener.getMarketMovers(Math.max(10, limit * 2));
        List<Gainer> gainers = parseGainers(movers).stream()
                .filter(g -> passesGapFilter(g, safeConfig))
                .sorted(Comparator.comparing((Gainer g) -> g.changePercent).reversed())
                .toList();
        for (Gainer gainer : gainers) {
            if (selected.size() >= limit) {
                break;
            }
            selected.add(gainer.symbol);
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
        LOGGER.info(() -> "Gap-and-Go discovery selected " + result.size() + " candidate(s): " + result);
        return result;
    }

    private static boolean passesGapFilter(Gainer gainer, GapRocketConfig config) {
        if (gainer.symbol.isBlank()) {
            return false;
        }
        if (gainer.changePercent.compareTo(config.minimumPremarketGapPercent()) < 0) {
            return false;
        }
        // Apply price bounds only when the screener supplied a usable price.
        if (gainer.price.compareTo(BigDecimal.ZERO) > 0) {
            if (gainer.price.compareTo(config.minimumStockPrice()) < 0) {
                return false;
            }
            return config.maximumStockPrice() == null
                    || gainer.price.compareTo(config.maximumStockPrice()) <= 0;
        }
        return true;
    }

    private static List<Gainer> parseGainers(JSONObject body) {
        if (body == null) {
            return List.of();
        }
        JSONArray array = body.optJSONArray("gainers");
        if (array == null) {
            return List.of();
        }
        List<Gainer> gainers = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String symbol = symbol(item);
            if (symbol.isBlank()) {
                continue;
            }
            gainers.add(new Gainer(
                    symbol,
                    decimal(item, "price", "latest_price", "close"),
                    decimal(item, "percent_change", "change_percent", "change_pct")));
        }
        return gainers;
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

    private record Gainer(String symbol, BigDecimal price, BigDecimal changePercent) {
    }
}
