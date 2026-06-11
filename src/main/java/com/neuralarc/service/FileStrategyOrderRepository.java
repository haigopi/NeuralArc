package com.neuralarc.service;

import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyOrderSide;
import com.neuralarc.model.StrategyOrderStatus;
import com.neuralarc.model.StrategyOrderType;
import com.neuralarc.model.StrategyStage;
import com.neuralarc.model.TimeInForce;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class FileStrategyOrderRepository implements StrategyOrderRepository {
    private static final long FLUSH_DELAY_MILLIS = 200L;

    private final Path filePath;
    private final List<StrategyOrder> orders = new ArrayList<>();
    private final Map<String, StrategyOrder> orderById = new HashMap<>();
    private final Map<String, List<StrategyOrder>> ordersByStrategyId = new HashMap<>();
    private final Map<String, StrategyOrder> latestByAlpacaOrderId = new HashMap<>();
    private final Map<String, StrategyOrder> latestByClientOrderId = new HashMap<>();
    private final ScheduledExecutorService flushExecutor;
    private boolean dirty;
    private boolean flushScheduled;

    public FileStrategyOrderRepository(Path filePath) {
        this.filePath = filePath;
        this.flushExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "neuralarc-strategy-order-repo");
            thread.setDaemon(true);
            return thread;
        });
        reloadFromDisk();
        Runtime.getRuntime().addShutdownHook(new Thread(this::close, "neuralarc-strategy-order-repo-shutdown"));
    }

    @Override
    public synchronized void save(StrategyOrder order) {
        StrategyOrder existing = orderById.get(order.id());
        if (existing == null) {
            orders.add(order);
        } else {
            int index = orders.indexOf(existing);
            if (index >= 0) {
                orders.set(index, order);
            }
        }
        rebuildIndexes();
        markDirty();
    }

    @Override
    public synchronized List<StrategyOrder> findByStrategyId(String strategyId) {
        return new ArrayList<>(ordersByStrategyId.getOrDefault(strategyId, List.of()));
    }

    @Override
    public synchronized Optional<StrategyOrder> findLatestByStrategyStage(String strategyId, StrategyStage stage) {
        return findByStrategyId(strategyId).stream()
                .filter(o -> o.stage() == stage)
                .max(Comparator.comparing(StrategyOrder::submittedAt));
    }

    @Override
    public synchronized Optional<StrategyOrder> findByAlpacaOrderId(String alpacaOrderId) {
        if (alpacaOrderId == null || alpacaOrderId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(latestByAlpacaOrderId.get(alpacaOrderId));
    }

    @Override
    public synchronized Optional<StrategyOrder> findByClientOrderId(String clientOrderId) {
        if (clientOrderId == null || clientOrderId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(latestByClientOrderId.get(clientOrderId));
    }

    @Override
    public synchronized void deleteByStrategyId(String strategyId) {
        if (orders.removeIf(order -> order.strategyId().equals(strategyId))) {
            rebuildIndexes();
            markDirty();
        }
    }

    public synchronized void reloadFromDisk() {
        orders.clear();
        orders.addAll(readAllFromDisk());
        rebuildIndexes();
        dirty = false;
        flushScheduled = false;
    }

    public void flushNow() {
        List<StrategyOrder> snapshot;
        synchronized (this) {
            if (!dirty) {
                return;
            }
            snapshot = new ArrayList<>(orders);
            dirty = false;
            flushScheduled = false;
        }
        persistSnapshot(snapshot);
    }

    public void close() {
        flushNow();
        flushExecutor.shutdownNow();
    }

    private synchronized void markDirty() {
        dirty = true;
        if (flushScheduled) {
            return;
        }
        flushScheduled = true;
        flushExecutor.schedule(this::flushNow, FLUSH_DELAY_MILLIS, TimeUnit.MILLISECONDS);
    }

    private List<StrategyOrder> readAllFromDisk() {
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
                        decimal(o, "limitPrice", "0.00"),
                        decimal(o, "stopPrice", "0.00"),
                        decimal(o, "requestedQuantity", o.optString("quantity", "0")),
                        decimal(o, "filledQuantity", "0.00"),
                        decimal(o, "filledAveragePrice", "0.00"),
                        StrategyOrderStatus.valueOf(o.getString("status")),
                        Instant.parse(o.getString("submittedAt")),
                        parseInstant(o.optString("updatedAt", "")),
                        parseInstant(o.optString("filledAt", "")),
                        o.optString("rawResponseJson", "{}"),
                        TimeInForce.safeValue(o.optString("timeInForce", "DAY"))
                ));
            }
            return orders;
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    private void rebuildIndexes() {
        orderById.clear();
        ordersByStrategyId.clear();
        latestByAlpacaOrderId.clear();
        latestByClientOrderId.clear();
        for (StrategyOrder order : orders) {
            orderById.put(order.id(), order);
            ordersByStrategyId.computeIfAbsent(order.strategyId(), ignored -> new ArrayList<>()).add(order);
            if (order.alpacaOrderId() != null && !order.alpacaOrderId().isBlank()) {
                latestByAlpacaOrderId.merge(order.alpacaOrderId(), order, this::latestOrder);
            }
            if (order.clientOrderId() != null && !order.clientOrderId().isBlank()) {
                latestByClientOrderId.merge(order.clientOrderId(), order, this::latestOrder);
            }
        }
    }

    private StrategyOrder latestOrder(StrategyOrder left, StrategyOrder right) {
        return Comparator.comparing(StrategyOrder::submittedAt).compare(left, right) >= 0 ? left : right;
    }

    private void persistSnapshot(List<StrategyOrder> orders) {
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
            json.put("stopPrice", o.stopPrice().toPlainString());
            json.put("requestedQuantity", o.requestedQuantity().toPlainString());
            json.put("filledQuantity", o.filledQuantity().toPlainString());
            json.put("filledAveragePrice", o.filledAveragePrice().toPlainString());
            json.put("status", o.status().name());
            json.put("timeInForce", o.timeInForce() == null ? TimeInForce.DAY.name() : o.timeInForce().name());
            json.put("submittedAt", o.submittedAt().toString());
            json.put("updatedAt", o.updatedAt() == null ? "" : o.updatedAt().toString());
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

    private BigDecimal decimal(JSONObject object, String key, String fallback) {
        try {
            return new BigDecimal(object.optString(key, fallback));
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Instant.parse(value);
    }
}
