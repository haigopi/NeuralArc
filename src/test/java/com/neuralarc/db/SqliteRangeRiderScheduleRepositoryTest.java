package com.neuralarc.db;

import com.neuralarc.model.RangeRiderSchedule;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.rangerider.RangeRiderConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SqliteRangeRiderScheduleRepositoryTest {
    @TempDir
    Path tempDir;

    private SqliteRangeRiderScheduleRepository repository() {
        return new SqliteRangeRiderScheduleRepository(AppDatabase.open(tempDir.resolve("neuralarc.db")));
    }

    private RangeRiderSchedule schedule(String id, String workspaceId, boolean executeAfterScan) {
        return new RangeRiderSchedule(id, true, LocalTime.of(9, 45), LocalTime.of(9, 45), LocalTime.of(15, 30),
                executeAfterScan, workspaceId, RangeRiderConfig.defaults(StrategyMode.LIVE));
    }

    @Test
    void persistsAndReloadsFromDatabase() {
        SqliteRangeRiderScheduleRepository repo = repository();
        repo.save(schedule("s1", "w1", true));

        repo.invalidateCache(); // force reload from SQLite
        Optional<RangeRiderSchedule> loaded = repo.findById("s1");

        assertTrue(loaded.isPresent());
        assertTrue(loaded.get().executeAfterScan());
        assertEquals(LocalTime.of(9, 45), loaded.get().scanTimeEt());
        assertEquals(LocalTime.of(15, 30), loaded.get().executionWindowEndEt());
        assertEquals("w1", loaded.get().workspaceId());
        assertEquals(StrategyMode.LIVE, loaded.get().config().mode());
    }

    @Test
    void persistsAcrossRepositoryInstancesForAppRestart() {
        Path dbPath = tempDir.resolve("neuralarc.db");
        SqliteRangeRiderScheduleRepository first = new SqliteRangeRiderScheduleRepository(AppDatabase.open(dbPath));
        first.save(schedule("s1", "w1", true));

        // A fresh repository on a fresh AppDatabase simulates an app restart.
        SqliteRangeRiderScheduleRepository restarted = new SqliteRangeRiderScheduleRepository(AppDatabase.open(dbPath));

        RangeRiderSchedule loaded = restarted.findByWorkspaceId("w1").orElseThrow();
        assertEquals("s1", loaded.id());
        assertTrue(loaded.enabled());
        assertTrue(loaded.executeAfterScan());
        assertEquals(StrategyMode.LIVE, loaded.config().mode());
    }

    @Test
    void roundTripsTheFullConfigThroughTheDatabase() {
        SqliteRangeRiderScheduleRepository repo = repository();
        RangeRiderConfig config = new RangeRiderConfig(20, new BigDecimal("3"), new BigDecimal("9"),
                new BigDecimal("70"), 3_000_000L, new BigDecimal("25"), new BigDecimal("500"),
                new BigDecimal("0.4"), new BigDecimal("0.6"), new BigDecimal("1.5"), 5,
                RangeRiderConfig.ExecutionFrequency.EVERY_15_MINUTES, StrategyMode.PAPER, List.of("AAPL"));
        repo.save(new RangeRiderSchedule("s1", true, LocalTime.of(9, 45), LocalTime.of(9, 45),
                LocalTime.of(15, 30), false, "w1", config));

        repo.invalidateCache();
        RangeRiderConfig loaded = repo.findById("s1").orElseThrow().config();

        assertEquals(20, loaded.lookbackSessions());
        assertEquals(new BigDecimal("70"), loaded.minimumSameDayFillRatePercent());
        assertEquals(new BigDecimal("0.4"), loaded.entryBufferPercent());
        assertEquals(new BigDecimal("0.6"), loaded.exitBufferPercent());
        assertEquals(RangeRiderConfig.ExecutionFrequency.EVERY_15_MINUTES, loaded.executionFrequency());
        assertEquals(List.of("AAPL"), loaded.candidateSymbols());
    }

    @Test
    void findsByWorkspaceId() {
        SqliteRangeRiderScheduleRepository repo = repository();
        repo.save(schedule("s1", "w1", false));
        repo.save(schedule("s2", "w2", true));

        assertEquals("s2", repo.findByWorkspaceId("w2").orElseThrow().id());
        assertTrue(repo.findByWorkspaceId("missing").isEmpty());
    }

    @Test
    void updatesExistingScheduleInPlace() {
        SqliteRangeRiderScheduleRepository repo = repository();
        repo.save(schedule("s1", "w1", false));
        repo.save(schedule("s1", "w1", true)); // same id, executeAfterScan flipped

        assertEquals(1, repo.findAll().size());
        assertTrue(repo.findById("s1").orElseThrow().executeAfterScan());
    }

    @Test
    void deletesById() {
        SqliteRangeRiderScheduleRepository repo = repository();
        repo.save(schedule("s1", "w1", true));

        repo.deleteById("s1");

        assertTrue(repo.findAll().isEmpty());
        assertFalse(repo.findById("s1").isPresent());
    }
}
