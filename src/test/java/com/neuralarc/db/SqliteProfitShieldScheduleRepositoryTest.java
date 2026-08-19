package com.neuralarc.db;

import com.neuralarc.model.ProfitShieldSchedule;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.profitshield.ProfitShieldConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteProfitShieldScheduleRepositoryTest {
    @TempDir
    Path tempDir;

    private SqliteProfitShieldScheduleRepository repository() {
        return new SqliteProfitShieldScheduleRepository(AppDatabase.open(tempDir.resolve("neuralarc.db")));
    }

    private ProfitShieldSchedule schedule(String id, String workspaceId, boolean executeAfterScan) {
        return new ProfitShieldSchedule(id, true, LocalTime.of(9, 45), LocalTime.of(9, 45), LocalTime.of(15, 45),
                executeAfterScan, workspaceId, ProfitShieldConfig.defaults(StrategyMode.LIVE));
    }

    @Test
    void persistsAndReloadsFromDatabase() {
        SqliteProfitShieldScheduleRepository repo = repository();
        repo.save(schedule("s1", "w1", true));

        repo.invalidateCache(); // force reload from SQLite
        Optional<ProfitShieldSchedule> loaded = repo.findById("s1");

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
        SqliteProfitShieldScheduleRepository first = new SqliteProfitShieldScheduleRepository(AppDatabase.open(dbPath));
        first.save(schedule("s1", "w1", true));

        // A fresh repository on a fresh AppDatabase simulates an app restart.
        SqliteProfitShieldScheduleRepository restarted = new SqliteProfitShieldScheduleRepository(AppDatabase.open(dbPath));

        ProfitShieldSchedule loaded = restarted.findByWorkspaceId("w1").orElseThrow();
        assertEquals("s1", loaded.id());
        assertTrue(loaded.enabled());
        assertTrue(loaded.executeAfterScan());
        assertEquals(StrategyMode.LIVE, loaded.config().mode());
    }

    @Test
    void roundTripsTheFullConfigThroughTheDatabase() {
        SqliteProfitShieldScheduleRepository repo = repository();
        ProfitShieldConfig config = new ProfitShieldConfig(90, new BigDecimal("2.5"), new BigDecimal("15"),
                new BigDecimal("8"), 750_000L, new BigDecimal("20"), new BigDecimal("500"),
                ProfitShieldConfig.TrendFilter.ABOVE_MA_50, new BigDecimal("0.5"), new BigDecimal("2"),
                new BigDecimal("4"), 6, StrategyMode.PAPER, List.of("MSFT"));
        repo.save(new ProfitShieldSchedule("s1", true, LocalTime.of(9, 45), LocalTime.of(9, 45),
                LocalTime.of(15, 45), false, "w1", config));

        repo.invalidateCache();
        ProfitShieldConfig loaded = repo.findById("s1").orElseThrow().config();

        assertEquals(90, loaded.drawdownLookbackSessions());
        assertEquals(new BigDecimal("2.5"), loaded.maximumDailyVolatilityPercent());
        assertEquals(new BigDecimal("15"), loaded.maximumDrawdownPercent());
        assertEquals(new BigDecimal("8"), loaded.maximumDistanceFromHighPercent());
        assertEquals(ProfitShieldConfig.TrendFilter.ABOVE_MA_50, loaded.trendFilter());
        assertEquals(new BigDecimal("2"), loaded.protectiveStopPercent());
        assertEquals(List.of("MSFT"), loaded.candidateSymbols());
    }

    @Test
    void findsByWorkspaceId() {
        SqliteProfitShieldScheduleRepository repo = repository();
        repo.save(schedule("s1", "w1", false));
        repo.save(schedule("s2", "w2", true));

        assertEquals("s2", repo.findByWorkspaceId("w2").orElseThrow().id());
        assertTrue(repo.findByWorkspaceId("missing").isEmpty());
    }

    @Test
    void updatesExistingScheduleInPlace() {
        SqliteProfitShieldScheduleRepository repo = repository();
        repo.save(schedule("s1", "w1", false));
        repo.save(schedule("s1", "w1", true)); // same id, executeAfterScan flipped

        assertEquals(1, repo.findAll().size());
        assertTrue(repo.findById("s1").orElseThrow().executeAfterScan());
    }

    @Test
    void deletesById() {
        SqliteProfitShieldScheduleRepository repo = repository();
        repo.save(schedule("s1", "w1", true));

        repo.deleteById("s1");

        assertTrue(repo.findAll().isEmpty());
        assertFalse(repo.findById("s1").isPresent());
    }
}
