package com.neuralarc.service;

import com.neuralarc.earningshunter.EarningsHunterConfig;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class EarningsHunterDiscoveryService {
    private final AlpacaScreenerClient screener;

    public EarningsHunterDiscoveryService(AlpacaScreenerClient screener) {
        this.screener = Objects.requireNonNull(screener, "screener");
    }

    public List<String> discoverCandidates(EarningsHunterConfig config, int maxSymbols) throws AlpacaScreenerException {
        EarningsHunterConfig safeConfig = config == null ? EarningsHunterConfig.defaults(null) : config;
        int limit = Math.max(1, maxSymbols);
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        addSymbols(selected, screener.getMostActives("volume", Math.max(20, limit * 3)), "most_actives", safeConfig, limit);
        if (selected.size() < limit) {
            addSymbols(selected, screener.getMarketMovers(Math.max(10, limit * 2)), "gainers", safeConfig, limit);
        }
        if (selected.size() < limit) {
            addSymbols(selected, screener.getMarketMovers(Math.max(10, limit * 2)), "losers", safeConfig, limit);
        }
        return new ArrayList<>(selected);
    }

    private static void addSymbols(LinkedHashSet<String> selected, JSONObject body, String arrayName,
                                   EarningsHunterConfig config, int limit) {
        if (body == null || selected.size() >= limit) {
            return;
        }
        JSONArray array = body.optJSONArray(arrayName);
        if (array == null && "most_actives".equals(arrayName)) {
            array = body.optJSONArray("mostActives");
        }
        if (array == null) {
            return;
        }
        for (int i = 0; i < array.length() && selected.size() < limit; i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String symbol = item.optString("symbol", item.optString("S", "")).trim().toUpperCase(Locale.ROOT);
            BigDecimal price = decimal(item, "price", "latest_price", "close");
            if (!symbol.isBlank() && passesPriceBounds(price, config)) {
                selected.add(symbol);
            }
        }
    }

    private static boolean passesPriceBounds(BigDecimal price, EarningsHunterConfig config) {
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
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
            }
        }
        return BigDecimal.ZERO;
    }
}
