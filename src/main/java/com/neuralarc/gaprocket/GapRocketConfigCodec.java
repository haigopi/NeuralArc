package com.neuralarc.gaprocket;

import com.neuralarc.model.StrategyMode;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Serializes {@link GapRocketConfig} to/from JSON for durable storage (e.g. persisted schedules).
 * Lenient on read: missing or malformed fields fall back to the config's own defaults.
 */
public final class GapRocketConfigCodec {
    private GapRocketConfigCodec() {
    }

    public static String toJson(GapRocketConfig config) {
        GapRocketConfig safe = config == null ? GapRocketConfig.defaults(null) : config;
        JSONObject json = new JSONObject();
        json.put("minimumPremarketGapPercent", safe.minimumPremarketGapPercent().toPlainString());
        json.put("minimumPremarketVolume", safe.minimumPremarketVolume());
        json.put("minimumStockPrice", safe.minimumStockPrice().toPlainString());
        json.put("minimumRelativeVolume", safe.minimumRelativeVolume().toPlainString());
        json.put("maximumStockPrice", safe.maximumStockPrice() == null ? JSONObject.NULL
                : safe.maximumStockPrice().toPlainString());
        json.put("newsCatalystRequired", safe.newsCatalystRequired());
        JSONArray catalysts = new JSONArray();
        safe.catalystTypes().forEach(type -> catalysts.put(type.name()));
        json.put("catalystTypes", catalysts);
        json.put("marketTrendFilter", safe.marketTrendFilter().name());
        json.put("entryStyle", safe.entryStyle().name());
        json.put("openingRangeDuration", safe.openingRangeDuration().name());
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

    public static GapRocketConfig fromJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return GapRocketConfig.defaults(null);
        }
        JSONObject json = new JSONObject(raw);
        GapRocketConfig defaults = GapRocketConfig.defaults(null);
        return new GapRocketConfig(
                decimal(json, "minimumPremarketGapPercent", defaults.minimumPremarketGapPercent()),
                json.optLong("minimumPremarketVolume", defaults.minimumPremarketVolume()),
                decimal(json, "minimumStockPrice", defaults.minimumStockPrice()),
                decimal(json, "minimumRelativeVolume", defaults.minimumRelativeVolume()),
                json.isNull("maximumStockPrice") ? null : decimal(json, "maximumStockPrice", null),
                json.optBoolean("newsCatalystRequired", defaults.newsCatalystRequired()),
                catalystTypes(json),
                enumValue(GapRocketConfig.MarketTrendFilter.class, json.optString("marketTrendFilter"), defaults.marketTrendFilter()),
                enumValue(GapRocketConfig.EntryStyle.class, json.optString("entryStyle"), defaults.entryStyle()),
                enumValue(GapRocketConfig.OpeningRangeDuration.class, json.optString("openingRangeDuration"), defaults.openingRangeDuration()),
                decimal(json, "stopLossPercent", defaults.stopLossPercent()),
                decimal(json, "takeProfitPercent", defaults.takeProfitPercent()),
                json.optInt("maxStocksToAdd", defaults.maxStocksToAdd()),
                enumValue(GapRocketConfig.ExecutionFrequency.class, json.optString("executionFrequency"), defaults.executionFrequency()),
                enumValue(StrategyMode.class, json.optString("mode"), defaults.mode()),
                symbols(json));
    }

    private static BigDecimal decimal(JSONObject json, String key, BigDecimal fallback) {
        String value = json.optString(key, null);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static Set<GapRocketConfig.CatalystType> catalystTypes(JSONObject json) {
        JSONArray array = json.optJSONArray("catalystTypes");
        if (array == null || array.isEmpty()) {
            return EnumSet.allOf(GapRocketConfig.CatalystType.class);
        }
        EnumSet<GapRocketConfig.CatalystType> types = EnumSet.noneOf(GapRocketConfig.CatalystType.class);
        for (int i = 0; i < array.length(); i++) {
            GapRocketConfig.CatalystType type = enumValue(GapRocketConfig.CatalystType.class, array.optString(i), null);
            if (type != null) {
                types.add(type);
            }
        }
        return types.isEmpty() ? EnumSet.allOf(GapRocketConfig.CatalystType.class) : types;
    }

    private static List<String> symbols(JSONObject json) {
        JSONArray array = json.optJSONArray("candidateSymbols");
        if (array == null) {
            return List.of();
        }
        List<String> symbols = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            String symbol = array.optString(i, "").trim();
            if (!symbol.isBlank()) {
                symbols.add(symbol);
            }
        }
        return symbols;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String name, E fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
