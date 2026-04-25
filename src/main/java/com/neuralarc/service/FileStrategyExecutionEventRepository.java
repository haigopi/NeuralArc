package com.neuralarc.service;

import com.neuralarc.model.StrategyEventType;
import com.neuralarc.model.StrategyExecutionEvent;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class FileStrategyExecutionEventRepository implements StrategyExecutionEventRepository {
    private final Path filePath;

    public FileStrategyExecutionEventRepository(Path filePath) {
        this.filePath = filePath;
    }

    @Override
    public synchronized void save(StrategyExecutionEvent event) {
        List<StrategyExecutionEvent> all = findAll();
        all.add(event);
        writeAll(all);
    }

    @Override
    public synchronized List<StrategyExecutionEvent> findByStrategyId(String strategyId) {
        return findAll().stream().filter(e -> e.strategyId().equals(strategyId)).toList();
    }

    private List<StrategyExecutionEvent> findAll() {
        if (!Files.exists(filePath)) {
            return new ArrayList<>();
        }
        try {
            JSONArray arr = new JSONArray(Files.readString(filePath));
            List<StrategyExecutionEvent> result = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                result.add(new StrategyExecutionEvent(
                        o.getString("id"),
                        o.getString("strategyId"),
                        StrategyEventType.valueOf(o.getString("eventType")),
                        o.optString("message", ""),
                        o.optString("metadataJson", "{}"),
                        Instant.parse(o.getString("createdAt"))
                ));
            }
            return result;
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    private void writeAll(List<StrategyExecutionEvent> events) {
        JSONArray arr = new JSONArray();
        for (StrategyExecutionEvent e : events) {
            JSONObject o = new JSONObject();
            o.put("id", e.id());
            o.put("strategyId", e.strategyId());
            o.put("eventType", e.eventType().name());
            o.put("message", e.message());
            o.put("metadataJson", e.metadataJson());
            o.put("createdAt", e.createdAt().toString());
            arr.put(o);
        }
        try {
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, arr.toString());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to persist strategy events", ex);
        }
    }
}

