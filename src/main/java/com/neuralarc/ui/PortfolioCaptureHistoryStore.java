package com.neuralarc.ui;

import com.neuralarc.util.Monetary;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class PortfolioCaptureHistoryStore {
    private final Path historyFile;

    PortfolioCaptureHistoryStore(Path historyFile) {
        this.historyFile = historyFile;
    }

    synchronized Summary append(Entry entry) {
        List<Entry> entries = new ArrayList<>(loadEntries());
        entries.add(entry);
        saveEntries(entries);
        return summarize(entries);
    }

    synchronized Summary summary() {
        return summarize(loadEntries());
    }

    synchronized List<Entry> loadEntries() {
        if (!Files.exists(historyFile)) {
            return List.of();
        }
        try {
            JSONObject root = new JSONObject(Files.readString(historyFile, StandardCharsets.UTF_8));
            JSONArray array = root.optJSONArray("captures");
            if (array == null) {
                return List.of();
            }
            List<Entry> entries = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject json = array.optJSONObject(i);
                if (json == null) {
                    continue;
                }
                entries.add(new Entry(
                        json.optString("id", ""),
                        parseInstant(json.optString("timestamp", "")),
                        json.optInt("loopNumber", 0),
                        json.optString("triggerReason", ""),
                        json.optString("executionFlow", ""),
                        json.optInt("capturedCount", 0),
                        decimal(json, "totalInvestment"),
                        decimal(json, "estimatedPortfolioValue"),
                        decimal(json, "actualBrokerExecutionValue"),
                        decimal(json, "estimatedPnl"),
                        decimal(json, "actualPnl"),
                        decimal(json, "executionVariance")
                ));
            }
            return entries;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private void saveEntries(List<Entry> entries) {
        try {
            Files.createDirectories(historyFile.getParent());
            JSONArray array = new JSONArray();
            for (Entry entry : entries) {
                JSONObject json = new JSONObject();
                json.put("id", entry.id());
                json.put("timestamp", entry.timestamp() == null ? "" : entry.timestamp().toString());
                json.put("loopNumber", entry.loopNumber());
                json.put("triggerReason", entry.triggerReason());
                json.put("executionFlow", entry.executionFlow());
                json.put("capturedCount", entry.capturedCount());
                json.put("totalInvestment", Monetary.round(entry.totalInvestment()));
                json.put("estimatedPortfolioValue", Monetary.round(entry.estimatedPortfolioValue()));
                json.put("actualBrokerExecutionValue", Monetary.round(entry.actualBrokerExecutionValue()));
                json.put("estimatedPnl", Monetary.round(entry.estimatedPnl()));
                json.put("actualPnl", Monetary.round(entry.actualPnl()));
                json.put("executionVariance", Monetary.round(entry.executionVariance()));
                array.put(json);
            }
            JSONObject root = new JSONObject();
            root.put("captures", array);
            Files.writeString(historyFile, root.toString(2), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Capture history is operator context; failure should not affect trading.
        }
    }

    private Summary summarize(List<Entry> entries) {
        BigDecimal estimatedPnl = Monetary.zero();
        BigDecimal actualPnl = Monetary.zero();
        BigDecimal actualValue = Monetary.zero();
        int capturedStocks = 0;
        Instant lastTimestamp = null;
        for (Entry entry : entries) {
            estimatedPnl = estimatedPnl.add(entry.estimatedPnl());
            actualPnl = actualPnl.add(entry.actualPnl());
            actualValue = actualValue.add(entry.actualBrokerExecutionValue());
            capturedStocks += Math.max(0, entry.capturedCount());
            if (entry.timestamp() != null && (lastTimestamp == null || entry.timestamp().isAfter(lastTimestamp))) {
                lastTimestamp = entry.timestamp();
            }
        }
        return new Summary(
                entries.size(),
                capturedStocks,
                Monetary.round(estimatedPnl),
                Monetary.round(actualPnl),
                Monetary.round(actualValue),
                lastTimestamp
        );
    }

    private static BigDecimal decimal(JSONObject json, String key) {
        return Monetary.round(new BigDecimal(json.optString(key, "0")));
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Instant.parse(value);
    }

    record Entry(
            String id,
            Instant timestamp,
            int loopNumber,
            String triggerReason,
            String executionFlow,
            int capturedCount,
            BigDecimal totalInvestment,
            BigDecimal estimatedPortfolioValue,
            BigDecimal actualBrokerExecutionValue,
            BigDecimal estimatedPnl,
            BigDecimal actualPnl,
            BigDecimal executionVariance
    ) {
    }

    record Summary(
            int captureCount,
            int capturedStocks,
            BigDecimal estimatedPnl,
            BigDecimal actualPnl,
            BigDecimal actualBrokerExecutionValue,
            Instant lastTimestamp
    ) {
    }
}
