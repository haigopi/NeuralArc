package com.neuralarc.db;

import com.neuralarc.model.IntradayValueSample;
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

/**
 * SQLite-backed intraday account equity series, one row per mode per minute. A day's series is read
 * from the database once and then served from the in-memory cache, which every write keeps current,
 * so the chart can read it on the EDT without touching the database.
 */
public final class SqliteAccountEquityRepository {
    /** Days of history kept; older rows are pruned when a new day starts. */
    public static final int RETENTION_DAYS = 30;

    private final AppDatabase db;
    private final Map<String, List<IntradayValueSample>> cache = new HashMap<>();

    public SqliteAccountEquityRepository(AppDatabase db) {
        this.db = db;
    }

    public synchronized void invalidateCache() {
        cache.clear();
    }

    /** Stores the sample for its minute, replacing an earlier reading of the same minute. */
    public synchronized void save(StrategyMode mode, LocalDate sessionDate, IntradayValueSample sample) {
        try (PreparedStatement ps = db.get().prepareStatement("""
                INSERT INTO account_equity_samples (mode, session_date, minute_epoch, equity, last_equity)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(mode, minute_epoch) DO UPDATE SET
                    equity = excluded.equity,
                    last_equity = excluded.last_equity""")) {
            ps.setString(1, mode.name());
            ps.setString(2, sessionDate.toString());
            ps.setLong(3, sample.minute().getEpochSecond());
            ps.setString(4, sample.value().toPlainString());
            ps.setString(5, sample.baseline().toPlainString());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save account equity sample", ex);
        }
        List<IntradayValueSample> day = cache.get(key(mode, sessionDate));
        if (day != null) {
            day.removeIf(existing -> existing.minute().equals(sample.minute()));
            day.add(sample);
            day.sort((a, b) -> a.minute().compareTo(b.minute()));
        }
    }

    /** The day's samples in time order. */
    public synchronized List<IntradayValueSample> findDay(StrategyMode mode, LocalDate sessionDate) {
        return List.copyOf(cache.computeIfAbsent(key(mode, sessionDate), ignored -> load(mode, sessionDate)));
    }

    /** Deletes days before {@code oldestKept}; returns how many rows went. */
    public synchronized int pruneBefore(LocalDate oldestKept) {
        try (PreparedStatement ps = db.get().prepareStatement(
                "DELETE FROM account_equity_samples WHERE session_date < ?")) {
            ps.setString(1, oldestKept.toString());
            int removed = ps.executeUpdate();
            cache.keySet().removeIf(key -> key.substring(key.indexOf('|') + 1).compareTo(oldestKept.toString()) < 0);
            return removed;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to prune account equity samples", ex);
        }
    }

    private List<IntradayValueSample> load(StrategyMode mode, LocalDate sessionDate) {
        List<IntradayValueSample> samples = new ArrayList<>();
        try (PreparedStatement ps = db.get().prepareStatement("""
                SELECT minute_epoch, equity, last_equity FROM account_equity_samples
                WHERE mode = ? AND session_date = ? ORDER BY minute_epoch""")) {
            ps.setString(1, mode.name());
            ps.setString(2, sessionDate.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    samples.add(new IntradayValueSample(
                            Instant.ofEpochSecond(rs.getLong(1)),
                            new BigDecimal(rs.getString(2)),
                            new BigDecimal(rs.getString(3))));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load account equity samples", ex);
        }
        return samples;
    }

    private static String key(StrategyMode mode, LocalDate sessionDate) {
        return mode.name() + "|" + sessionDate;
    }
}
