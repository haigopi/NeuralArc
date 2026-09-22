package com.neuralarc.db;

import com.neuralarc.model.RecommendationType;
import com.neuralarc.model.SmartPicksSchedule;
import com.neuralarc.model.StrategyMode;

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
 * SQLite-backed store for the Smart Picks workspaces' autonomous scans. Mirrors the in-memory cache +
 * invalidate contract used by the other {@code Sqlite*ScheduleRepository} classes.
 */
public final class SqliteSmartPicksScheduleRepository {
    private final AppDatabase db;
    private final Map<String, SmartPicksSchedule> cache = new LinkedHashMap<>();
    private boolean cacheValid;

    public SqliteSmartPicksScheduleRepository(AppDatabase db) {
        this.db = db;
        seedCache();
    }

    public synchronized void invalidateCache() {
        cache.clear();
        cacheValid = false;
    }

    public synchronized void save(SmartPicksSchedule schedule) {
        String sql = """
                INSERT INTO smart_picks_schedules
                    (id, enabled, workspace_id, workspace_code, scan_time_et, days, execute_after_scan,
                     quantity, term, mode, updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET
                    enabled=excluded.enabled, workspace_id=excluded.workspace_id,
                    workspace_code=excluded.workspace_code, scan_time_et=excluded.scan_time_et,
                    days=excluded.days, execute_after_scan=excluded.execute_after_scan,
                    quantity=excluded.quantity, term=excluded.term, mode=excluded.mode,
                    updated_at=excluded.updated_at
                """;
        try (PreparedStatement ps = db.get().prepareStatement(sql)) {
            ps.setString(1, schedule.id());
            ps.setInt(2, schedule.enabled() ? 1 : 0);
            ps.setString(3, schedule.workspaceId());
            ps.setString(4, schedule.workspaceCode());
            ps.setString(5, schedule.scanTimeEt().toString());
            ps.setString(6, schedule.daysCode());
            ps.setInt(7, schedule.executeAfterScan() ? 1 : 0);
            ps.setInt(8, schedule.quantity());
            ps.setString(9, schedule.term().name());
            ps.setString(10, schedule.mode().name());
            ps.setString(11, Instant.now().toString());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save Smart Picks schedule " + schedule.id(), ex);
        }
        cache.put(schedule.id(), schedule);
        cacheValid = true;
    }

    public synchronized List<SmartPicksSchedule> findAll() {
        ensureCache();
        return new ArrayList<>(cache.values());
    }

    public synchronized Optional<SmartPicksSchedule> findByWorkspaceId(String workspaceId) {
        ensureCache();
        return cache.values().stream().filter(schedule -> schedule.workspaceId().equals(workspaceId)).findFirst();
    }

    public synchronized void deleteById(String id) {
        try (PreparedStatement ps = db.get().prepareStatement("DELETE FROM smart_picks_schedules WHERE id=?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to delete Smart Picks schedule " + id, ex);
        }
        cache.remove(id);
    }

    private void ensureCache() {
        if (!cacheValid) {
            seedCache();
        }
    }

    private void seedCache() {
        cache.clear();
        try (PreparedStatement ps = db.get().prepareStatement("SELECT * FROM smart_picks_schedules ORDER BY rowid");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                SmartPicksSchedule schedule = new SmartPicksSchedule(
                        rs.getString("id"),
                        rs.getInt("enabled") == 1,
                        rs.getString("workspace_id"),
                        rs.getString("workspace_code"),
                        parseTime(rs.getString("scan_time_et")),
                        SmartPicksSchedule.parseDays(rs.getString("days")),
                        rs.getInt("execute_after_scan") == 1,
                        rs.getInt("quantity"),
                        parseEnum(RecommendationType.class, rs.getString("term"), RecommendationType.SHORT_TERM),
                        parseEnum(StrategyMode.class, rs.getString("mode"), StrategyMode.PAPER));
                cache.put(schedule.id(), schedule);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load Smart Picks schedules from DB", ex);
        }
        cacheValid = true;
    }

    private static LocalTime parseTime(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalTime.parse(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, E fallback) {
        try {
            return value == null ? fallback : Enum.valueOf(type, value);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
