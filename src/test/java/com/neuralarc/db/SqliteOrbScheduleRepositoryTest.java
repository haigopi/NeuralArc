package com.neuralarc.db;

import com.neuralarc.model.OrbSchedule;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.orb.OrbConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SqliteOrbScheduleRepositoryTest {
    @TempDir
    Path tempDir;

    private SqliteOrbScheduleRepository repository() {
        return new SqliteOrbScheduleRepository(AppDatabase.open(tempDir.resolve("neuralarc.db")));
    }

    private OrbSchedule schedule(String id, String workspaceId, boolean executeAfterRangeClose) {
        return new OrbSchedule(id, true, LocalTime.of(9, 45), LocalTime.of(11, 0),
                executeAfterRangeClose, workspaceId, OrbConfig.defaults(StrategyMode.LIVE));
    }

    @Test
    void persistsAndReloadsFromDatabase() {
        SqliteOrbScheduleRepository repo = repository();
        repo.save(schedule("s1", "w1", true));

        repo.invalidateCache(); // force reload from SQLite
        Optional<OrbSchedule> loaded = repo.findById("s1");

        assertTrue(loaded.isPresent());
        assertTrue(loaded.get().executeAfterRangeClose());
        assertEquals(LocalTime.of(9, 45), loaded.get().rangeAnalysisTimeEt());
        assertEquals(LocalTime.of(11, 0), loaded.get().executionWindowEndEt());
        assertEquals("w1", loaded.get().workspaceId());
        assertEquals(StrategyMode.LIVE, loaded.get().config().mode());
    }

    @Test
    void persistsAcrossRepositoryInstancesForAppRestart() {
        Path dbPath = tempDir.resolve("neuralarc.db");
        SqliteOrbScheduleRepository first = new SqliteOrbScheduleRepository(AppDatabase.open(dbPath));
        first.save(schedule("s1", "w1", true));

        SqliteOrbScheduleRepository restarted = new SqliteOrbScheduleRepository(AppDatabase.open(dbPath));

        OrbSchedule loaded = restarted.findByWorkspaceId("w1").orElseThrow();
        assertEquals("s1", loaded.id());
        assertTrue(loaded.enabled());
        assertTrue(loaded.executeAfterRangeClose());
        assertEquals(StrategyMode.LIVE, loaded.config().mode());
    }

    @Test
    void findsByWorkspaceId() {
        SqliteOrbScheduleRepository repo = repository();
        repo.save(schedule("s1", "w1", false));
        repo.save(schedule("s2", "w2", true));

        assertEquals("s2", repo.findByWorkspaceId("w2").orElseThrow().id());
        assertTrue(repo.findByWorkspaceId("missing").isEmpty());
    }

    @Test
    void updatesExistingScheduleInPlace() {
        SqliteOrbScheduleRepository repo = repository();
        repo.save(schedule("s1", "w1", false));
        repo.save(schedule("s1", "w1", true)); // same id, executeAfterRangeClose flipped

        assertEquals(1, repo.findAll().size());
        assertTrue(repo.findById("s1").orElseThrow().executeAfterRangeClose());
    }

    @Test
    void deletesById() {
        SqliteOrbScheduleRepository repo = repository();
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
