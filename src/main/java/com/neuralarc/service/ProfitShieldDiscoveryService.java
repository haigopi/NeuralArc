package com.neuralarc.service;

import com.neuralarc.profitshield.ProfitShieldConfig;
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
 * Discovers live Profit Shield candidate symbols from Alpaca's screener so the operator does not have
 * to type tickers by hand. The defensive book wants liquid, heavily traded names, so the most-active
 * list by volume is the primary source; the day's movers are only used to fill remaining slots when
 * the actives list is short. Uses live broker/market-data endpoints only — never hardcoded tickers,
 * canned prices, or synthetic candidates.
 *
 * <p>This service applies price bounds only. Whether a name is actually defensive enough — volatility,
 * drawdown, distance from its high, trend — is decided downstream by {@code ProfitShieldLiveScanner}
 * and {@code ProfitShieldAnalyzer} from real daily bars.
 */
public final class ProfitShieldDiscoveryService {
    private static final Logger LOGGER = Logger.getLogger(ProfitShieldDiscoveryService.class.getName());

    private final AlpacaScreenerClient screener;

    public ProfitShieldDiscoveryService(AlpacaScreenerClient screener) {
        this.screener = Objects.requireNonNull(screener, "screener");
    }

    /**
     * Discover up to {@code maxSymbols} candidate tickers: the most actively traded names first, then
     * the day's movers to fill any remaining slots.
     *
     * @throws AlpacaScreenerException on API or network error
     */
    public List<String> discoverCandidates(ProfitShieldConfig config, int maxSymbols) throws AlpacaScreenerException {
        ProfitShieldConfig safeConfig = config == null ? ProfitShieldConfig.defaults(null) : config;
        int limit = Math.max(1, maxSymbols);
        LinkedHashSet<String> selected = new LinkedHashSet<>();

        JSONObject actives = screener.getMostActives("volume", Math.max(20, limit * 3));
        addSymbols(selected, actives, "most_actives", safeConfig, limit);
        addSymbols(selected, actives, "mostActives", safeConfig, limit);

        if (selected.size() < limit) {
            JSONObject movers = screener.getMarketMovers(Math.max(10, limit * 2));
            addSymbols(selected, movers, "gainers", safeConfig, limit);
            addSymbols(selected, movers, "losers", safeConfig, limit);
        }

        List<String> result = new ArrayList<>(selected);
        LOGGER.info(() -> "Profit Shield discovery selected " + result.size() + " candidate(s): " + result);
        return result;
    }

    private static void addSymbols(LinkedHashSet<String> selected, JSONObject body, String arrayName,
                                   ProfitShieldConfig config, int limit) {
        if (body == null || selected.size() >= limit) {
            return;
        }
        JSONArray array = body.optJSONArray(arrayName);
        if (array == null) {
            return;
        }
        for (int i = 0; i < array.length() && selected.size() < limit; i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String symbol = item.optString("symbol", item.optString("S", "")).trim().toUpperCase(Locale.ROOT);
            if (!symbol.isBlank() && passesPriceBounds(decimal(item, "price", "latest_price", "close"), config)) {
                selected.add(symbol);
            }
        }
    }

    private static boolean passesPriceBounds(BigDecimal price, ProfitShieldConfig config) {
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            return true; // no price data — let the scanner decide
        }
        if (price.compareTo(config.minimumStockPrice()) < 0) {
            return false;
        }
        return config.maximumStockPrice() == null || price.compareTo(config.maximumStockPrice()) <= 0;
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
}
