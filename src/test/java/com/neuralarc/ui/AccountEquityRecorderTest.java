package com.neuralarc.ui;

import com.neuralarc.db.AppDatabase;
import com.neuralarc.db.SqliteAccountEquityRepository;
import com.neuralarc.model.IntradayValueSample;
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

class AccountEquityRecorderTest {
    @TempDir
    Path tempDir;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-18T14:30:10Z"));

    @Test
    void keepsOnePointPerMinuteHoldingTheMinutesLastReading() {
        SqliteAccountEquityRepository repository = repository();
        AccountEquityRecorder recorder = recorder(repository);

        recorder.record(StrategyMode.LIVE, new BigDecimal("1000"), new BigDecimal("990"));
        clock.at("2026-09-18T14:30:40Z");
        recorder.record(StrategyMode.LIVE, new BigDecimal("1004"), new BigDecimal("990"));
        clock.at("2026-09-18T14:31:05Z");
        recorder.record(StrategyMode.LIVE, new BigDecimal("1010"), new BigDecimal("990"));

        List<IntradayValueSample> today = recorder.today(StrategyMode.LIVE);
        assertEquals(List.of(new BigDecimal("1004.00"), new BigDecimal("1010.00")),
                today.stream().map(IntradayValueSample::value).toList());
        assertEquals(Instant.parse("2026-09-18T14:30:00Z"), today.get(0).minute());
        repository.invalidateCache();
        assertEquals(today, repository.findDay(StrategyMode.LIVE, LocalDate.of(2026, 9, 18)), "every minute is persisted");
    }

    @Test
    void todaysSeriesIsRestoredAfterARestart() {
        SqliteAccountEquityRepository repository = repository();
        recorder(repository).record(StrategyMode.PAPER, new BigDecimal("5000"), new BigDecimal("4900"));

        AccountEquityRecorder restarted = recorder(repository());

        assertEquals(1, restarted.today(StrategyMode.PAPER).size());
    }

    @Test
    void anUnreadableAccountIsNotPlottedAsAZeroEquityCrash() {
        AccountEquityRecorder recorder = recorder(repository());

        recorder.record(StrategyMode.LIVE, BigDecimal.ZERO, new BigDecimal("1000"));
        recorder.record(StrategyMode.LIVE, null, new BigDecimal("1000"));

        assertTrue(recorder.today(StrategyMode.LIVE).isEmpty());
    }

    @Test
    void aMissingPreviousCloseFallsBackToTheReadingItself() {
        AccountEquityRecorder recorder = recorder(repository());

        recorder.record(StrategyMode.LIVE, new BigDecimal("1000"), BigDecimal.ZERO);

        assertEquals(new BigDecimal("1000.00"), recorder.today(StrategyMode.LIVE).get(0).baseline(),
                "a zero baseline would report the whole account as today's gain");
    }

    @Test
    void aNewMarketDayStartsAFreshSeries() {
        AccountEquityRecorder recorder = recorder(repository());
        recorder.record(StrategyMode.LIVE, new BigDecimal("1000"), new BigDecimal("990"));

        clock.at("2026-09-19T14:30:00Z");

        assertTrue(recorder.today(StrategyMode.LIVE).isEmpty());
    }

    private SqliteAccountEquityRepository repository() {
        return new SqliteAccountEquityRepository(AppDatabase.open(tempDir.resolve("neuralarc.db")));
    }

    private AccountEquityRecorder recorder(SqliteAccountEquityRepository repository) {
        return new AccountEquityRecorder(repository, Runnable::run, clock, null);
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
