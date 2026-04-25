package com.neuralarc.service;

import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyOrderSide;
import com.neuralarc.model.StrategyOrderStatus;
import com.neuralarc.model.StrategyOrderType;
import com.neuralarc.model.StrategyStage;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class FileStrategyOrderRepository implements StrategyOrderRepository {
    private final Path filePath;

    public FileStrategyOrderRepository(Path filePath) {
        this.filePath = filePath;
    }

    @Override
    public synchronized void save(StrategyOrder order) {
        List<StrategyOrder> all = findAll();
        boolean replaced = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id().equals(order.id())) {
                all.set(i, order);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            all.add(order);
        }
        writeAll(all);
    }

    @Override
    public synchronized List<StrategyOrder> findByStrategyId(String strategyId) {
        return findAll().stream().filter(o -> o.strategyId().equals(strategyId)).toList();
    }

    @Override
    public synchronized Optional<StrategyOrder> findLatestByStrategyStage(String strategyId, StrategyStage stage) {
        return findByStrategyId(strategyId).stream()
                .filter(o -> o.stage() == stage)
                .max(Comparator.comparing(StrategyOrder::submittedAt));
    }

    private List<StrategyOrder> findAll() {
        if (!Files.exists(filePath)) {
            return new ArrayList<>();
        }
        try {
            JSONArray arr = new JSONArray(Files.readString(filePath));
            List<StrategyOrder> orders = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                orders.add(new StrategyOrder(
                        o.getString("id"),
                        o.getString("strategyId"),
                        StrategyStage.valueOf(o.getString("stage")),
                        o.optString("alpacaOrderId", null),
                        o.getString("clientOrderId"),
                        o.getString("symbol"),
                        StrategyOrderSide.valueOf(o.getString("side")),
                        StrategyOrderType.valueOf(o.getString("orderType")),
                        new BigDecimal(o.getString("limitPrice")),
                        o.getInt("quantity"),
                        new BigDecimal(o.optString("filledQuantity", "0.00")),
                        StrategyOrderStatus.valueOf(o.getString("status")),
                        Instant.parse(o.getString("submittedAt")),
                        o.optString("filledAt", "").isBlank() ? null : Instant.parse(o.getString("filledAt")),
                        o.optString("rawResponseJson", "{}")
                ));
            }
            return orders;
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    private void writeAll(List<StrategyOrder> orders) {
        JSONArray arr = new JSONArray();
        for (StrategyOrder o : orders) {
            JSONObject json = new JSONObject();
            json.put("id", o.id());
            json.put("strategyId", o.strategyId());
            json.put("stage", o.stage().name());
            json.put("alpacaOrderId", o.alpacaOrderId());
            json.put("clientOrderId", o.clientOrderId());
            json.put("symbol", o.symbol());
            json.put("side", o.side().name());
            json.put("orderType", o.orderType().name());
            json.put("limitPrice", o.limitPrice().toPlainString());
            json.put("quantity", o.quantity());
            json.put("filledQuantity", o.filledQuantity().toPlainString());
            json.put("status", o.status().name());
            json.put("submittedAt", o.submittedAt().toString());
            json.put("filledAt", o.filledAt() == null ? "" : o.filledAt().toString());
            json.put("rawResponseJson", o.rawResponseJson() == null ? "{}" : o.rawResponseJson());
            arr.put(json);
        }
        try {
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, arr.toString());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to persist strategy orders", ex);
        }
    }
}

