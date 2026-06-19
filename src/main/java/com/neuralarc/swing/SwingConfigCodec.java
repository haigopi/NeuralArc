package com.neuralarc.swing;

import com.neuralarc.model.StrategyMode;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Serializes {@link SwingConfig} to/from JSON for durable storage (e.g. persisted schedules).
 * Lenient on read: missing or malformed fields fall back to the config's own defaults.
 */
public final class SwingConfigCodec {
    private SwingConfigCodec() {}

    public static String toJson(SwingConfig config) {
        SwingConfig safe = config == null ? SwingConfig.defaults(null) : config;
        JSONObject json = new JSONObject();
        json.put("minimumPullbackPercent", safe.minimumPullbackPercent().toPlainString());
        json.put("maximumPullbackPercent", safe.maximumPullbackPercent().toPlainString());
        json.put("minimumAverageVolume", safe.minimumAverageVolume());
        json.put("minimumStockPrice", safe.minimumStockPrice().toPlainString());
        json.put("minimumRelativeVolume", safe.minimumRelativeVolume().toPlainString());
        json.put("maximumStockPrice", safe.maximumStockPrice() == null ? JSONObject.NULL : safe.maximumStockPrice().toPlainString());
        json.put("trendFilter", safe.trendFilter().name());
        json.put("stopLossPercent", safe.stopLossPercent().toPlainString());
        json.put("targetProfitPercent", safe.targetProfitPercent().toPlainString());
        json.put("maxStocksToAdd", safe.maxStocksToAdd());
        json.put("executionFrequency", safe.executionFrequency().name());
        json.put("mode", safe.mode().name());
        JSONArray symbols = new JSONArray();
        safe.candidateSymbols().forEach(symbols::put);
        json.put("candidateSymbols", symbols);
        return json.toString();
    }

    public static SwingConfig fromJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return SwingConfig.defaults(null);
        }
        JSONObject json = new JSONObject(raw);
        SwingConfig defaults = SwingConfig.defaults(null);
        return new SwingConfig(
                decimal(json, "minimumPullbackPercent", defaults.minimumPullbackPercent()),
                decimal(json, "maximumPullbackPercent", defaults.maximumPullbackPercent()),
                json.optLong("minimumAverageVolume", defaults.minimumAverageVolume()),
                decimal(json, "minimumStockPrice", defaults.minimumStockPrice()),
                decimal(json, "minimumRelativeVolume", defaults.minimumRelativeVolume()),
                json.isNull("maximumStockPrice") ? null : decimal(json, "maximumStockPrice", null),
                enumValue(SwingConfig.TrendFilter.class, json.optString("trendFilter"), defaults.trendFilter()),
                decimal(json, "stopLossPercent", defaults.stopLossPercent()),
                decimal(json, "targetProfitPercent", defaults.targetProfitPercent()),
                json.optInt("maxStocksToAdd", defaults.maxStocksToAdd()),
                enumValue(SwingConfig.ExecutionFrequency.class, json.optString("executionFrequency"), defaults.executionFrequency()),
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
