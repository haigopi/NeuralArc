package com.neuralarc.db;

import com.neuralarc.diphunter.DipHunterConfigCodec;
import com.neuralarc.model.DipHunterSchedule;

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
 * SQLite-backed store for autonomous Dip Hunter schedules. Mirrors the in-memory cache + invalidate
 * contract used by the other {@code Sqlite*Repository} classes.
 */
public final class SqliteDipHunterScheduleRepository {
    private final AppDatabase db;
    private final Map<String, DipHunterSchedule> cache = new LinkedHashMap<>();
    private boolean cacheValid;

    public SqliteDipHunterScheduleRepository(AppDatabase db) {
        this.db = db;
        seedCache();
    }

    public synchronized void invalidateCache() {
        cache.clear();
        cacheValid = false;
    }

    public synchronized void save(DipHunterSchedule schedule) {
        upsert(schedule);
        cache.put(schedule.id(), schedule);
        cacheValid = true;
    }

    public synchronized Optional<DipHunterSchedule> findById(String id) {
        ensureCache();
        return Optional.ofNullable(cache.get(id));
    }

    public synchronized List<DipHunterSchedule> findAll() {
        ensureCache();
        return new ArrayList<>(cache.values());
    }

    public synchronized Optional<DipHunterSchedule> findByWorkspaceId(String workspaceId) {
        ensureCache();
        return cache.values().stream()
                .filter(schedule -> schedule.workspaceId().equals(workspaceId))
                .findFirst();
    }

    public synchronized void deleteById(String id) {
        try (PreparedStatement ps = db.get().prepareStatement("DELETE FROM dip_hunter_schedules WHERE id=?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to delete Dip Hunter schedule " + id, ex);
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
        try (PreparedStatement ps = db.get().prepareStatement("SELECT * FROM dip_hunter_schedules ORDER BY rowid");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                DipHunterSchedule schedule = fromResultSet(rs);
                cache.put(schedule.id(), schedule);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load Dip Hunter schedules from DB", ex);
        }
        cacheValid = true;
    }

    private void upsert(DipHunterSchedule schedule) {
        String sql = """
                INSERT INTO dip_hunter_schedules
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
            ps.setString(8, DipHunterConfigCodec.toJson(schedule.config()));
            ps.setString(9, Instant.now().toString());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to upsert Dip Hunter schedule " + schedule.id(), ex);
        }
    }

    private DipHunterSchedule fromResultSet(ResultSet rs) throws SQLException {
        return new DipHunterSchedule(
                rs.getString("id"),
                rs.getInt("enabled") == 1,
                parseTime(rs.getString("scan_time_et")),
                parseTime(rs.getString("window_start_et")),
                parseTime(rs.getString("window_end_et")),
                rs.getInt("execute_after_scan") == 1,
                rs.getString("workspace_id"),
                DipHunterConfigCodec.fromJson(rs.getString("config_json"))
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
