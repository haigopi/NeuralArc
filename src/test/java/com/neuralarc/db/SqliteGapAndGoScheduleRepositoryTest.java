package com.neuralarc.db;

import com.neuralarc.gaprocket.GapRocketConfig;
import com.neuralarc.model.GapAndGoSchedule;
import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteGapAndGoScheduleRepositoryTest {
    @TempDir
    Path tempDir;

    private SqliteGapAndGoScheduleRepository repository() {
        return new SqliteGapAndGoScheduleRepository(AppDatabase.open(tempDir.resolve("neuralarc.db")));
    }

    private GapAndGoSchedule schedule(String id, String workspaceId, boolean executeAfterScan) {
        return new GapAndGoSchedule(id, true, LocalTime.of(9, 5), LocalTime.of(9, 45), LocalTime.of(11, 0),
                executeAfterScan, workspaceId, GapRocketConfig.defaults(StrategyMode.LIVE));
    }

    @Test
    void persistsAndReloadsFromDatabase() {
        SqliteGapAndGoScheduleRepository repo = repository();
        repo.save(schedule("s1", "w1", true));

        repo.invalidateCache(); // force reload from SQLite
        Optional<GapAndGoSchedule> loaded = repo.findById("s1");

        assertTrue(loaded.isPresent());
        assertTrue(loaded.get().executeAfterScan());
        assertEquals(LocalTime.of(9, 5), loaded.get().scanTimeEt());
        assertEquals("w1", loaded.get().workspaceId());
        assertEquals(StrategyMode.LIVE, loaded.get().config().mode());
    }

    @Test
    void findsByWorkspaceId() {
        SqliteGapAndGoScheduleRepository repo = repository();
        repo.save(schedule("s1", "w1", false));
        repo.save(schedule("s2", "w2", true));

        assertEquals("s2", repo.findByWorkspaceId("w2").orElseThrow().id());
        assertTrue(repo.findByWorkspaceId("missing").isEmpty());
    }

    @Test
    void updatesExistingScheduleInPlace() {
        SqliteGapAndGoScheduleRepository repo = repository();
        repo.save(schedule("s1", "w1", false));
        repo.save(schedule("s1", "w1", true)); // same id, executeAfterScan flipped

        assertEquals(1, repo.findAll().size());
        assertTrue(repo.findById("s1").orElseThrow().executeAfterScan());
    }

    @Test
    void deletesById() {
        SqliteGapAndGoScheduleRepository repo = repository();
        repo.save(schedule("s1", "w1", true));

        repo.deleteById("s1");

        assertTrue(repo.findAll().isEmpty());
        assertFalse(repo.findById("s1").isPresent());
    }
}
