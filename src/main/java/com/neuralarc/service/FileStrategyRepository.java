package com.neuralarc.service;

import com.neuralarc.model.ProfitHoldType;
import com.neuralarc.model.StopLossType;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyLifecycleState;
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
                Strategy strategy = new Strategy(
                        o.getString("id"),
                        o.optString("name", ""),
                        o.optString("symbol", ""),
                        StrategyMode.valueOf(o.optString("mode", "PAPER")),
                        StrategyStatus.valueOf(o.optString("status", "CREATED")),
                        StrategyLifecycleState.valueOf(o.optString("currentState", "CREATED")),
                        decimal(o, "baseBuyLimitPrice", o.optString("initialBuyLimitPrice", "0.00")),
                        o.optInt("baseBuyQuantity", o.optInt("initialBuyQuantity", 0)),
                        decimal(o, "buyLimit1Price", o.optString("lossBuyLevel1Price", "0.00")),
                        o.optInt("buyLimit1Quantity", o.optInt("lossBuyLevel1Quantity", 0)),
                        decimal(o, "buyLimit2Price", o.optString("lossBuyLevel2Price", "0.00")),
                        o.optInt("buyLimit2Quantity", o.optInt("lossBuyLevel2Quantity", 0)),
                        o.optBoolean("automatedStopLossEnabled", decimal(o, "stopLossPrice", "0.00").compareTo(BigDecimal.ZERO) > 0),
                        StopLossType.valueOf(o.optString("stopLossType", "FIXED_PRICE")),
                        decimal(o, "stopLossPrice", "0.00"),
                        decimal(o, "stopLossPercent", "0.00"),
                        o.optBoolean("optionalLossExitEnabled", false),
                        decimal(o, "optionalLossExitPrice", "0.00"),
                        o.optBoolean("targetSellEnabled", true),
                        decimal(o, "targetSellPrice", "0.00"),
                        decimal(o, "targetSellQuantityOrPercent", "100.00"),
                        o.optBoolean("targetSellPercentBased", true),
                        o.optBoolean("profitHoldEnabled", false),
                        ProfitHoldType.valueOf(o.optString("profitHoldType", "PERCENT_TRAILING")),
                        decimal(o, "profitHoldPercent", o.optString("profitHoldPercentOrAmount", "0.00")),
                        decimal(o, "profitHoldAmount", "0.00"),
                        decimal(o, "highestObservedPriceAfterTarget", o.optString("highestPriceAfterTarget", "0.00")),
                        o.optBoolean("restartAfterExitEnabled", false),
                        o.optInt("maxTotalQuantity", 0),
                        decimal(o, "maxCapitalAllowed", "0.00"),
                        o.optInt("pollingIntervalSeconds", o.optInt("pollingSeconds", 10)),
                        Instant.parse(o.optString("createdAt", Instant.now().toString())),
                        Instant.parse(o.optString("updatedAt", Instant.now().toString()))
                );
                String lastPolledAt = o.optString("lastPolledAt", "");
                if (!lastPolledAt.isBlank()) {
                    strategy.setLastPolledAt(Instant.parse(lastPolledAt));
                }
                String lastError = o.optString("lastError", "");
                if (!lastError.isBlank()) {
                    strategy.setLastError(lastError);
                }
                strategy.setLastEvent(o.optString("lastEvent", ""));
                strategy.setLatestOrderStatus(o.optString("latestOrderStatus", ""));
                strategy.setLatestAlpacaOrderId(o.optString("latestAlpacaOrderId", ""));
                result.add(strategy);
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

    @Override
    public synchronized void deleteById(String id) {
        List<Strategy> all = findAll().stream().filter(s -> !s.id().equals(id)).toList();
        writeAll(all);
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
            o.put("currentState", s.currentState().name());
            o.put("baseBuyLimitPrice", s.baseBuyLimitPrice().toPlainString());
            o.put("baseBuyQuantity", s.baseBuyQuantity());
            o.put("buyLimit1Price", s.buyLimit1Price().toPlainString());
            o.put("buyLimit1Quantity", s.buyLimit1Quantity());
            o.put("buyLimit2Price", s.buyLimit2Price().toPlainString());
            o.put("buyLimit2Quantity", s.buyLimit2Quantity());
            o.put("automatedStopLossEnabled", s.automatedStopLossEnabled());
            o.put("stopLossType", s.stopLossType().name());
            o.put("stopLossPrice", s.stopLossPrice().toPlainString());
            o.put("stopLossPercent", s.stopLossPercent().toPlainString());
            o.put("optionalLossExitEnabled", s.optionalLossExitEnabled());
            o.put("optionalLossExitPrice", s.optionalLossExitPrice().toPlainString());
            o.put("targetSellEnabled", s.targetSellEnabled());
            o.put("targetSellPrice", s.targetSellPrice().toPlainString());
            o.put("targetSellQuantityOrPercent", s.targetSellQuantityOrPercent().toPlainString());
            o.put("targetSellPercentBased", s.targetSellPercentBased());
            o.put("profitHoldEnabled", s.profitHoldEnabled());
            o.put("profitHoldType", s.profitHoldType().name());
            o.put("profitHoldPercent", s.profitHoldPercent().toPlainString());
            o.put("profitHoldAmount", s.profitHoldAmount().toPlainString());
            o.put("highestObservedPriceAfterTarget", s.highestObservedPriceAfterTarget().toPlainString());
            o.put("restartAfterExitEnabled", s.restartAfterExitEnabled());
            o.put("maxTotalQuantity", s.maxTotalQuantity());
            o.put("maxCapitalAllowed", s.maxCapitalAllowed().toPlainString());
            o.put("pollingIntervalSeconds", s.pollingIntervalSeconds());
            o.put("createdAt", s.createdAt().toString());
            o.put("updatedAt", s.updatedAt().toString());
            o.put("lastPolledAt", s.lastPolledAt() == null ? "" : s.lastPolledAt().toString());
            o.put("lastError", s.lastError() == null ? "" : s.lastError());
            o.put("lastEvent", s.lastEvent() == null ? "" : s.lastEvent());
            o.put("latestOrderStatus", s.latestOrderStatus() == null ? "" : s.latestOrderStatus());
            o.put("latestAlpacaOrderId", s.latestAlpacaOrderId() == null ? "" : s.latestAlpacaOrderId());
            arr.put(o);
        }
        try {
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, arr.toString());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to persist strategies", ex);
        }
    }

    private BigDecimal decimal(JSONObject object, String key, String fallback) {
        String value = object.optString(key, fallback);
        if (value == null || value.isBlank()) {
            value = fallback;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }
}
