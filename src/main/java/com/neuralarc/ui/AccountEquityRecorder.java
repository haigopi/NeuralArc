package com.neuralarc.ui;

import com.neuralarc.db.SqliteAccountEquityRepository;
import com.neuralarc.model.IntradayValueSample;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Keeps today's minute-by-minute account equity per trading mode and persists each minute.
 *
 * <p>Readings arrive more often than once a minute; the last reading of a minute is the one kept, so
 * the stored series has exactly one point per minute. The day is the US market session day, so a
 * series does not split at the operator's local midnight. The series lives in memory for the chart
 * (read on the EDT) and every write goes to the database on {@code writer}, off the EDT.
 */
final class AccountEquityRecorder {
    static final ZoneId SESSION_ZONE = ZoneId.of("America/New_York");

    private final SqliteAccountEquityRepository repository;
    private final Executor writer;
    private final Clock clock;
    private final Consumer<String> log;
    private final Map<StrategyMode, List<IntradayValueSample>> today = new EnumMap<>(StrategyMode.class);
    private LocalDate loadedDay;

    AccountEquityRecorder(SqliteAccountEquityRepository repository, Executor writer, Clock clock, Consumer<String> log) {
        this.repository = repository;
        this.writer = writer;
        this.clock = clock;
        this.log = log == null ? ignored -> { } : log;
    }

    /**
     * Records a reading for the current minute. A missing or non-positive equity is dropped: it means the
     * account could not be read, and plotting it would draw a false crash to $0.
     */
    synchronized void record(StrategyMode mode, BigDecimal equity, BigDecimal lastEquity) {
        if (mode == null || equity == null || equity.signum() <= 0) {
            return;
        }
        BigDecimal previousClose = lastEquity == null || lastEquity.signum() <= 0 ? equity : lastEquity;
        Instant now = Instant.now(clock);
        LocalDate day = sessionDay(now);
        List<IntradayValueSample> series = series(mode, day);
        IntradayValueSample sample = new IntradayValueSample(
                now.truncatedTo(ChronoUnit.MINUTES), Monetary.round(equity), Monetary.round(previousClose));
        if (!series.isEmpty() && series.get(series.size() - 1).minute().equals(sample.minute())) {
            series.set(series.size() - 1, sample);
        } else {
            series.add(sample);
        }
        writer.execute(() -> {
            try {
                repository.save(mode, day, sample);
            } catch (RuntimeException ex) {
                log.accept("[Equity Chart] Could not save the account equity sample: " + ex.getMessage());
            }
        });
    }

    /** Today's samples for {@code mode}, oldest first. */
    synchronized List<IntradayValueSample> today(StrategyMode mode) {
        return List.copyOf(series(mode, sessionDay(Instant.now(clock))));
    }

    private List<IntradayValueSample> series(StrategyMode mode, LocalDate day) {
        if (!day.equals(loadedDay)) {
            today.clear();
            loadedDay = day;
            writer.execute(() -> {
                try {
                    repository.pruneBefore(day.minusDays(SqliteAccountEquityRepository.RETENTION_DAYS));
                } catch (RuntimeException ex) {
                    log.accept("[Equity Chart] Could not prune old account equity samples: " + ex.getMessage());
                }
            });
        }
        return today.computeIfAbsent(mode, ignored -> new ArrayList<>(repository.findDay(mode, day)));
    }

    private LocalDate sessionDay(Instant instant) {
        return instant.atZone(SESSION_ZONE).toLocalDate();
    }
}
