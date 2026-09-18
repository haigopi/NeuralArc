package com.neuralarc.db;

import com.neuralarc.model.PortfolioValueSample;
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
 * SQLite-backed intraday portfolio value series, one row per mode per minute. A day's series is read
 * from the database once and then served from the in-memory cache, which every write keeps current,
 * so the chart can read it on the EDT without touching the database.
 */
public final class SqlitePortfolioValueRepository {
    /** Days of history kept; older rows are pruned when a new day starts. */
    public static final int RETENTION_DAYS = 30;

    private final AppDatabase db;
    private final Map<String, List<PortfolioValueSample>> cache = new HashMap<>();

    public SqlitePortfolioValueRepository(AppDatabase db) {
        this.db = db;
    }

    public synchronized void invalidateCache() {
        cache.clear();
    }

    /** Stores the sample for its minute, replacing an earlier reading of the same minute. */
    public synchronized void save(StrategyMode mode, LocalDate sessionDate, PortfolioValueSample sample) {
        try (PreparedStatement ps = db.get().prepareStatement("""
                INSERT INTO portfolio_value_samples (mode, session_date, minute_epoch, market_value, invested_value)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(mode, minute_epoch) DO UPDATE SET
                    market_value = excluded.market_value,
                    invested_value = excluded.invested_value""")) {
            ps.setString(1, mode.name());
            ps.setString(2, sessionDate.toString());
            ps.setLong(3, sample.minute().getEpochSecond());
            ps.setString(4, sample.marketValue().toPlainString());
            ps.setString(5, sample.investedValue().toPlainString());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save portfolio value sample", ex);
        }
        List<PortfolioValueSample> day = cache.get(key(mode, sessionDate));
        if (day != null) {
            day.removeIf(existing -> existing.minute().equals(sample.minute()));
            day.add(sample);
            day.sort((a, b) -> a.minute().compareTo(b.minute()));
        }
    }

    /** The day's samples in time order. */
    public synchronized List<PortfolioValueSample> findDay(StrategyMode mode, LocalDate sessionDate) {
        return List.copyOf(cache.computeIfAbsent(key(mode, sessionDate), ignored -> load(mode, sessionDate)));
    }

    /** Deletes days before {@code oldestKept}; returns how many rows went. */
    public synchronized int pruneBefore(LocalDate oldestKept) {
        try (PreparedStatement ps = db.get().prepareStatement(
                "DELETE FROM portfolio_value_samples WHERE session_date < ?")) {
            ps.setString(1, oldestKept.toString());
            int removed = ps.executeUpdate();
            cache.keySet().removeIf(key -> key.substring(key.indexOf('|') + 1).compareTo(oldestKept.toString()) < 0);
            return removed;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to prune portfolio value samples", ex);
        }
    }

    private List<PortfolioValueSample> load(StrategyMode mode, LocalDate sessionDate) {
        List<PortfolioValueSample> samples = new ArrayList<>();
        try (PreparedStatement ps = db.get().prepareStatement("""
                SELECT minute_epoch, market_value, invested_value FROM portfolio_value_samples
                WHERE mode = ? AND session_date = ? ORDER BY minute_epoch""")) {
            ps.setString(1, mode.name());
            ps.setString(2, sessionDate.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    samples.add(new PortfolioValueSample(
                            Instant.ofEpochSecond(rs.getLong(1)),
                            new BigDecimal(rs.getString(2)),
                            new BigDecimal(rs.getString(3))));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load portfolio value samples", ex);
        }
        return samples;
    }

    private static String key(StrategyMode mode, LocalDate sessionDate) {
        return mode.name() + "|" + sessionDate;
    }
}
