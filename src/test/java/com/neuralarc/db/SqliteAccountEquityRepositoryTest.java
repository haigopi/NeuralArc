package com.neuralarc.db;

import com.neuralarc.model.IntradayValueSample;
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

class SqliteAccountEquityRepositoryTest {
    private static final LocalDate DAY = LocalDate.of(2026, 9, 18);

    @TempDir
    Path tempDir;

    @Test
    void aDaysSeriesSurvivesARestartInTimeOrder() {
        Path file = tempDir.resolve("neuralarc.db");
        SqliteAccountEquityRepository repository = new SqliteAccountEquityRepository(AppDatabase.open(file));
        repository.save(StrategyMode.LIVE, DAY, sample("2026-09-18T14:31:00Z", "1010"));
        repository.save(StrategyMode.LIVE, DAY, sample("2026-09-18T14:30:00Z", "1000"));
        repository.save(StrategyMode.PAPER, DAY, sample("2026-09-18T14:30:00Z", "5000"));

        SqliteAccountEquityRepository reopened = new SqliteAccountEquityRepository(AppDatabase.open(file));

        List<IntradayValueSample> live = reopened.findDay(StrategyMode.LIVE, DAY);
        assertEquals(List.of(new BigDecimal("1000"), new BigDecimal("1010")),
                live.stream().map(IntradayValueSample::value).toList());
        assertEquals(1, reopened.findDay(StrategyMode.PAPER, DAY).size(), "modes are separate series");
    }

    @Test
    void aLaterReadingOfTheSameMinuteReplacesTheEarlierOne() {
        SqliteAccountEquityRepository repository = new SqliteAccountEquityRepository(
                AppDatabase.open(tempDir.resolve("neuralarc.db")));
        repository.findDay(StrategyMode.LIVE, DAY);
        repository.save(StrategyMode.LIVE, DAY, sample("2026-09-18T14:30:00Z", "1000"));
        repository.save(StrategyMode.LIVE, DAY, sample("2026-09-18T14:30:00Z", "1005"));

        List<IntradayValueSample> cached = repository.findDay(StrategyMode.LIVE, DAY);
        repository.invalidateCache();
        List<IntradayValueSample> stored = repository.findDay(StrategyMode.LIVE, DAY);

        assertEquals(cached, stored, "cache and database agree");
        assertEquals(1, stored.size());
        assertEquals(new BigDecimal("1005"), stored.get(0).value());
    }

    @Test
    void oldDaysArePruned() {
        SqliteAccountEquityRepository repository = new SqliteAccountEquityRepository(
                AppDatabase.open(tempDir.resolve("neuralarc.db")));
        repository.save(StrategyMode.LIVE, DAY.minusDays(40), sample("2026-08-09T14:30:00Z", "900"));
        repository.save(StrategyMode.LIVE, DAY, sample("2026-09-18T14:30:00Z", "1000"));

        assertEquals(1, repository.pruneBefore(DAY.minusDays(30)));

        assertTrue(repository.findDay(StrategyMode.LIVE, DAY.minusDays(40)).isEmpty());
        assertEquals(1, repository.findDay(StrategyMode.LIVE, DAY).size());
    }

    private static IntradayValueSample sample(String minute, String value) {
        return new IntradayValueSample(Instant.parse(minute), new BigDecimal(value), new BigDecimal("950"));
    }
}
