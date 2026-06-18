package com.neuralarc.db;

import com.neuralarc.diphunter.DipHunterConfig;
import com.neuralarc.model.DipHunterSchedule;
import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SqliteDipHunterScheduleRepositoryTest {
    @TempDir
    Path tempDir;

    private SqliteDipHunterScheduleRepository repository() {
        return new SqliteDipHunterScheduleRepository(AppDatabase.open(tempDir.resolve("neuralarc.db")));
    }

    private DipHunterSchedule schedule(String id, String workspaceId, boolean executeAfterScan) {
        return new DipHunterSchedule(id, true, LocalTime.of(10, 0), LocalTime.of(10, 0), LocalTime.of(15, 30),
                executeAfterScan, workspaceId, DipHunterConfig.defaults(StrategyMode.LIVE));
    }

    @Test
    void persistsAndReloadsFromDatabase() {
        SqliteDipHunterScheduleRepository repo = repository();
        repo.save(schedule("s1", "w1", true));

        repo.invalidateCache(); // force reload from SQLite
        Optional<DipHunterSchedule> loaded = repo.findById("s1");

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
        SqliteDipHunterScheduleRepository first = new SqliteDipHunterScheduleRepository(AppDatabase.open(dbPath));
        first.save(schedule("s1", "w1", true));

        SqliteDipHunterScheduleRepository restarted = new SqliteDipHunterScheduleRepository(AppDatabase.open(dbPath));

        DipHunterSchedule loaded = restarted.findByWorkspaceId("w1").orElseThrow();
        assertEquals("s1", loaded.id());
        assertTrue(loaded.enabled());
        assertTrue(loaded.executeAfterScan());
        assertEquals(StrategyMode.LIVE, loaded.config().mode());
    }

    @Test
    void findsByWorkspaceId() {
        SqliteDipHunterScheduleRepository repo = repository();
        repo.save(schedule("s1", "w1", false));
        repo.save(schedule("s2", "w2", true));

        assertEquals("s2", repo.findByWorkspaceId("w2").orElseThrow().id());
        assertTrue(repo.findByWorkspaceId("missing").isEmpty());
    }

    @Test
    void updatesExistingScheduleInPlace() {
        SqliteDipHunterScheduleRepository repo = repository();
        repo.save(schedule("s1", "w1", false));
        repo.save(schedule("s1", "w1", true)); // same id, executeAfterScan flipped

        assertEquals(1, repo.findAll().size());
        assertTrue(repo.findById("s1").orElseThrow().executeAfterScan());
    }

    @Test
    void deletesById() {
        SqliteDipHunterScheduleRepository repo = repository();
        repo.save(schedule("s1", "w1", true));

        repo.deleteById("s1");

        assertTrue(repo.findAll().isEmpty());
        assertFalse(repo.findById("s1").isPresent());
    }

    @Test
    void emptyRepositoryReturnsEmptyList() {
        assertTrue(repository().findAll().isEmpty());
    }
}
