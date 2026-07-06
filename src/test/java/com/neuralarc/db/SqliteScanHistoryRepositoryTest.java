package com.neuralarc.db;

import com.neuralarc.model.ScanHistoryEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SqliteScanHistoryRepositoryTest {
    @TempDir
    Path tempDir;

    private static final Instant BASE = Instant.parse("2026-07-06T14:00:00Z");

    private SqliteScanHistoryRepository repository() {
        return new SqliteScanHistoryRepository(AppDatabase.open(tempDir.resolve("neuralarc.db")));
    }

    private ScanHistoryEntry entry(String id, String workspaceId, int minutesAfterBase, String summary) {
        return new ScanHistoryEntry(id, workspaceId, BASE.plus(minutesAfterBase, ChronoUnit.MINUTES),
                ScanHistoryEntry.TRIGGER_MANUAL, summary);
    }

    @Test
    void returnsMostRecentFirstScopedToWorkspace() {
        SqliteScanHistoryRepository repo = repository();
        repo.save(entry("a", "w1", 0, "Added 1"));
        repo.save(entry("b", "w1", 5, "No qualifying candidates"));
        repo.save(entry("c", "w2", 3, "Added 2"));

        List<ScanHistoryEntry> recent = repo.findRecentByWorkspace("w1", 10);

        assertEquals(2, recent.size());
        assertEquals("b", recent.get(0).id());
        assertEquals("a", recent.get(1).id());
        assertEquals(1, repo.findRecentByWorkspace("w2", 10).size());
        assertTrue(repo.findRecentByWorkspace("missing", 10).isEmpty());
    }

    @Test
    void honoursLimit() {
        SqliteScanHistoryRepository repo = repository();
        for (int i = 0; i < 5; i++) {
            repo.save(entry("id" + i, "w1", i, "Added " + i));
        }
        assertEquals(2, repo.findRecentByWorkspace("w1", 2).size());
        assertTrue(repo.findRecentByWorkspace("w1", 0).isEmpty());
    }

    @Test
    void prunesToRetentionWindowPerWorkspace() {
        SqliteScanHistoryRepository repo = repository();
        int total = SqliteScanHistoryRepository.MAX_ENTRIES_PER_WORKSPACE + 6;
        for (int i = 0; i < total; i++) {
            repo.save(entry("id" + i, "w1", i, "Scan " + i));
        }

        repo.invalidateCache(); // force reload from SQLite to confirm pruning hit the DB
        List<ScanHistoryEntry> all = repo.findRecentByWorkspace("w1", total);

        assertEquals(SqliteScanHistoryRepository.MAX_ENTRIES_PER_WORKSPACE, all.size());
        // Newest survives, oldest pruned.
        assertEquals("id" + (total - 1), all.get(0).id());
        assertTrue(all.stream().noneMatch(e -> e.id().equals("id0")));
    }

    @Test
    void persistsAcrossRepositoryInstancesForAppRestart() {
        Path dbPath = tempDir.resolve("neuralarc.db");
        SqliteScanHistoryRepository first = new SqliteScanHistoryRepository(AppDatabase.open(dbPath));
        first.save(entry("a", "w1", 0, "Added 3"));

        SqliteScanHistoryRepository restarted = new SqliteScanHistoryRepository(AppDatabase.open(dbPath));
        List<ScanHistoryEntry> recent = restarted.findRecentByWorkspace("w1", 10);

        assertEquals(1, recent.size());
        assertEquals("Added 3", recent.get(0).summary());
        assertEquals(ScanHistoryEntry.TRIGGER_MANUAL, recent.get(0).trigger());
    }
}
