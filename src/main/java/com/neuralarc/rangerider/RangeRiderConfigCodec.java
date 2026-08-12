package com.neuralarc.rangerider;

import com.neuralarc.model.StrategyMode;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Serializes {@link RangeRiderConfig} to/from JSON for durable storage (e.g. persisted schedules).
 * Lenient on read: missing or malformed fields fall back to the config's own defaults.
 */
public final class RangeRiderConfigCodec {
    private RangeRiderConfigCodec() {}

    public static String toJson(RangeRiderConfig config) {
        RangeRiderConfig safe = config == null ? RangeRiderConfig.defaults(null) : config;
        JSONObject json = new JSONObject();
        json.put("lookbackSessions", safe.lookbackSessions());
        json.put("minimumAverageRangePercent", safe.minimumAverageRangePercent().toPlainString());
        json.put("maximumAverageRangePercent", safe.maximumAverageRangePercent().toPlainString());
        json.put("minimumSameDayFillRatePercent", safe.minimumSameDayFillRatePercent().toPlainString());
        json.put("minimumAverageVolume", safe.minimumAverageVolume());
        json.put("minimumStockPrice", safe.minimumStockPrice().toPlainString());
        json.put("maximumStockPrice", safe.maximumStockPrice() == null ? JSONObject.NULL : safe.maximumStockPrice().toPlainString());
        json.put("entryBufferPercent", safe.entryBufferPercent().toPlainString());
        json.put("exitBufferPercent", safe.exitBufferPercent().toPlainString());
        json.put("stopLossPercent", safe.stopLossPercent().toPlainString());
        json.put("maxStocksToAdd", safe.maxStocksToAdd());
        json.put("executionFrequency", safe.executionFrequency().name());
        json.put("mode", safe.mode().name());
        JSONArray symbols = new JSONArray();
        safe.candidateSymbols().forEach(symbols::put);
        json.put("candidateSymbols", symbols);
        return json.toString();
    }

    public static RangeRiderConfig fromJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return RangeRiderConfig.defaults(null);
        }
        JSONObject json = new JSONObject(raw);
        RangeRiderConfig defaults = RangeRiderConfig.defaults(null);
        return new RangeRiderConfig(
                json.optInt("lookbackSessions", defaults.lookbackSessions()),
                decimal(json, "minimumAverageRangePercent", defaults.minimumAverageRangePercent()),
                decimal(json, "maximumAverageRangePercent", defaults.maximumAverageRangePercent()),
                decimal(json, "minimumSameDayFillRatePercent", defaults.minimumSameDayFillRatePercent()),
                json.optLong("minimumAverageVolume", defaults.minimumAverageVolume()),
                decimal(json, "minimumStockPrice", defaults.minimumStockPrice()),
                json.isNull("maximumStockPrice") ? null : decimal(json, "maximumStockPrice", null),
                decimal(json, "entryBufferPercent", defaults.entryBufferPercent()),
                decimal(json, "exitBufferPercent", defaults.exitBufferPercent()),
                decimal(json, "stopLossPercent", defaults.stopLossPercent()),
                json.optInt("maxStocksToAdd", defaults.maxStocksToAdd()),
                enumValue(RangeRiderConfig.ExecutionFrequency.class, json.optString("executionFrequency"), defaults.executionFrequency()),
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
