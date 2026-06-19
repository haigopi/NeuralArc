package com.neuralarc.db;

import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.SwingSchedule;
import com.neuralarc.swing.SwingConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SqliteSwingScheduleRepositoryTest {
    @TempDir
    Path tempDir;

    private SqliteSwingScheduleRepository repository() {
        return new SqliteSwingScheduleRepository(AppDatabase.open(tempDir.resolve("neuralarc.db")));
    }

    private SwingSchedule schedule(String id, String workspaceId, boolean executeAfterScan) {
        return new SwingSchedule(id, true, LocalTime.of(9, 45), LocalTime.of(9, 45), LocalTime.of(15, 45),
                executeAfterScan, workspaceId, SwingConfig.defaults(StrategyMode.LIVE));
    }

    @Test
    void persistsAndReloadsFromDatabase() {
        SqliteSwingScheduleRepository repo = repository();
        repo.save(schedule("s1", "w1", true));

        repo.invalidateCache(); // force reload from SQLite
        Optional<SwingSchedule> loaded = repo.findById("s1");

        assertTrue(loaded.isPresent());
        assertTrue(loaded.get().executeAfterScan());
        assertEquals(LocalTime.of(9, 45), loaded.get().scanTimeEt());
        assertEquals(LocalTime.of(15, 45), loaded.get().executionWindowEndEt());
        assertEquals("w1", loaded.get().workspaceId());
        assertEquals(StrategyMode.LIVE, loaded.get().config().mode());
    }

    @Test
    void persistsAcrossRepositoryInstancesForAppRestart() {
        Path dbPath = tempDir.resolve("neuralarc.db");
        SqliteSwingScheduleRepository first = new SqliteSwingScheduleRepository(AppDatabase.open(dbPath));
        first.save(schedule("s1", "w1", true));

        // A fresh repository on a fresh AppDatabase simulates an app restart.
        SqliteSwingScheduleRepository restarted = new SqliteSwingScheduleRepository(AppDatabase.open(dbPath));

        SwingSchedule loaded = restarted.findByWorkspaceId("w1").orElseThrow();
        assertEquals("s1", loaded.id());
        assertTrue(loaded.enabled());
        assertTrue(loaded.executeAfterScan());
        assertEquals(StrategyMode.LIVE, loaded.config().mode());
    }

    @Test
    void findsByWorkspaceId() {
        SqliteSwingScheduleRepository repo = repository();
        repo.save(schedule("s1", "w1", false));
        repo.save(schedule("s2", "w2", true));

        assertEquals("s2", repo.findByWorkspaceId("w2").orElseThrow().id());
        assertTrue(repo.findByWorkspaceId("missing").isEmpty());
    }

    @Test
    void updatesExistingScheduleInPlace() {
        SqliteSwingScheduleRepository repo = repository();
        repo.save(schedule("s1", "w1", false));
        repo.save(schedule("s1", "w1", true)); // same id, executeAfterScan flipped

        assertEquals(1, repo.findAll().size());
        assertTrue(repo.findById("s1").orElseThrow().executeAfterScan());
    }

    @Test
    void deletesById() {
        SqliteSwingScheduleRepository repo = repository();
        repo.save(schedule("s1", "w1", true));

        repo.deleteById("s1");

        assertTrue(repo.findAll().isEmpty());
        assertFalse(repo.findById("s1").isPresent());
    }
}
