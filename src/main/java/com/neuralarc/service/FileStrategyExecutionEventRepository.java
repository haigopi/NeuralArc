package com.neuralarc.service;

import com.neuralarc.model.StrategyEventType;
import com.neuralarc.model.StrategyExecutionEvent;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class FileStrategyExecutionEventRepository implements StrategyExecutionEventRepository {
    private static final long FLUSH_DELAY_MILLIS = 500L;

    private final Path filePath;
    private final List<StrategyExecutionEvent> events = new ArrayList<>();
    private final Map<String, List<StrategyExecutionEvent>> eventsByStrategyId = new HashMap<>();
    private final ScheduledExecutorService flushExecutor;
    private boolean dirty;
    private boolean flushScheduled;

    public FileStrategyExecutionEventRepository(Path filePath) {
        this.filePath = filePath;
        this.flushExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "neuralarc-strategy-event-repo");
            thread.setDaemon(true);
            return thread;
        });
        reloadFromDisk();
        Runtime.getRuntime().addShutdownHook(new Thread(this::close, "neuralarc-strategy-event-repo-shutdown"));
    }

    @Override
    public synchronized void save(StrategyExecutionEvent event) {
        events.add(event);
        eventsByStrategyId.computeIfAbsent(event.strategyId(), ignored -> new ArrayList<>()).add(event);
        markDirty();
    }

    @Override
    public synchronized List<StrategyExecutionEvent> findByStrategyId(String strategyId) {
        return new ArrayList<>(eventsByStrategyId.getOrDefault(strategyId, List.of()));
    }

    @Override
    public synchronized void deleteByStrategyId(String strategyId) {
        if (events.removeIf(event -> event.strategyId().equals(strategyId))) {
            rebuildIndexes();
            markDirty();
        }
    }

    public synchronized void reloadFromDisk() {
        events.clear();
        events.addAll(readAllFromDisk());
        rebuildIndexes();
        dirty = false;
        flushScheduled = false;
    }

    public void flushNow() {
        List<StrategyExecutionEvent> snapshot;
        synchronized (this) {
            if (!dirty) {
                return;
            }
            snapshot = new ArrayList<>(events);
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

    private List<StrategyExecutionEvent> readAllFromDisk() {
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

    private synchronized void rebuildIndexes() {
        eventsByStrategyId.clear();
        for (StrategyExecutionEvent event : events) {
            eventsByStrategyId.computeIfAbsent(event.strategyId(), ignored -> new ArrayList<>()).add(event);
        }
    }

    private void persistSnapshot(List<StrategyExecutionEvent> events) {
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
