package com.neuralarc.vwap;

import com.neuralarc.model.StrategyMode;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Serializes {@link VwapConfig} to/from JSON for durable storage (e.g. persisted schedules).
 * Lenient on read: missing or malformed fields fall back to the config's own defaults.
 */
public final class VwapConfigCodec {
    private VwapConfigCodec() {}

    public static String toJson(VwapConfig config) {
        VwapConfig safe = config == null ? VwapConfig.defaults(null) : config;
        JSONObject json = new JSONObject();
        json.put("minimumDiscountPercent", safe.minimumDiscountPercent().toPlainString());
        json.put("maximumDiscountPercent", safe.maximumDiscountPercent().toPlainString());
        json.put("minimumAverageVolume", safe.minimumAverageVolume());
        json.put("minimumStockPrice", safe.minimumStockPrice().toPlainString());
        json.put("minimumRelativeVolume", safe.minimumRelativeVolume().toPlainString());
        json.put("maximumStockPrice", safe.maximumStockPrice() == null ? JSONObject.NULL : safe.maximumStockPrice().toPlainString());
        json.put("trendFilter", safe.trendFilter().name());
        json.put("stopLossPercent", safe.stopLossPercent().toPlainString());
        json.put("maxStocksToAdd", safe.maxStocksToAdd());
        json.put("executionFrequency", safe.executionFrequency().name());
        json.put("mode", safe.mode().name());
        JSONArray symbols = new JSONArray();
        safe.candidateSymbols().forEach(symbols::put);
        json.put("candidateSymbols", symbols);
        return json.toString();
    }

    public static VwapConfig fromJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return VwapConfig.defaults(null);
        }
        JSONObject json = new JSONObject(raw);
        VwapConfig defaults = VwapConfig.defaults(null);
        return new VwapConfig(
                decimal(json, "minimumDiscountPercent", defaults.minimumDiscountPercent()),
                decimal(json, "maximumDiscountPercent", defaults.maximumDiscountPercent()),
                json.optLong("minimumAverageVolume", defaults.minimumAverageVolume()),
                decimal(json, "minimumStockPrice", defaults.minimumStockPrice()),
                decimal(json, "minimumRelativeVolume", defaults.minimumRelativeVolume()),
                json.isNull("maximumStockPrice") ? null : decimal(json, "maximumStockPrice", null),
                enumValue(VwapConfig.TrendFilter.class, json.optString("trendFilter"), defaults.trendFilter()),
                decimal(json, "stopLossPercent", defaults.stopLossPercent()),
                json.optInt("maxStocksToAdd", defaults.maxStocksToAdd()),
                enumValue(VwapConfig.ExecutionFrequency.class, json.optString("executionFrequency"), defaults.executionFrequency()),
                enumValue(StrategyMode.class, json.optString("mode"), defaults.mode()),
                symbols(json));
    }

    private static BigDecimal decimal(JSONObject json, String key, BigDecimal fallback) {
        String value = json.optString(key, null);
        if (value == null || value.isBlank()) return fallback;
        try { return new BigDecimal(value); } catch (NumberFormatException ex) { return fallback; }
    }

    private static List<String> symbols(JSONObject json) {
        JSONArray array = json.optJSONArray("candidateSymbols");
        if (array == null) return List.of();
        List<String> symbols = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            String symbol = array.optString(i, "").trim();
            if (!symbol.isBlank()) symbols.add(symbol);
        }
        return symbols;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String name, E fallback) {
        if (name == null || name.isBlank()) return fallback;
        try { return Enum.valueOf(type, name); } catch (IllegalArgumentException ex) { return fallback; }
    }
}
