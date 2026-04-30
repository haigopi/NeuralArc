package com.neuralarc.service;

import com.neuralarc.model.AutoAnalyzeResult;
import com.neuralarc.util.AppMetadata;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persists and loads {@link AutoAnalyzeResult} objects locally, one JSON file per
 * upper-cased symbol under {@code ~/.neuralarc/auto-analyze/}.
 *
 * <p>A new run for the same symbol overwrites the previous result — only the most
 * recent analysis is retained per symbol.</p>
 */
public class AutoAnalyzeResultStore {

    private static final Logger LOGGER = Logger.getLogger(AutoAnalyzeResultStore.class.getName());

    private final Path storeDir;

    /** Production constructor – uses the app-data directory. */
    public AutoAnalyzeResultStore() {
        this(AppMetadata.appDataDirectory().resolve("auto-analyze"));
    }

    /** Package-visible test constructor. */
    AutoAnalyzeResultStore(Path storeDir) {
        this.storeDir = storeDir;
    }

    /** Persist {@code result} to disk. No-op if {@code result} is {@code null}. */
    public void save(AutoAnalyzeResult result) {
        if (result == null) {
            return;
        }
        try {
            Files.createDirectories(storeDir);
            Path file = storeDir.resolve(result.symbol().toUpperCase() + ".json");
            Files.writeString(file, toJson(result).toString(2), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to persist AutoAnalyzeResult for " + result.symbol(), ex);
        }
    }

    /**
     * Load the most recent persisted result for {@code symbol}.
     *
     * @return an {@link Optional} containing the result, or empty if nothing is persisted or the
     *         file cannot be read.
     */
    public Optional<AutoAnalyzeResult> load(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return Optional.empty();
        }
        Path file = storeDir.resolve(symbol.trim().toUpperCase() + ".json");
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            return Optional.of(fromJson(new JSONObject(content)));
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to load AutoAnalyzeResult for " + symbol, ex);
            return Optional.empty();
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private JSONObject toJson(AutoAnalyzeResult r) {
        JSONObject json = new JSONObject();
        json.put("symbol", r.symbol());
        json.put("startDate", r.startDate().toString());
        json.put("endDate", r.endDate().toString());
        json.put("intervalMinutes", r.intervalMinutes());
        json.put("averageDailyOpen", r.averageDailyOpen().toPlainString());
        json.put("averageDailyClose", r.averageDailyClose().toPlainString());
        json.put("averageDailyLow", r.averageDailyLow().toPlainString());
        json.put("averageDailyHigh", r.averageDailyHigh().toPlainString());
        json.put("oneWeekLow", r.oneWeekLow().toPlainString());
        json.put("oneWeekHigh", r.oneWeekHigh().toPlainString());
        json.put("twoWeekLow", r.twoWeekLow().toPlainString());
        json.put("twoWeekHigh", r.twoWeekHigh().toPlainString());
        json.put("oneMonthLow", r.oneMonthLow().toPlainString());
        json.put("oneMonthHigh", r.oneMonthHigh().toPlainString());
        json.put("twoMonthLow", r.twoMonthLow().toPlainString());
        json.put("twoMonthHigh", r.twoMonthHigh().toPlainString());
        json.put("fourMonthLow", r.fourMonthLow().toPlainString());
        json.put("fourMonthHigh", r.fourMonthHigh().toPlainString());
        json.put("sixMonthLow", r.sixMonthLow().toPlainString());
        json.put("sixMonthHigh", r.sixMonthHigh().toPlainString());
        json.put("eightMonthLow", r.eightMonthLow().toPlainString());
        json.put("eightMonthHigh", r.eightMonthHigh().toPlainString());
        json.put("oneYearLow", r.oneYearLow().toPlainString());
        json.put("oneYearHigh", r.oneYearHigh().toPlainString());
        // Legacy aliases for compatibility with older builds.
        json.put("fiftyTwoWeekLow", r.oneYearLow().toPlainString());
        json.put("fiftyTwoWeekHigh", r.oneYearHigh().toPlainString());
        json.put("todayStockPrice", r.todayStockPrice().toPlainString());
        json.put("todayOpen", r.todayOpen().toPlainString());
        json.put("todayHighSoFar", r.todayHighSoFar().toPlainString());
        json.put("todayLowSoFar", r.todayLowSoFar().toPlainString());
        json.put("todayCloseAvailable", r.todayCloseAvailable());
        json.put("todayClose", r.todayClose().toPlainString());
        json.put("thresholdNumber", r.thresholdNumber().toPlainString());
        json.put("dailyBarsProcessed", r.dailyBarsProcessed());
        json.put("intradayBarsProcessed", r.intradayBarsProcessed());
        json.put("analyzedAt", r.analyzedAt().toString());
        return json;
    }

    private AutoAnalyzeResult fromJson(JSONObject json) {
        // Backward-compat: older cache files won't have all range fields.
        String avgLow  = json.getString("averageDailyLow");
        String avgHigh = json.getString("averageDailyHigh");
        String sixMonthLow = json.optString("sixMonthLow", avgLow);
        String sixMonthHigh = json.optString("sixMonthHigh", avgHigh);
        String oneYearLow = json.optString("oneYearLow", json.optString("fiftyTwoWeekLow", sixMonthLow));
        String oneYearHigh = json.optString("oneYearHigh", json.optString("fiftyTwoWeekHigh", sixMonthHigh));
        return new AutoAnalyzeResult(
                json.getString("symbol"),
                LocalDate.parse(json.getString("startDate")),
                LocalDate.parse(json.getString("endDate")),
                json.getInt("intervalMinutes"),
                new BigDecimal(json.getString("averageDailyOpen")),
                new BigDecimal(json.getString("averageDailyClose")),
                new BigDecimal(avgLow),
                new BigDecimal(avgHigh),
                new BigDecimal(json.optString("oneWeekLow", sixMonthLow)),
                new BigDecimal(json.optString("oneWeekHigh", sixMonthHigh)),
                new BigDecimal(json.optString("twoWeekLow", sixMonthLow)),
                new BigDecimal(json.optString("twoWeekHigh", sixMonthHigh)),
                new BigDecimal(json.optString("oneMonthLow", sixMonthLow)),
                new BigDecimal(json.optString("oneMonthHigh", sixMonthHigh)),
                new BigDecimal(json.optString("twoMonthLow", sixMonthLow)),
                new BigDecimal(json.optString("twoMonthHigh", sixMonthHigh)),
                new BigDecimal(json.optString("fourMonthLow", sixMonthLow)),
                new BigDecimal(json.optString("fourMonthHigh", sixMonthHigh)),
                new BigDecimal(sixMonthLow),
                new BigDecimal(sixMonthHigh),
                new BigDecimal(json.optString("eightMonthLow", sixMonthLow)),
                new BigDecimal(json.optString("eightMonthHigh", sixMonthHigh)),
                new BigDecimal(oneYearLow),
                new BigDecimal(oneYearHigh),
                new BigDecimal(json.optString("todayStockPrice", "0")),
                new BigDecimal(json.optString("todayOpen", "0")),
                new BigDecimal(json.optString("todayHighSoFar", "0")),
                new BigDecimal(json.optString("todayLowSoFar", "0")),
                json.optBoolean("todayCloseAvailable", false),
                new BigDecimal(json.optString("todayClose", "0")),
                new BigDecimal(json.getString("thresholdNumber")),
                json.getInt("dailyBarsProcessed"),
                json.getInt("intradayBarsProcessed"),
                Instant.parse(json.getString("analyzedAt"))
        );
    }
}
