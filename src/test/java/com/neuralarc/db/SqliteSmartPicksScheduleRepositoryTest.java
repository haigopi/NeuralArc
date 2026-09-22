package com.neuralarc.db;

import com.neuralarc.model.RecommendationType;
import com.neuralarc.model.SmartPicksSchedule;
import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteSmartPicksScheduleRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void aScheduleSurvivesARestartWithEveryField() {
        Path file = tempDir.resolve("neuralarc.db");
        SmartPicksSchedule saved = new SmartPicksSchedule("s1", true, "w1", "rebound", LocalTime.of(15, 30),
                EnumSet.of(DayOfWeek.FRIDAY), true, 5, RecommendationType.LONG_TERM, StrategyMode.LIVE);
        new SqliteSmartPicksScheduleRepository(AppDatabase.open(file)).save(saved);

        SmartPicksSchedule loaded = new SqliteSmartPicksScheduleRepository(AppDatabase.open(file))
                .findByWorkspaceId("w1").orElseThrow();

        assertEquals(saved, loaded);
        assertEquals("REBOUND", loaded.workspaceCode(), "codes are normalized");
        assertEquals("Fri 15:30 ET", loaded.summary());
    }

    @Test
    void deletingRemovesItFromTheCacheAndTheDatabase() {
        Path file = tempDir.resolve("neuralarc.db");
        SqliteSmartPicksScheduleRepository repository = new SqliteSmartPicksScheduleRepository(AppDatabase.open(file));
        repository.save(new SmartPicksSchedule("s1", true, "w1", "MOVERS", LocalTime.of(10, 0), null, false, 1, null, null));

        repository.deleteById("s1");

        assertTrue(repository.findAll().isEmpty());
        assertTrue(new SqliteSmartPicksScheduleRepository(AppDatabase.open(file)).findAll().isEmpty());
    }
}
