package com.neuralarc.db;

import com.neuralarc.model.StrategyEventType;
import com.neuralarc.model.StrategyExecutionEvent;
import com.neuralarc.service.StrategyExecutionEventRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SQLite-backed {@link StrategyExecutionEventRepository}.
 *
 * <p>Maintains an in-memory index keyed by strategy-id for fast
 * {@link #findByStrategyId} calls.  Writes go directly to SQLite –
 * no debounce is required.
 */
public final class SqliteStrategyExecutionEventRepository implements StrategyExecutionEventRepository {

    private final AppDatabase db;

    // In-memory index — rebuilt on construction and kept up-to-date on writes.
    private final List<StrategyExecutionEvent> events = new ArrayList<>();
    private final Map<String, List<StrategyExecutionEvent>> eventsByStrategyId = new HashMap<>();

    public SqliteStrategyExecutionEventRepository(AppDatabase db) {
        this.db = db;
        seedCache();
    }

    // -----------------------------------------------------------------------

    @Override
    public synchronized void save(StrategyExecutionEvent event) {
        insert(event);
        events.add(event);
        eventsByStrategyId.computeIfAbsent(event.strategyId(), k -> new ArrayList<>()).add(event);
    }

    @Override
    public synchronized List<StrategyExecutionEvent> findByStrategyId(String strategyId) {
        return new ArrayList<>(eventsByStrategyId.getOrDefault(strategyId, List.of()));
    }

    @Override
    public synchronized void deleteByStrategyId(String strategyId) {
        try (PreparedStatement ps = db.get().prepareStatement(
                "DELETE FROM strategy_events WHERE strategy_id=?")) {
            ps.setString(1, strategyId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to delete events for strategy " + strategyId, ex);
        }
        events.removeIf(e -> e.strategyId().equals(strategyId));
        rebuildIndexes();
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private void seedCache() {
        events.clear();
        try (PreparedStatement ps = db.get().prepareStatement(
                "SELECT * FROM strategy_events ORDER BY created_at");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                events.add(fromResultSet(rs));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load strategy events from DB", ex);
        }
        rebuildIndexes();
    }

    private void insert(StrategyExecutionEvent e) {
        String sql = """
                INSERT OR IGNORE INTO strategy_events (
                    id, strategy_id, event_type, message, metadata_json, created_at
                ) VALUES (?,?,?,?,?,?)
                """;
        try (PreparedStatement ps = db.get().prepareStatement(sql)) {
            ps.setString(1, e.id());
            ps.setString(2, e.strategyId());
            ps.setString(3, e.eventType().name());
            ps.setString(4, e.message() == null ? "" : e.message());
            ps.setString(5, e.metadataJson() == null ? "{}" : e.metadataJson());
            ps.setString(6, e.createdAt().toString());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to insert strategy event " + e.id(), ex);
        }
    }

    private StrategyExecutionEvent fromResultSet(ResultSet rs) throws SQLException {
        return new StrategyExecutionEvent(
                rs.getString("id"),
                rs.getString("strategy_id"),
                StrategyEventType.valueOf(rs.getString("event_type")),
                rs.getString("message"),
                rs.getString("metadata_json"),
                Instant.parse(rs.getString("created_at"))
        );
    }

    private void rebuildIndexes() {
        eventsByStrategyId.clear();
        for (StrategyExecutionEvent event : events) {
            eventsByStrategyId.computeIfAbsent(event.strategyId(), k -> new ArrayList<>()).add(event);
        }
    }

    /** Reloads the in-memory cache from the DB (e.g. after an import). */
    public synchronized void reloadFromDb() {
        seedCache();
    }
}

