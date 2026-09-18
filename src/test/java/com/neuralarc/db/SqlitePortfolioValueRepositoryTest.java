package com.neuralarc.db;

import com.neuralarc.model.PortfolioValueSample;
import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlitePortfolioValueRepositoryTest {
    private static final LocalDate DAY = LocalDate.of(2026, 9, 18);

    @TempDir
    Path tempDir;

    @Test
    void aDaysSeriesSurvivesARestartInTimeOrder() {
        Path file = tempDir.resolve("neuralarc.db");
        SqlitePortfolioValueRepository repository = new SqlitePortfolioValueRepository(AppDatabase.open(file));
        repository.save(StrategyMode.LIVE, DAY, sample("2026-09-18T14:31:00Z", "1010"));
        repository.save(StrategyMode.LIVE, DAY, sample("2026-09-18T14:30:00Z", "1000"));
        repository.save(StrategyMode.PAPER, DAY, sample("2026-09-18T14:30:00Z", "5000"));

        SqlitePortfolioValueRepository reopened = new SqlitePortfolioValueRepository(AppDatabase.open(file));

        List<PortfolioValueSample> live = reopened.findDay(StrategyMode.LIVE, DAY);
        assertEquals(List.of(new BigDecimal("1000"), new BigDecimal("1010")),
                live.stream().map(PortfolioValueSample::marketValue).toList());
        assertEquals(1, reopened.findDay(StrategyMode.PAPER, DAY).size(), "modes are separate series");
    }

    @Test
    void aLaterReadingOfTheSameMinuteReplacesTheEarlierOne() {
        SqlitePortfolioValueRepository repository = new SqlitePortfolioValueRepository(
                AppDatabase.open(tempDir.resolve("neuralarc.db")));
        repository.findDay(StrategyMode.LIVE, DAY);
        repository.save(StrategyMode.LIVE, DAY, sample("2026-09-18T14:30:00Z", "1000"));
        repository.save(StrategyMode.LIVE, DAY, sample("2026-09-18T14:30:00Z", "1005"));

        List<PortfolioValueSample> cached = repository.findDay(StrategyMode.LIVE, DAY);
        repository.invalidateCache();
        List<PortfolioValueSample> stored = repository.findDay(StrategyMode.LIVE, DAY);

        assertEquals(cached, stored, "cache and database agree");
        assertEquals(1, stored.size());
        assertEquals(new BigDecimal("1005"), stored.get(0).marketValue());
    }

    @Test
    void oldDaysArePruned() {
        SqlitePortfolioValueRepository repository = new SqlitePortfolioValueRepository(
                AppDatabase.open(tempDir.resolve("neuralarc.db")));
        repository.save(StrategyMode.LIVE, DAY.minusDays(40), sample("2026-08-09T14:30:00Z", "900"));
        repository.save(StrategyMode.LIVE, DAY, sample("2026-09-18T14:30:00Z", "1000"));

        assertEquals(1, repository.pruneBefore(DAY.minusDays(30)));

        assertTrue(repository.findDay(StrategyMode.LIVE, DAY.minusDays(40)).isEmpty());
        assertEquals(1, repository.findDay(StrategyMode.LIVE, DAY).size());
    }

    private static PortfolioValueSample sample(String minute, String value) {
        return new PortfolioValueSample(Instant.parse(minute), new BigDecimal(value), new BigDecimal("950"));
    }
}
