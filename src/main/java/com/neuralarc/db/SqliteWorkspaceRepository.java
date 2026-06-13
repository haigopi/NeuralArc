package com.neuralarc.db;

import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyWorkspace;
import com.neuralarc.service.WorkspaceRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SQLite-backed {@link WorkspaceRepository}.
 *
 * <p>Follows the same contract as {@link SqliteStrategyRepository}: an in-memory
 * {@code LinkedHashMap} cache invalidated on every write and lazily reloaded on read.
 */
public final class SqliteWorkspaceRepository implements WorkspaceRepository {
    private final AppDatabase db;
    private final Map<String, StrategyWorkspace> cache = new LinkedHashMap<>();
    private boolean cacheValid;

    public SqliteWorkspaceRepository(AppDatabase db) {
        this.db = db;
        seedCache();
    }

    public synchronized void invalidateCache() {
        cache.clear();
        cacheValid = false;
    }

    @Override
    public synchronized void save(StrategyWorkspace workspace) {
        upsert(workspace);
        cache.put(workspace.id(), workspace);
        cacheValid = true;
    }

    @Override
    public synchronized Optional<StrategyWorkspace> findById(String id) {
        ensureCache();
        return Optional.ofNullable(cache.get(id));
    }

    @Override
    public synchronized List<StrategyWorkspace> findAll() {
        ensureCache();
        return new ArrayList<>(cache.values());
    }

    @Override
    public synchronized List<StrategyWorkspace> findByMode(StrategyMode mode) {
        ensureCache();
        List<StrategyWorkspace> result = new ArrayList<>();
        for (StrategyWorkspace w : cache.values()) {
            if (w.mode() == mode) {
                result.add(w);
            }
        }
        return result;
    }

    @Override
    public synchronized List<StrategyWorkspace> findActive(StrategyMode mode) {
        ensureCache();
        List<StrategyWorkspace> result = new ArrayList<>();
        for (StrategyWorkspace w : cache.values()) {
            if (w.mode() == mode && !w.archived()) {
                result.add(w);
            }
        }
        return result;
    }

    @Override
    public synchronized void deleteById(String id) {
        try (PreparedStatement ps = db.get().prepareStatement("DELETE FROM strategy_workspaces WHERE id=?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to delete workspace " + id, ex);
        }
        cache.remove(id);
    }

    // -----------------------------------------------------------------------

    private void ensureCache() {
        if (!cacheValid) {
            seedCache();
        }
    }

    private void seedCache() {
        cache.clear();
        try (PreparedStatement ps = db.get().prepareStatement("SELECT * FROM strategy_workspaces ORDER BY rowid");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                StrategyWorkspace w = fromResultSet(rs);
                cache.put(w.id(), w);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load workspaces from DB", ex);
        }
        cacheValid = true;
    }

    private void upsert(StrategyWorkspace w) {
        String sql = """
                INSERT INTO strategy_workspaces (id, name, code, mode, archived, created_at, updated_at)
                VALUES (?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET
                    name=excluded.name, code=excluded.code, mode=excluded.mode,
                    archived=excluded.archived, created_at=excluded.created_at, updated_at=excluded.updated_at
                """;
        try (PreparedStatement ps = db.get().prepareStatement(sql)) {
            ps.setString(1, w.id());
            ps.setString(2, w.name());
            ps.setString(3, w.code());
            ps.setString(4, w.mode().name());
            ps.setInt(5, w.archived() ? 1 : 0);
            ps.setString(6, w.createdAt().toString());
            ps.setString(7, w.updatedAt().toString());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to upsert workspace " + w.id(), ex);
        }
    }

    private StrategyWorkspace fromResultSet(ResultSet rs) throws SQLException {
        return new StrategyWorkspace(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("code"),
                safeMode(rs.getString("mode")),
                rs.getInt("archived") == 1,
                parseInstant(rs.getString("created_at")),
                parseInstant(rs.getString("updated_at"))
        );
    }

    private StrategyMode safeMode(String value) {
        try {
            return StrategyMode.valueOf(value == null ? "PAPER" : value);
        } catch (IllegalArgumentException ex) {
            return StrategyMode.PAPER;
        }
    }

    private Instant parseInstant(String value) {
        try {
            return value == null || value.isBlank() ? Instant.now() : Instant.parse(value);
        } catch (Exception ex) {
            return Instant.now();
        }
    }
}
