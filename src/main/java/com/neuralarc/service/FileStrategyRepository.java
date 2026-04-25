package com.neuralarc.service;

import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyStatus;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FileStrategyRepository implements StrategyRepository {
    private final Path filePath;

    public FileStrategyRepository(Path filePath) {
        this.filePath = filePath;
    }

    @Override
    public synchronized void save(Strategy strategy) {
        List<Strategy> all = findAll();
        boolean replaced = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id().equals(strategy.id())) {
                all.set(i, strategy);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            all.add(strategy);
        }
        writeAll(all);
    }

    @Override
    public synchronized Optional<Strategy> findById(String id) {
        return findAll().stream().filter(s -> s.id().equals(id)).findFirst();
    }

    @Override
    public synchronized List<Strategy> findAll() {
        if (!Files.exists(filePath)) {
            return new ArrayList<>();
        }
        try {
            String json = Files.readString(filePath);
            JSONArray arr = new JSONArray(json.isBlank() ? "[]" : json);
            List<Strategy> result = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Strategy s = new Strategy(
                        o.getString("id"),
                        o.optString("name", ""),
                        o.optString("symbol", ""),
                        StrategyMode.valueOf(o.optString("mode", "PAPER")),
                        StrategyStatus.valueOf(o.optString("status", "CREATED")),
                        new BigDecimal(o.optString("initialBuyLimitPrice", "0.00")),
                        o.optInt("initialBuyQuantity", 0),
                        new BigDecimal(o.optString("stopLossPrice", "0.00")),
                        new BigDecimal(o.optString("targetSellPrice", "0.00")),
                        o.optBoolean("profitHoldEnabled", false),
                        new BigDecimal(o.optString("profitHoldPercentOrAmount", "0.00")),
                        new BigDecimal(o.optString("lossBuyLevel1Price", "0.00")),
                        o.optInt("lossBuyLevel1Quantity", 0),
                        new BigDecimal(o.optString("lossBuyLevel2Price", "0.00")),
                        o.optInt("lossBuyLevel2Quantity", 0),
                        o.optInt("maxTotalQuantity", 0),
                        new BigDecimal(o.optString("maxCapitalAllowed", "0.00")),
                        Instant.parse(o.optString("createdAt", Instant.now().toString())),
                        Instant.parse(o.optString("updatedAt", Instant.now().toString()))
                );
                String lastPolledAt = o.optString("lastPolledAt", "");
                if (!lastPolledAt.isBlank()) {
                    s.setLastPolledAt(Instant.parse(lastPolledAt));
                }
                String lastError = o.optString("lastError", "");
                if (!lastError.isBlank()) {
                    s.setLastError(lastError);
                }
                if (o.optBoolean("profitHoldArmed", false)) {
                    s.armProfitHold(new BigDecimal(o.optString("highestPriceAfterTarget", "0.00")));
                }
                result.add(s);
            }
            return result;
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    @Override
    public synchronized List<Strategy> findActive() {
        return findAll().stream().filter(s -> s.status() == StrategyStatus.ACTIVE).toList();
    }

    private void writeAll(List<Strategy> strategies) {
        JSONArray arr = new JSONArray();
        for (Strategy s : strategies) {
            JSONObject o = new JSONObject();
            o.put("id", s.id());
            o.put("name", s.name());
            o.put("symbol", s.symbol());
            o.put("mode", s.mode().name());
            o.put("status", s.status().name());
            o.put("initialBuyLimitPrice", s.initialBuyLimitPrice().toPlainString());
            o.put("initialBuyQuantity", s.initialBuyQuantity());
            o.put("stopLossPrice", s.stopLossPrice().toPlainString());
            o.put("targetSellPrice", s.targetSellPrice().toPlainString());
            o.put("profitHoldEnabled", s.profitHoldEnabled());
            o.put("profitHoldPercentOrAmount", s.profitHoldPercentOrAmount().toPlainString());
            o.put("lossBuyLevel1Price", s.lossBuyLevel1Price().toPlainString());
            o.put("lossBuyLevel1Quantity", s.lossBuyLevel1Quantity());
            o.put("lossBuyLevel2Price", s.lossBuyLevel2Price().toPlainString());
            o.put("lossBuyLevel2Quantity", s.lossBuyLevel2Quantity());
            o.put("maxTotalQuantity", s.maxTotalQuantity());
            o.put("maxCapitalAllowed", s.maxCapitalAllowed().toPlainString());
            o.put("createdAt", s.createdAt().toString());
            o.put("updatedAt", s.updatedAt().toString());
            o.put("lastPolledAt", s.lastPolledAt() == null ? "" : s.lastPolledAt().toString());
            o.put("lastError", s.lastError() == null ? "" : s.lastError());
            o.put("profitHoldArmed", s.profitHoldArmed());
            o.put("highestPriceAfterTarget", s.highestPriceAfterTarget().toPlainString());
            arr.put(o);
        }
        try {
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, arr.toString());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to persist strategies", ex);
        }
    }
}

