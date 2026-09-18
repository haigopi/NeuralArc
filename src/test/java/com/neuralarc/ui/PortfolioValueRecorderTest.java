package com.neuralarc.ui;

import com.neuralarc.db.AppDatabase;
import com.neuralarc.db.SqlitePortfolioValueRepository;
import com.neuralarc.model.PortfolioValueSample;
import com.neuralarc.model.StrategyMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioValueRecorderTest {
    @TempDir
    Path tempDir;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-18T14:30:10Z"));

    @Test
    void keepsOnePointPerMinuteHoldingTheMinutesLastReading() {
        SqlitePortfolioValueRepository repository = repository();
        PortfolioValueRecorder recorder = recorder(repository);

        recorder.record(StrategyMode.LIVE, new BigDecimal("1000"), new BigDecimal("990"));
        clock.at("2026-09-18T14:30:40Z");
        recorder.record(StrategyMode.LIVE, new BigDecimal("1004"), new BigDecimal("990"));
        clock.at("2026-09-18T14:31:05Z");
        recorder.record(StrategyMode.LIVE, new BigDecimal("1010"), new BigDecimal("990"));

        List<PortfolioValueSample> today = recorder.today(StrategyMode.LIVE);
        assertEquals(List.of(new BigDecimal("1004.00"), new BigDecimal("1010.00")),
                today.stream().map(PortfolioValueSample::marketValue).toList());
        assertEquals(Instant.parse("2026-09-18T14:30:00Z"), today.get(0).minute());
        repository.invalidateCache();
        assertEquals(today, repository.findDay(StrategyMode.LIVE, LocalDate.of(2026, 9, 18)), "every minute is persisted");
    }

    @Test
    void todaysSeriesIsRestoredAfterARestart() {
        SqlitePortfolioValueRepository repository = repository();
        recorder(repository).record(StrategyMode.PAPER, new BigDecimal("5000"), new BigDecimal("4900"));

        PortfolioValueRecorder restarted = recorder(repository());

        assertEquals(1, restarted.today(StrategyMode.PAPER).size());
    }

    @Test
    void anAllZeroReadingFromBeforePositionsLoadIsNotPlotted() {
        PortfolioValueRecorder recorder = recorder(repository());

        recorder.record(StrategyMode.LIVE, BigDecimal.ZERO, BigDecimal.ZERO);

        assertTrue(recorder.today(StrategyMode.LIVE).isEmpty());
    }

    @Test
    void aNewMarketDayStartsAFreshSeries() {
        PortfolioValueRecorder recorder = recorder(repository());
        recorder.record(StrategyMode.LIVE, new BigDecimal("1000"), new BigDecimal("990"));

        clock.at("2026-09-19T14:30:00Z");

        assertTrue(recorder.today(StrategyMode.LIVE).isEmpty());
    }

    private SqlitePortfolioValueRepository repository() {
        return new SqlitePortfolioValueRepository(AppDatabase.open(tempDir.resolve("neuralarc.db")));
    }

    private PortfolioValueRecorder recorder(SqlitePortfolioValueRepository repository) {
        return new PortfolioValueRecorder(repository, Runnable::run, clock, null);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void at(String instant) {
            now = Instant.parse(instant);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
