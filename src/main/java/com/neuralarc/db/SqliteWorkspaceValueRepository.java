package com.neuralarc.db;

import com.neuralarc.model.StrategyMode;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SQLite-backed intraday value of each workspace's holdings, one row per mode, workspace and minute.
 * A day's series is read once and then served from the in-memory cache, which writes keep current, so
 * the chart can read it on the EDT without touching the database.
 */
public final class SqliteWorkspaceValueRepository {
    public static final int RETENTION_DAYS = 30;

    /** One minute's value. */
    public record Point(Instant minute, BigDecimal value) {
    }

    private final AppDatabase db;
    private final Map<String, List<Point>> days = new HashMap<>();
    private final Map<String, Optional<BigDecimal>> previousClose = new HashMap<>();

    public SqliteWorkspaceValueRepository(AppDatabase db) {
        this.db = db;
    }

    public synchronized void invalidateCache() {
        days.clear();
        previousClose.clear();
    }

    /** Stores the point for its minute, replacing an earlier reading of the same minute. */
    public synchronized void save(StrategyMode mode, String workspaceId, LocalDate sessionDate, Point point) {
        try (PreparedStatement ps = db.get().prepareStatement("""
                INSERT INTO workspace_value_samples (mode, workspace_id, session_date, minute_epoch, market_value)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(mode, workspace_id, minute_epoch) DO UPDATE SET market_value = excluded.market_value""")) {
            ps.setString(1, mode.name());
            ps.setString(2, workspaceId);
            ps.setString(3, sessionDate.toString());
            ps.setLong(4, point.minute().getEpochSecond());
            ps.setString(5, point.value().toPlainString());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save workspace value sample", ex);
        }
        List<Point> day = days.get(key(mode, workspaceId, sessionDate));
        if (day != null) {
            day.removeIf(existing -> existing.minute().equals(point.minute()));
            day.add(point);
            day.sort((a, b) -> a.minute().compareTo(b.minute()));
        }
    }

    /** The day's points in time order. */
    public synchronized List<Point> findDay(StrategyMode mode, String workspaceId, LocalDate sessionDate) {
        return List.copyOf(days.computeIfAbsent(key(mode, workspaceId, sessionDate),
                ignored -> load(mode, workspaceId, sessionDate)));
    }

    /** The last value recorded on an earlier session day: the workspace's "previous close". */
    public synchronized Optional<BigDecimal> previousClose(StrategyMode mode, String workspaceId, LocalDate sessionDate) {
        return previousClose.computeIfAbsent(key(mode, workspaceId, sessionDate), ignored -> {
            try (PreparedStatement ps = db.get().prepareStatement("""
                    SELECT market_value FROM workspace_value_samples
                    WHERE mode = ? AND workspace_id = ? AND session_date < ?
                    ORDER BY minute_epoch DESC LIMIT 1""")) {
                ps.setString(1, mode.name());
                ps.setString(2, workspaceId);
                ps.setString(3, sessionDate.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(new BigDecimal(rs.getString(1))) : Optional.empty();
                }
            } catch (SQLException ex) {
                throw new IllegalStateException("Failed to read the workspace's previous close", ex);
            }
        });
    }

    public synchronized int pruneBefore(LocalDate oldestKept) {
        try (PreparedStatement ps = db.get().prepareStatement(
                "DELETE FROM workspace_value_samples WHERE session_date < ?")) {
            ps.setString(1, oldestKept.toString());
            return ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to prune workspace value samples", ex);
        }
    }

    private List<Point> load(StrategyMode mode, String workspaceId, LocalDate sessionDate) {
        List<Point> points = new ArrayList<>();
        try (PreparedStatement ps = db.get().prepareStatement("""
                SELECT minute_epoch, market_value FROM workspace_value_samples
                WHERE mode = ? AND workspace_id = ? AND session_date = ? ORDER BY minute_epoch""")) {
            ps.setString(1, mode.name());
            ps.setString(2, workspaceId);
            ps.setString(3, sessionDate.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    points.add(new Point(Instant.ofEpochSecond(rs.getLong(1)), new BigDecimal(rs.getString(2))));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load workspace value samples", ex);
        }
        return points;
    }

    private static String key(StrategyMode mode, String workspaceId, LocalDate sessionDate) {
        return mode.name() + "|" + workspaceId + "|" + sessionDate;
    }
}
