package com.neuralarc.db;

import com.neuralarc.model.StrategyMode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Remembers symbols the operator deleted so the broker sync does not resurrect them.
 *
 * <p>Deleting a strategy only removes local rows; the position or open order usually still exists
 * at the broker, and {@code syncRemoteStrategies()} recreates a strategy for any broker symbol
 * without a local row. Scoped per mode so removing a paper strategy never hides the live one.
 */
public final class SqliteRemoteSyncSuppressionRepository {
    private static final Logger LOGGER = Logger.getLogger(SqliteRemoteSyncSuppressionRepository.class.getName());

    private final Connection connection;

    public SqliteRemoteSyncSuppressionRepository(AppDatabase database) {
        this.connection = database.get();
    }

    public void suppress(String symbol, StrategyMode mode) {
        String normalized = normalize(symbol);
        if (normalized.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO remote_sync_suppressions (symbol, mode)
                VALUES (?, ?)
                ON CONFLICT(symbol, mode) DO UPDATE SET suppressed_at = datetime('now')
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, normalized);
            ps.setString(2, modeKey(mode));
            ps.executeUpdate();
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Failed to record remote-sync suppression for " + normalized, ex);
        }
    }

    /** Called when the operator deliberately (re)creates a strategy for the symbol. */
    public void clear(String symbol, StrategyMode mode) {
        String normalized = normalize(symbol);
        if (normalized.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM remote_sync_suppressions WHERE symbol = ? AND mode = ?")) {
            ps.setString(1, normalized);
            ps.setString(2, modeKey(mode));
            ps.executeUpdate();
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Failed to clear remote-sync suppression for " + normalized, ex);
        }
    }

    public Set<String> suppressedSymbols(StrategyMode mode) {
        Set<String> symbols = new LinkedHashSet<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT symbol FROM remote_sync_suppressions WHERE mode = ?")) {
            ps.setString(1, modeKey(mode));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    symbols.add(rs.getString("symbol"));
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Failed to load remote-sync suppressions", ex);
        }
        return symbols;
    }

    private String normalize(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }

    private String modeKey(StrategyMode mode) {
        return (mode == null ? StrategyMode.PAPER : mode).name();
    }
}
