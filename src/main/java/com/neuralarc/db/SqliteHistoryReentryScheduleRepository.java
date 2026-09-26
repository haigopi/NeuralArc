package com.neuralarc.db;

import com.neuralarc.model.HistoryReentrySchedule;
import com.neuralarc.model.StrategyMode;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * The saved Trade History re-entry schedule, one per trading mode.
 *
 * <p>Cached in memory like the other schedule repositories: the scheduler asks for it once a minute
 * and the header reads it on every refresh, neither of which should touch the database.
 */
public final class SqliteHistoryReentryScheduleRepository {
    private final AppDatabase db;
    private final Map<StrategyMode, HistoryReentrySchedule> cache = new EnumMap<>(StrategyMode.class);
    private boolean cacheValid;

    public SqliteHistoryReentryScheduleRepository(AppDatabase db) {
        this.db = db;
    }

    public synchronized void invalidateCache() {
        cache.clear();
        cacheValid = false;
    }

    public synchronized Optional<HistoryReentrySchedule> findByMode(StrategyMode mode) {
        ensureCache();
        return Optional.ofNullable(cache.get(mode));
    }

    /** Saves the one schedule for its mode, replacing whatever was there. */
    public synchronized void save(HistoryReentrySchedule schedule) {
        if (schedule == null) {
            return;
        }
        String sql = """
                INSERT INTO history_reentry_schedules
                    (id, mode, enabled, day_of_week, scan_time_et, cadence, group_filter, max_stocks, last_run_date, updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(mode) DO UPDATE SET
                    id = excluded.id, enabled = excluded.enabled, day_of_week = excluded.day_of_week,
                    scan_time_et = excluded.scan_time_et, cadence = excluded.cadence,
                    group_filter = excluded.group_filter, max_stocks = excluded.max_stocks,
                    last_run_date = excluded.last_run_date, updated_at = excluded.updated_at""";
        try (PreparedStatement ps = db.get().prepareStatement(sql)) {
            ps.setString(1, schedule.id());
            ps.setString(2, schedule.mode().name());
            ps.setInt(3, schedule.enabled() ? 1 : 0);
            ps.setString(4, schedule.day().name());
            ps.setString(5, schedule.scanTimeEt().toString());
            ps.setString(6, schedule.cadence().name());
            ps.setString(7, schedule.group().name());
            ps.setInt(8, schedule.maxStocks());
            ps.setString(9, schedule.lastRunDate() == null ? null : schedule.lastRunDate().toString());
            ps.setString(10, Instant.now().toString());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save the history re-entry schedule", ex);
        }
        ensureCache();
        cache.put(schedule.mode(), schedule);
    }

    public synchronized void deleteByMode(StrategyMode mode) {
        try (PreparedStatement ps = db.get().prepareStatement("DELETE FROM history_reentry_schedules WHERE mode=?")) {
            ps.setString(1, mode.name());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to delete the history re-entry schedule", ex);
        }
        ensureCache();
        cache.remove(mode);
    }

    private void ensureCache() {
        if (cacheValid) {
            return;
        }
        cache.clear();
        String sql = "SELECT id, mode, enabled, day_of_week, scan_time_et, cadence, group_filter, max_stocks,"
                + " last_run_date FROM history_reentry_schedules";
        try (PreparedStatement ps = db.get().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                StrategyMode mode = StrategyMode.valueOf(rs.getString("mode"));
                String lastRun = rs.getString("last_run_date");
                cache.put(mode, new HistoryReentrySchedule(
                        rs.getString("id"),
                        rs.getInt("enabled") == 1,
                        mode,
                        DayOfWeek.valueOf(rs.getString("day_of_week")),
                        LocalTime.parse(rs.getString("scan_time_et")),
                        HistoryReentrySchedule.Cadence.valueOf(rs.getString("cadence")),
                        HistoryReentrySchedule.Group.valueOf(rs.getString("group_filter")),
                        rs.getInt("max_stocks"),
                        lastRun == null || lastRun.isBlank() ? null : LocalDate.parse(lastRun)));
            }
            cacheValid = true;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to read history re-entry schedules", ex);
        }
    }
}
