package com.neuralarc.orb;

import com.neuralarc.model.StrategyMode;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Serializes {@link OrbConfig} to/from JSON for durable storage (e.g. persisted session configs).
 * Lenient on read: missing or malformed fields fall back to the config's own defaults.
 */
public final class OrbConfigCodec {
    private OrbConfigCodec() {}

    public static String toJson(OrbConfig config) {
        OrbConfig safe = config == null ? OrbConfig.defaults(null) : config;
        JSONObject json = new JSONObject();
        json.put("rangeDurationMinutes", safe.rangeDurationMinutes());
        json.put("entryBufferPercent", safe.entryBufferPercent().toPlainString());
        json.put("stopMode", safe.stopMode().name());
        json.put("riskPercent", safe.riskPercent().toPlainString());
        json.put("takeProfitPercent", safe.takeProfitPercent().toPlainString());
        json.put("maxStocksToAdd", safe.maxStocksToAdd());
        json.put("minimumPrice", safe.minimumPrice().toPlainString());
        json.put("maximumPrice", safe.maximumPrice() == null ? JSONObject.NULL : safe.maximumPrice().toPlainString());
        json.put("minimumRelativeVolume", safe.minimumRelativeVolume().toPlainString());
        json.put("minimumRangePercent", safe.minimumRangePercent().toPlainString());
        json.put("latestEntryTimeEt", safe.latestEntryTimeEt().toString());
        json.put("autoDiscoverEnabled", safe.autoDiscoverEnabled());
        json.put("scheduleEnabled", safe.scheduleEnabled());
        json.put("mode", safe.mode() == null ? StrategyMode.PAPER.name() : safe.mode().name());
        JSONArray symbols = new JSONArray();
        safe.candidateSymbols().forEach(symbols::put);
        json.put("candidateSymbols", symbols);
        return json.toString();
    }

    public static OrbConfig fromJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return OrbConfig.defaults(null);
        }
        JSONObject json = new JSONObject(raw);
        OrbConfig defaults = OrbConfig.defaults(null);
        return new OrbConfig(
                json.optInt("rangeDurationMinutes", defaults.rangeDurationMinutes()),
                decimal(json, "entryBufferPercent", defaults.entryBufferPercent()),
                enumValue(OrbConfig.StopMode.class, json.optString("stopMode"), defaults.stopMode()),
                decimal(json, "riskPercent", defaults.riskPercent()),
                decimal(json, "takeProfitPercent", defaults.takeProfitPercent()),
                json.optInt("maxStocksToAdd", defaults.maxStocksToAdd()),
                decimal(json, "minimumPrice", defaults.minimumPrice()),
                json.isNull("maximumPrice") ? null : decimal(json, "maximumPrice", null),
                decimal(json, "minimumRelativeVolume", defaults.minimumRelativeVolume()),
                decimal(json, "minimumRangePercent", defaults.minimumRangePercent()),
                localTime(json, "latestEntryTimeEt", defaults.latestEntryTimeEt()),
                symbols(json),
                json.optBoolean("autoDiscoverEnabled", defaults.autoDiscoverEnabled()),
                json.optBoolean("scheduleEnabled", defaults.scheduleEnabled()),
                enumValue(StrategyMode.class, json.optString("mode"), defaults.mode())
        );
    }

    private static BigDecimal decimal(JSONObject json, String key, BigDecimal fallback) {
        String value = json.optString(key, null);
        if (value == null || value.isBlank()) return fallback;
        try { return new BigDecimal(value); } catch (NumberFormatException ex) { return fallback; }
    }

    private static LocalTime localTime(JSONObject json, String key, LocalTime fallback) {
        String value = json.optString(key, null);
        if (value == null || value.isBlank()) return fallback;
        try { return LocalTime.parse(value); } catch (Exception ex) { return fallback; }
    }

    private static List<String> symbols(JSONObject json) {
        JSONArray array = json.optJSONArray("candidateSymbols");
        if (array == null) return List.of();
        List<String> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            String s = array.optString(i, "").trim();
            if (!s.isBlank()) result.add(s);
        }
        return result;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String name, E fallback) {
        if (name == null || name.isBlank()) return fallback;
        try { return Enum.valueOf(type, name); } catch (IllegalArgumentException ex) { return fallback; }
    }
}
