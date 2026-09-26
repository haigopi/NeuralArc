package com.neuralarc.db;

import com.neuralarc.model.AgentToolCall;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The audit trail of AI agent tool calls.
 *
 * <p>Write-mostly and off the hot path: runs are occasional and the rows are read only when
 * someone asks what an agent did, so this keeps no cache. Each run is capped at
 * {@link #MAX_CALLS_PER_RUN} rows, which a tool-call budget should already prevent — the cap is
 * here so a bug in the loop cannot grow the database without bound.
 */
public final class SqliteAgentToolCallRepository {
    public static final int MAX_CALLS_PER_RUN = 500;

    private final AppDatabase db;

    public SqliteAgentToolCallRepository(AppDatabase db) {
        this.db = db;
    }

    public void save(AgentToolCall call) {
        if (call == null) {
            return;
        }
        String sql = "INSERT INTO agent_tool_calls (id, run_id, called_at, tool, ok, arguments, error, elapsed_ms)"
                + " VALUES (?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = db.get().prepareStatement(sql)) {
            ps.setString(1, call.id());
            ps.setString(2, call.runId());
            ps.setString(3, call.calledAt().toString());
            ps.setString(4, call.tool());
            ps.setInt(5, call.ok() ? 1 : 0);
            ps.setString(6, call.argumentsJson());
            ps.setString(7, call.error());
            ps.setLong(8, call.elapsedMillis());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to record agent tool call " + call.tool(), ex);
        }
        pruneRun(call.runId());
    }

    /** Every call in one run, oldest first — the order the agent made them. */
    public List<AgentToolCall> findByRun(String runId) {
        String sql = "SELECT id, run_id, called_at, tool, ok, arguments, error, elapsed_ms"
                + " FROM agent_tool_calls WHERE run_id=? ORDER BY called_at, rowid";
        List<AgentToolCall> calls = new ArrayList<>();
        try (PreparedStatement ps = db.get().prepareStatement(sql)) {
            ps.setString(1, runId == null ? "" : runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    calls.add(new AgentToolCall(
                            rs.getString("id"),
                            rs.getString("run_id"),
                            Instant.parse(rs.getString("called_at")),
                            rs.getString("tool"),
                            rs.getInt("ok") == 1,
                            rs.getString("arguments"),
                            rs.getString("error"),
                            rs.getLong("elapsed_ms")));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to read agent tool calls for run " + runId, ex);
        }
        return calls;
    }

    private void pruneRun(String runId) {
        String sql = "DELETE FROM agent_tool_calls WHERE run_id=? AND rowid NOT IN"
                + " (SELECT rowid FROM agent_tool_calls WHERE run_id=? ORDER BY rowid DESC LIMIT ?)";
        try (PreparedStatement ps = db.get().prepareStatement(sql)) {
            ps.setString(1, runId);
            ps.setString(2, runId);
            ps.setInt(3, MAX_CALLS_PER_RUN);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to prune agent tool calls for run " + runId, ex);
        }
    }
}
