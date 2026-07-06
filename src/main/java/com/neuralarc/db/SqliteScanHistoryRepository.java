package com.neuralarc.db;

import com.neuralarc.model.ScanHistoryEntry;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SQLite-backed store for strategy scan-run history. Mirrors the in-memory cache + invalidate
 * contract used by the other {@code Sqlite*Repository} classes. Each workspace keeps at most
 * {@link #MAX_ENTRIES_PER_WORKSPACE} entries; older rows are pruned on write so the log stays
 * bounded. Reads return the most recent entries first and are served from the cache so the UI can
 * refresh the empty-state history on the EDT without touching the database.
 */
public final class SqliteScanHistoryRepository {
    /** Retain a small rolling window per workspace; the UI shows the most recent 10. */
    public static final int MAX_ENTRIES_PER_WORKSPACE = 20;

    private final AppDatabase db;
    // workspaceId -> entries, most recent first.
    private final Map<String, List<ScanHistoryEntry>> cache = new LinkedHashMap<>();
    private boolean cacheValid;

    public SqliteScanHistoryRepository(AppDatabase db) {
        this.db = db;
        seedCache();
    }

    public synchronized void invalidateCache() {
        cache.clear();
        cacheValid = false;
    }

    /** Persist a scan run and prune the workspace back to the retention window. */
    public synchronized void save(ScanHistoryEntry entry) {
        if (entry == null) {
            return;
        }
        insert(entry);
        pruneWorkspace(entry.workspaceId());
        ensureCache();
        List<ScanHistoryEntry> entries = cache.computeIfAbsent(entry.workspaceId(), ignored -> new ArrayList<>());
        entries.add(0, entry);
        while (entries.size() > MAX_ENTRIES_PER_WORKSPACE) {
            entries.remove(entries.size() - 1);
        }
    }

    /** Most recent {@code limit} entries for {@code workspaceId}, newest first. */
    public synchronized List<ScanHistoryEntry> findRecentByWorkspace(String workspaceId, int limit) {
        ensureCache();
        List<ScanHistoryEntry> entries = cache.get(workspaceId == null ? "" : workspaceId);
        if (entries == null || entries.isEmpty() || limit <= 0) {
            return List.of();
        }
        return new ArrayList<>(entries.subList(0, Math.min(limit, entries.size())));
    }

    // -----------------------------------------------------------------------

    private void ensureCache() {
        if (!cacheValid) {
            seedCache();
        }
    }

    private void seedCache() {
        cache.clear();
        try (PreparedStatement ps = db.get().prepareStatement(
                "SELECT * FROM strategy_scan_history ORDER BY ran_at DESC, rowid DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ScanHistoryEntry entry = fromResultSet(rs);
                cache.computeIfAbsent(entry.workspaceId(), ignored -> new ArrayList<>()).add(entry);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load strategy scan history from DB", ex);
        }
        cacheValid = true;
    }

    private void insert(ScanHistoryEntry entry) {
        String sql = "INSERT INTO strategy_scan_history (id, workspace_id, ran_at, trigger, summary) "
                + "VALUES (?,?,?,?,?)";
        try (PreparedStatement ps = db.get().prepareStatement(sql)) {
            ps.setString(1, entry.id());
            ps.setString(2, entry.workspaceId());
            ps.setString(3, entry.ranAt().toString());
            ps.setString(4, entry.trigger());
            ps.setString(5, entry.summary());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to insert scan history entry " + entry.id(), ex);
        }
    }

    private void pruneWorkspace(String workspaceId) {
        String sql = """
                DELETE FROM strategy_scan_history
                WHERE workspace_id=?
                  AND id NOT IN (
                      SELECT id FROM strategy_scan_history
                      WHERE workspace_id=?
                      ORDER BY ran_at DESC, rowid DESC
                      LIMIT ?
                  )""";
        try (PreparedStatement ps = db.get().prepareStatement(sql)) {
            ps.setString(1, workspaceId);
            ps.setString(2, workspaceId);
            ps.setInt(3, MAX_ENTRIES_PER_WORKSPACE);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to prune scan history for workspace " + workspaceId, ex);
        }
    }

    private ScanHistoryEntry fromResultSet(ResultSet rs) throws SQLException {
        return new ScanHistoryEntry(
                rs.getString("id"),
                rs.getString("workspace_id"),
                parseInstant(rs.getString("ran_at")),
                rs.getString("trigger"),
                rs.getString("summary")
        );
    }

    private Instant parseInstant(String value) {
        try {
            return value == null || value.isBlank() ? Instant.now() : Instant.parse(value);
        } catch (Exception ex) {
            return Instant.now();
        }
    }
}
