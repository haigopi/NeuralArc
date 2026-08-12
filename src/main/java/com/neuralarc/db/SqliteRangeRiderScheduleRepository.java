package com.neuralarc.db;

import com.neuralarc.model.RangeRiderSchedule;
import com.neuralarc.rangerider.RangeRiderConfigCodec;

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
 * SQLite-backed store for autonomous Range Rider schedules. Mirrors the in-memory cache + invalidate
 * contract used by the other {@code Sqlite*Repository} classes.
 */
public final class SqliteRangeRiderScheduleRepository {
    private final AppDatabase db;
    private final Map<String, RangeRiderSchedule> cache = new LinkedHashMap<>();
    private boolean cacheValid;

    public SqliteRangeRiderScheduleRepository(AppDatabase db) {
        this.db = db;
        seedCache();
    }

    public synchronized void invalidateCache() {
        cache.clear();
        cacheValid = false;
    }

    public synchronized void save(RangeRiderSchedule schedule) {
        upsert(schedule);
        cache.put(schedule.id(), schedule);
        cacheValid = true;
    }

    public synchronized Optional<RangeRiderSchedule> findById(String id) {
        ensureCache();
        return Optional.ofNullable(cache.get(id));
    }

    public synchronized List<RangeRiderSchedule> findAll() {
        ensureCache();
        return new ArrayList<>(cache.values());
    }

    public synchronized Optional<RangeRiderSchedule> findByWorkspaceId(String workspaceId) {
        ensureCache();
        return cache.values().stream()
                .filter(schedule -> schedule.workspaceId().equals(workspaceId))
                .findFirst();
    }

    public synchronized void deleteById(String id) {
        try (PreparedStatement ps = db.get().prepareStatement("DELETE FROM range_rider_schedules WHERE id=?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to delete Range Rider schedule " + id, ex);
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
        try (PreparedStatement ps = db.get().prepareStatement("SELECT * FROM range_rider_schedules ORDER BY rowid");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                RangeRiderSchedule schedule = fromResultSet(rs);
                cache.put(schedule.id(), schedule);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load Range Rider schedules from DB", ex);
        }
        cacheValid = true;
    }

    private void upsert(RangeRiderSchedule schedule) {
        String sql = """
                INSERT INTO range_rider_schedules
                    (id, enabled, scan_time_et, window_start_et, window_end_et, execute_after_scan,
                     workspace_id, config_json, updated_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET
                    enabled=excluded.enabled, scan_time_et=excluded.scan_time_et,
                    window_start_et=excluded.window_start_et, window_end_et=excluded.window_end_et,
                    execute_after_scan=excluded.execute_after_scan, workspace_id=excluded.workspace_id,
                    config_json=excluded.config_json, updated_at=excluded.updated_at
                """;
        try (PreparedStatement ps = db.get().prepareStatement(sql)) {
            ps.setString(1, schedule.id());
            ps.setInt(2, schedule.enabled() ? 1 : 0);
            ps.setString(3, schedule.scanTimeEt().toString());
            ps.setString(4, schedule.executionWindowStartEt().toString());
            ps.setString(5, schedule.executionWindowEndEt().toString());
            ps.setInt(6, schedule.executeAfterScan() ? 1 : 0);
            ps.setString(7, schedule.workspaceId());
            ps.setString(8, RangeRiderConfigCodec.toJson(schedule.config()));
            ps.setString(9, Instant.now().toString());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to upsert Range Rider schedule " + schedule.id(), ex);
        }
    }

    private RangeRiderSchedule fromResultSet(ResultSet rs) throws SQLException {
        return new RangeRiderSchedule(
                rs.getString("id"),
                rs.getInt("enabled") == 1,
                parseTime(rs.getString("scan_time_et")),
                parseTime(rs.getString("window_start_et")),
                parseTime(rs.getString("window_end_et")),
                rs.getInt("execute_after_scan") == 1,
                rs.getString("workspace_id"),
                RangeRiderConfigCodec.fromJson(rs.getString("config_json"))
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
