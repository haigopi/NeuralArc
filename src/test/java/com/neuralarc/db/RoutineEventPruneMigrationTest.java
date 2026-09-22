package com.neuralarc.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoutineEventPruneMigrationTest {
    @TempDir
    Path tempDir;

    @Test
    void routinePollingEventsArePrunedAndEverythingElseIsKept() throws Exception {
        Path file = tempDir.resolve("neuralarc.db");
        AppDatabase first = AppDatabase.open(file);
        insert(first, "e1", "s1", "POLL_SUCCESS", "2026-09-18T13:00:00Z");
        insert(first, "e2", "s1", "POLL_SUCCESS", "2026-09-18T13:01:00Z");
        insert(first, "e3", "s1", "STOP_LOSS_ACTIVATED", "2026-09-18T13:00:00Z");
        insert(first, "e4", "s1", "STOP_LOSS_ACTIVATED", "2026-09-18T13:01:00Z");
        insert(first, "e5", "s1", "STOP_LOSS_ACTIVATED", "2026-09-19T13:00:00Z");
        insert(first, "e6", "s1", "ORDER_SUBMITTED", "2026-09-18T13:00:00Z");
        insert(first, "e7", "s1", "STRATEGY_FAILED", "2026-09-18T13:02:00Z");
        // Re-run the prune as an upgraded install would: forget that it already ran.
        try (Statement st = first.get().createStatement()) {
            st.executeUpdate("DELETE FROM schema_migrations WHERE version='023_prune_routine_events'");
        }
        first.get().close();

        AppDatabase upgraded = AppDatabase.open(file);

        assertEquals(0, count(upgraded, "POLL_SUCCESS"), "nothing reads poll heartbeats");
        assertEquals(2, count(upgraded, "STOP_LOSS_ACTIVATED"), "one activation per strategy per day is kept");
        assertEquals(1, count(upgraded, "ORDER_SUBMITTED"));
        assertEquals(1, count(upgraded, "STRATEGY_FAILED"));
    }

    private static void insert(AppDatabase db, String id, String strategyId, String type, String at) throws SQLException {
        try (PreparedStatement ps = db.get().prepareStatement(
                "INSERT INTO strategy_events (id, strategy_id, event_type, message, metadata_json, created_at) VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, id);
            ps.setString(2, strategyId);
            ps.setString(3, type);
            ps.setString(4, "");
            ps.setString(5, "{}");
            ps.setString(6, at);
            ps.executeUpdate();
        }
    }

    private static int count(AppDatabase db, String type) throws SQLException {
        try (PreparedStatement ps = db.get().prepareStatement("SELECT COUNT(*) FROM strategy_events WHERE event_type=?")) {
            ps.setString(1, type);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
