package com.neuralarc.db;

import com.neuralarc.model.OrbSchedule;
import com.neuralarc.orb.OrbConfigCodec;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SQLite-backed store for autonomous ORB schedules. Mirrors the in-memory cache + invalidate
 * contract used by the other {@code Sqlite*Repository} classes.
 */
public final class SqliteOrbScheduleRepository {
    private final AppDatabase db;
    private final Map<String, OrbSchedule> cache = new LinkedHashMap<>();
    private boolean cacheValid;

    public SqliteOrbScheduleRepository(AppDatabase db) {
        this.db = db;
        seedCache();
    }

    public synchronized void invalidateCache() {
        cache.clear();
        cacheValid = false;
    }

    public synchronized void save(OrbSchedule schedule) {
        upsert(schedule);
        cache.put(schedule.id(), schedule);
        cacheValid = true;
    }

    public synchronized Optional<OrbSchedule> findById(String id) {
        ensureCache();
        return Optional.ofNullable(cache.get(id));
    }

    public synchronized List<OrbSchedule> findAll() {
        ensureCache();
        return new ArrayList<>(cache.values());
    }

    public synchronized Optional<OrbSchedule> findByWorkspaceId(String workspaceId) {
        ensureCache();
        return cache.values().stream()
                .filter(s -> s.workspaceId().equals(workspaceId))
                .findFirst();
    }

    public synchronized void deleteById(String id) {
        try (PreparedStatement ps = db.get().prepareStatement("DELETE FROM orb_schedules WHERE id=?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to delete ORB schedule " + id, ex);
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
        try (PreparedStatement ps = db.get().prepareStatement("SELECT * FROM orb_schedules ORDER BY rowid");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                OrbSchedule schedule = fromResultSet(rs);
                cache.put(schedule.id(), schedule);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load ORB schedules from DB", ex);
        }
        cacheValid = true;
    }

    private void upsert(OrbSchedule schedule) {
        String sql = """
                INSERT INTO orb_schedules
                    (id, enabled, range_analysis_time_et, window_end_et, execute_after_range_close,
                     workspace_id, config_json, updated_at)
                VALUES (?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET
                    enabled=excluded.enabled,
                    range_analysis_time_et=excluded.range_analysis_time_et,
                    window_end_et=excluded.window_end_et,
                    execute_after_range_close=excluded.execute_after_range_close,
                    workspace_id=excluded.workspace_id,
                    config_json=excluded.config_json,
                    updated_at=excluded.updated_at
                """;
        try (PreparedStatement ps = db.get().prepareStatement(sql)) {
            ps.setString(1, schedule.id());
            ps.setInt(2, schedule.enabled() ? 1 : 0);
            ps.setString(3, schedule.rangeAnalysisTimeEt().toString());
            ps.setString(4, schedule.executionWindowEndEt().toString());
            ps.setInt(5, schedule.executeAfterRangeClose() ? 1 : 0);
            ps.setString(6, schedule.workspaceId());
            ps.setString(7, OrbConfigCodec.toJson(schedule.config()));
            ps.setString(8, Instant.now().toString());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to upsert ORB schedule " + schedule.id(), ex);
        }
    }

    private OrbSchedule fromResultSet(ResultSet rs) throws SQLException {
        return new OrbSchedule(
                rs.getString("id"),
                rs.getInt("enabled") == 1,
                parseTime(rs.getString("range_analysis_time_et")),
                parseTime(rs.getString("window_end_et")),
                rs.getInt("execute_after_range_close") == 1,
                rs.getString("workspace_id"),
                OrbConfigCodec.fromJson(rs.getString("config_json"))
        );
    }

    private LocalTime parseTime(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalTime.parse(value);
        } catch (Exception ex) {
            return null;
        }
    }
}
