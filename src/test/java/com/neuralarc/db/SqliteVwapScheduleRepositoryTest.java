package com.neuralarc.db;

import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.VwapSchedule;
import com.neuralarc.vwap.VwapConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SqliteVwapScheduleRepositoryTest {
    @TempDir
    Path tempDir;

    private SqliteVwapScheduleRepository repository() {
        return new SqliteVwapScheduleRepository(AppDatabase.open(tempDir.resolve("neuralarc.db")));
    }

    private VwapSchedule schedule(String id, String workspaceId, boolean executeAfterScan) {
        return new VwapSchedule(id, true, LocalTime.of(10, 0), LocalTime.of(10, 0), LocalTime.of(15, 30),
                executeAfterScan, workspaceId, VwapConfig.defaults(StrategyMode.LIVE));
    }

    @Test
    void persistsAndReloadsFromDatabase() {
        SqliteVwapScheduleRepository repo = repository();
        repo.save(schedule("s1", "w1", true));

        repo.invalidateCache(); // force reload from SQLite
        Optional<VwapSchedule> loaded = repo.findById("s1");

        assertTrue(loaded.isPresent());
        assertTrue(loaded.get().executeAfterScan());
        assertEquals(LocalTime.of(10, 0), loaded.get().scanTimeEt());
        assertEquals(LocalTime.of(15, 30), loaded.get().executionWindowEndEt());
        assertEquals("w1", loaded.get().workspaceId());
        assertEquals(StrategyMode.LIVE, loaded.get().config().mode());
    }

    @Test
    void persistsAcrossRepositoryInstancesForAppRestart() {
        Path dbPath = tempDir.resolve("neuralarc.db");
        SqliteVwapScheduleRepository first = new SqliteVwapScheduleRepository(AppDatabase.open(dbPath));
        first.save(schedule("s1", "w1", true));

        // A fresh repository on a fresh AppDatabase simulates an app restart.
        SqliteVwapScheduleRepository restarted = new SqliteVwapScheduleRepository(AppDatabase.open(dbPath));

        VwapSchedule loaded = restarted.findByWorkspaceId("w1").orElseThrow();
        assertEquals("s1", loaded.id());
        assertTrue(loaded.enabled());
        assertTrue(loaded.executeAfterScan());
        assertEquals(StrategyMode.LIVE, loaded.config().mode());
    }

    @Test
    void findsByWorkspaceId() {
        SqliteVwapScheduleRepository repo = repository();
        repo.save(schedule("s1", "w1", false));
        repo.save(schedule("s2", "w2", true));

        assertEquals("s2", repo.findByWorkspaceId("w2").orElseThrow().id());
        assertTrue(repo.findByWorkspaceId("missing").isEmpty());
    }

    @Test
    void updatesExistingScheduleInPlace() {
        SqliteVwapScheduleRepository repo = repository();
        repo.save(schedule("s1", "w1", false));
        repo.save(schedule("s1", "w1", true)); // same id, executeAfterScan flipped

        assertEquals(1, repo.findAll().size());
        assertTrue(repo.findById("s1").orElseThrow().executeAfterScan());
    }

    @Test
    void deletesById() {
        SqliteVwapScheduleRepository repo = repository();
        repo.save(schedule("s1", "w1", true));

        repo.deleteById("s1");

        assertTrue(repo.findAll().isEmpty());
        assertFalse(repo.findById("s1").isPresent());
    }
}
