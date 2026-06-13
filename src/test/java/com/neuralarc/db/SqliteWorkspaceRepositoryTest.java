package com.neuralarc.db;

import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyWorkspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteWorkspaceRepositoryTest {
    @TempDir
    Path tempDir;

    private SqliteWorkspaceRepository repository() {
        return new SqliteWorkspaceRepository(AppDatabase.open(tempDir.resolve("neuralarc.db")));
    }

    private StrategyWorkspace workspace(String id, String name, String code, StrategyMode mode, boolean archived) {
        return new StrategyWorkspace(id, name, code, mode, archived, null, null);
    }

    @Test
    void filtersByModeAndArchiveState() {
        SqliteWorkspaceRepository repo = repository();
        repo.save(workspace("w1", "ORB Engine", "ORB", StrategyMode.PAPER, false));
        repo.save(workspace("w2", "VWAP Desk", "VWAP", StrategyMode.LIVE, false));
        repo.save(workspace("w3", "Retired", "OLD", StrategyMode.PAPER, true));

        assertEquals(3, repo.findAll().size());
        assertEquals(2, repo.findByMode(StrategyMode.PAPER).size()); // ORB + Retired
        assertEquals(1, repo.findActive(StrategyMode.PAPER).size()); // ORB only (archived hidden)
        assertEquals(1, repo.findByMode(StrategyMode.LIVE).size());
        assertEquals("ORB", repo.findById("w1").orElseThrow().code());
    }

    @Test
    void persistsToDatabaseNotJustCache() {
        SqliteWorkspaceRepository repo = repository();
        repo.save(workspace("w1", "ORB Engine", "ORB", StrategyMode.PAPER, false));

        repo.invalidateCache(); // forces a reload from SQLite on next read
        StrategyWorkspace loaded = repo.findById("w1").orElseThrow();
        assertEquals("ORB Engine", loaded.name());
        assertEquals(StrategyMode.PAPER, loaded.mode());
    }

    @Test
    void renameAndArchiveRoundTrip() {
        SqliteWorkspaceRepository repo = repository();
        StrategyWorkspace w = workspace("w1", "Old Name", "OLD", StrategyMode.PAPER, false);
        repo.save(w);

        repo.save(w.withName("New Name").withArchived(true));
        repo.invalidateCache();

        StrategyWorkspace loaded = repo.findById("w1").orElseThrow();
        assertEquals("New Name", loaded.name());
        assertTrue(loaded.archived());
    }

    @Test
    void deleteRemovesWorkspace() {
        SqliteWorkspaceRepository repo = repository();
        repo.save(workspace("w1", "ORB", "ORB", StrategyMode.PAPER, false));
        repo.deleteById("w1");
        repo.invalidateCache();
        assertTrue(repo.findById("w1").isEmpty());
    }
}
