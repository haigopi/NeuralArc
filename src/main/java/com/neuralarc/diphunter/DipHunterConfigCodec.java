package com.neuralarc.diphunter;

import com.neuralarc.model.StrategyMode;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Serializes {@link DipHunterConfig} to/from JSON for durable storage (e.g. persisted schedules).
 * Lenient on read: missing or malformed fields fall back to the config's own defaults.
 */
public final class DipHunterConfigCodec {
    private DipHunterConfigCodec() {}

    public static String toJson(DipHunterConfig config) {
        DipHunterConfig safe = config == null ? DipHunterConfig.defaults(null) : config;
        JSONObject json = new JSONObject();
        json.put("minimumPullbackPercent", safe.minimumPullbackPercent().toPlainString());
        json.put("maximumPullbackPercent", safe.maximumPullbackPercent().toPlainString());
        json.put("minimumAverageVolume", safe.minimumAverageVolume());
        json.put("minimumStockPrice", safe.minimumStockPrice().toPlainString());
        json.put("minimumRelativeVolume", safe.minimumRelativeVolume().toPlainString());
        json.put("maximumStockPrice", safe.maximumStockPrice() == null ? JSONObject.NULL : safe.maximumStockPrice().toPlainString());
        json.put("trendFilter", safe.trendFilter().name());
        json.put("bounceConfirmation", safe.bounceConfirmation().name());
        json.put("stopLossPercent", safe.stopLossPercent().toPlainString());
        json.put("takeProfitPercent", safe.takeProfitPercent().toPlainString());
        json.put("maxStocksToAdd", safe.maxStocksToAdd());
        json.put("executionFrequency", safe.executionFrequency().name());
        json.put("mode", safe.mode().name());
        JSONArray symbols = new JSONArray();
        safe.candidateSymbols().forEach(symbols::put);
        json.put("candidateSymbols", symbols);
        return json.toString();
    }

    public static DipHunterConfig fromJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return DipHunterConfig.defaults(null);
        }
        JSONObject json = new JSONObject(raw);
        DipHunterConfig defaults = DipHunterConfig.defaults(null);
        return new DipHunterConfig(
                decimal(json, "minimumPullbackPercent", defaults.minimumPullbackPercent()),
                decimal(json, "maximumPullbackPercent", defaults.maximumPullbackPercent()),
                json.optLong("minimumAverageVolume", defaults.minimumAverageVolume()),
                decimal(json, "minimumStockPrice", defaults.minimumStockPrice()),
                decimal(json, "minimumRelativeVolume", defaults.minimumRelativeVolume()),
                json.isNull("maximumStockPrice") ? null : decimal(json, "maximumStockPrice", null),
                enumValue(DipHunterConfig.TrendFilter.class, json.optString("trendFilter"), defaults.trendFilter()),
                enumValue(DipHunterConfig.BounceConfirmation.class, json.optString("bounceConfirmation"), defaults.bounceConfirmation()),
                decimal(json, "stopLossPercent", defaults.stopLossPercent()),
                decimal(json, "takeProfitPercent", defaults.takeProfitPercent()),
                json.optInt("maxStocksToAdd", defaults.maxStocksToAdd()),
                enumValue(DipHunterConfig.ExecutionFrequency.class, json.optString("executionFrequency"), defaults.executionFrequency()),
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
