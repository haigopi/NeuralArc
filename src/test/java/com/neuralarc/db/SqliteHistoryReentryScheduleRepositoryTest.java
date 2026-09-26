package com.neuralarc.db;

import com.neuralarc.model.HistoryReentrySchedule;
import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteHistoryReentryScheduleRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void aScheduleSurvivesARestart() {
        Path dbPath = tempDir.resolve("neuralarc.db");
        HistoryReentrySchedule saved = new HistoryReentrySchedule("s1", true, StrategyMode.LIVE, DayOfWeek.WEDNESDAY,
                LocalTime.of(10, 30), HistoryReentrySchedule.Cadence.BIWEEKLY, HistoryReentrySchedule.Group.LOSSES,
                7, LocalDate.of(2026, 9, 9));
        new SqliteHistoryReentryScheduleRepository(AppDatabase.open(dbPath)).save(saved);

        HistoryReentrySchedule loaded = new SqliteHistoryReentryScheduleRepository(AppDatabase.open(dbPath))
                .findByMode(StrategyMode.LIVE).orElseThrow();

        assertEquals(saved, loaded);
        assertEquals(LocalDate.of(2026, 9, 9), loaded.lastRunDate(), "a fortnightly cadence needs its last run date");
    }

    @Test
    void eachModeKeepsItsOwnScheduleAndSavingReplacesIt() {
        SqliteHistoryReentryScheduleRepository repository =
                new SqliteHistoryReentryScheduleRepository(AppDatabase.open(tempDir.resolve("modes.db")));
        repository.save(HistoryReentrySchedule.defaults(StrategyMode.PAPER));
        repository.save(new HistoryReentrySchedule(null, true, StrategyMode.LIVE, DayOfWeek.FRIDAY, LocalTime.of(11, 0),
                HistoryReentrySchedule.Cadence.WEEKLY, HistoryReentrySchedule.Group.ALL, 3, null));

        repository.save(HistoryReentrySchedule.defaults(StrategyMode.PAPER).withLastRunDate(LocalDate.of(2026, 9, 21)));

        assertEquals(LocalDate.of(2026, 9, 21), repository.findByMode(StrategyMode.PAPER).orElseThrow().lastRunDate());
        assertEquals(3, repository.findByMode(StrategyMode.LIVE).orElseThrow().maxStocks(), "live is untouched");
        repository.deleteByMode(StrategyMode.PAPER);
        assertTrue(repository.findByMode(StrategyMode.PAPER).isEmpty());
        assertTrue(repository.findByMode(StrategyMode.LIVE).isPresent());
    }
}
