package com.neuralarc.service;

import com.neuralarc.model.StrategyMode;
import com.neuralarc.util.Monetary;
import org.json.JSONObject;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

public class PersistentAggregatePnlStore {
    private static final String KEY_ARCHIVED_PAPER = "paperArchivedRealized";
    private static final String KEY_ARCHIVED_LIVE = "liveArchivedRealized";

    private final Path filePath;
    private BigDecimal paperArchivedRealized = BigDecimal.ZERO.setScale(2);
    private BigDecimal liveArchivedRealized = BigDecimal.ZERO.setScale(2);

    public PersistentAggregatePnlStore(Path filePath) {
        this.filePath = filePath;
        load();
    }

    public synchronized BigDecimal archivedRealized(StrategyMode mode) {
        return mode == StrategyMode.LIVE ? Monetary.round(liveArchivedRealized) : Monetary.round(paperArchivedRealized);
    }

    public synchronized void addArchivedRealized(StrategyMode mode, BigDecimal delta) {
        BigDecimal normalizedDelta = Monetary.round(delta);
        if (normalizedDelta.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        if (mode == StrategyMode.LIVE) {
            liveArchivedRealized = Monetary.round(liveArchivedRealized.add(normalizedDelta));
        } else {
            paperArchivedRealized = Monetary.round(paperArchivedRealized.add(normalizedDelta));
        }
        save();
    }

    /**
     * Resets both realized P&L values to zero and deletes the backing file.
     * Call this when the user wipes all local data.
     */
    public synchronized void reset() {
        paperArchivedRealized = BigDecimal.ZERO.setScale(2);
        liveArchivedRealized = BigDecimal.ZERO.setScale(2);
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException ignored) {
            // Non-fatal — in-memory values are already zeroed.
        }
    }

    private void load() {
        if (Files.exists(filePath)) {
            try {
                String raw = Files.readString(filePath).trim();
                if (!raw.isBlank()) {
                    JSONObject json = new JSONObject(raw);
                    paperArchivedRealized = parseMoney(json.optString(KEY_ARCHIVED_PAPER, "0"));
                    liveArchivedRealized = parseMoney(json.optString(KEY_ARCHIVED_LIVE, "0"));
                    return;
                }
            } catch (Exception ignored) {
                // Try legacy fallback below.
            }
        }

        Path legacyPath = filePath.getParent().resolve("aggregate-pnl.properties");
        if (!Files.exists(legacyPath)) {
            return;
        }
        try {
            java.util.Properties legacy = new java.util.Properties();
            legacy.load(Files.newInputStream(legacyPath));
            paperArchivedRealized = parseMoney(legacy.getProperty("archived.paper.realized"));
            liveArchivedRealized = parseMoney(legacy.getProperty("archived.live.realized"));
            save();
        } catch (IOException ignored) {
            // Fallback to empty totals when file cannot be read.
        }
    }

    private void save() {
        try {
            Files.createDirectories(filePath.getParent());
            JSONObject json = new JSONObject();
            json.put(KEY_ARCHIVED_PAPER, Monetary.round(paperArchivedRealized).toPlainString());
            json.put(KEY_ARCHIVED_LIVE, Monetary.round(liveArchivedRealized).toPlainString());
            Files.writeString(filePath, json.toString(2));
        } catch (IOException ignored) {
            // Keep app running if persistence fails.
        }
    }

    private BigDecimal parseMoney(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO.setScale(2);
        }
        try {
            return Monetary.round(new BigDecimal(value.trim()));
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO.setScale(2);
        }
    }
}

