package com.neuralarc.profitshield;

import com.neuralarc.model.StrategyMode;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Serializes {@link ProfitShieldConfig} to/from JSON for durable storage (e.g. persisted schedules).
 * Lenient on read: missing or malformed fields fall back to the config's own defaults.
 */
public final class ProfitShieldConfigCodec {
    private ProfitShieldConfigCodec() {}

    public static String toJson(ProfitShieldConfig config) {
        ProfitShieldConfig safe = config == null ? ProfitShieldConfig.defaults(null) : config;
        JSONObject json = new JSONObject();
        json.put("drawdownLookbackSessions", safe.drawdownLookbackSessions());
        json.put("maximumDailyVolatilityPercent", safe.maximumDailyVolatilityPercent().toPlainString());
        json.put("maximumDrawdownPercent", safe.maximumDrawdownPercent().toPlainString());
        json.put("maximumDistanceFromHighPercent", safe.maximumDistanceFromHighPercent().toPlainString());
        json.put("minimumAverageVolume", safe.minimumAverageVolume());
        json.put("minimumStockPrice", safe.minimumStockPrice().toPlainString());
        json.put("maximumStockPrice", safe.maximumStockPrice() == null ? JSONObject.NULL : safe.maximumStockPrice().toPlainString());
        json.put("trendFilter", safe.trendFilter().name());
        json.put("entryDiscountPercent", safe.entryDiscountPercent().toPlainString());
        json.put("protectiveStopPercent", safe.protectiveStopPercent().toPlainString());
        json.put("targetProfitPercent", safe.targetProfitPercent().toPlainString());
        json.put("maxStocksToAdd", safe.maxStocksToAdd());
        json.put("mode", safe.mode().name());
        JSONArray symbols = new JSONArray();
        safe.candidateSymbols().forEach(symbols::put);
        json.put("candidateSymbols", symbols);
        return json.toString();
    }

    public static ProfitShieldConfig fromJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return ProfitShieldConfig.defaults(null);
        }
        JSONObject json = new JSONObject(raw);
        ProfitShieldConfig defaults = ProfitShieldConfig.defaults(null);
        return new ProfitShieldConfig(
                json.optInt("drawdownLookbackSessions", defaults.drawdownLookbackSessions()),
                decimal(json, "maximumDailyVolatilityPercent", defaults.maximumDailyVolatilityPercent()),
                decimal(json, "maximumDrawdownPercent", defaults.maximumDrawdownPercent()),
                decimal(json, "maximumDistanceFromHighPercent", defaults.maximumDistanceFromHighPercent()),
                json.optLong("minimumAverageVolume", defaults.minimumAverageVolume()),
                decimal(json, "minimumStockPrice", defaults.minimumStockPrice()),
                json.isNull("maximumStockPrice") ? null : decimal(json, "maximumStockPrice", null),
                enumValue(ProfitShieldConfig.TrendFilter.class, json.optString("trendFilter"), defaults.trendFilter()),
                decimal(json, "entryDiscountPercent", defaults.entryDiscountPercent()),
                decimal(json, "protectiveStopPercent", defaults.protectiveStopPercent()),
                decimal(json, "targetProfitPercent", defaults.targetProfitPercent()),
                json.optInt("maxStocksToAdd", defaults.maxStocksToAdd()),
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
