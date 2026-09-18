package com.neuralarc.ui;

import com.neuralarc.db.SqlitePortfolioValueRepository;
import com.neuralarc.model.PortfolioValueSample;
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
 * Keeps today's minute-by-minute portfolio value per trading mode and persists each minute.
 *
 * <p>Readings arrive more often than once a minute; the last reading of a minute is the one kept, so
 * the stored series has exactly one point per minute. The day is the US market session day, so a
 * series does not split at the operator's local midnight. The series lives in memory for the chart
 * (read on the EDT) and every write goes to the database on {@code writer}, off the EDT.
 */
final class PortfolioValueRecorder {
    static final ZoneId SESSION_ZONE = ZoneId.of("America/New_York");

    private final SqlitePortfolioValueRepository repository;
    private final Executor writer;
    private final Clock clock;
    private final Consumer<String> log;
    private final Map<StrategyMode, List<PortfolioValueSample>> today = new EnumMap<>(StrategyMode.class);
    private LocalDate loadedDay;

    PortfolioValueRecorder(SqlitePortfolioValueRepository repository, Executor writer, Clock clock, Consumer<String> log) {
        this.repository = repository;
        this.writer = writer;
        this.clock = clock;
        this.log = log == null ? ignored -> { } : log;
    }

    /**
     * Records a reading for the current minute. An all-zero reading is dropped: it is what the grid
     * reports before positions have loaded at startup, and plotting it would draw a false crash to $0.
     */
    synchronized void record(StrategyMode mode, BigDecimal marketValue, BigDecimal investedValue) {
        if (mode == null || marketValue == null || investedValue == null
                || (marketValue.signum() == 0 && investedValue.signum() == 0)) {
            return;
        }
        Instant now = Instant.now(clock);
        LocalDate day = sessionDay(now);
        List<PortfolioValueSample> series = series(mode, day);
        PortfolioValueSample sample = new PortfolioValueSample(
                now.truncatedTo(ChronoUnit.MINUTES), Monetary.round(marketValue), Monetary.round(investedValue));
        if (!series.isEmpty() && series.get(series.size() - 1).minute().equals(sample.minute())) {
            series.set(series.size() - 1, sample);
        } else {
            series.add(sample);
        }
        writer.execute(() -> {
            try {
                repository.save(mode, day, sample);
            } catch (RuntimeException ex) {
                log.accept("[Portfolio Chart] Could not save the portfolio value sample: " + ex.getMessage());
            }
        });
    }

    /** Today's samples for {@code mode}, oldest first. */
    synchronized List<PortfolioValueSample> today(StrategyMode mode) {
        return List.copyOf(series(mode, sessionDay(Instant.now(clock))));
    }

    private List<PortfolioValueSample> series(StrategyMode mode, LocalDate day) {
        if (!day.equals(loadedDay)) {
            today.clear();
            loadedDay = day;
            writer.execute(() -> {
                try {
                    repository.pruneBefore(day.minusDays(SqlitePortfolioValueRepository.RETENTION_DAYS));
                } catch (RuntimeException ex) {
                    log.accept("[Portfolio Chart] Could not prune old portfolio value samples: " + ex.getMessage());
                }
            });
        }
        return today.computeIfAbsent(mode, ignored -> new ArrayList<>(repository.findDay(mode, day)));
    }

    private LocalDate sessionDay(Instant instant) {
        return instant.atZone(SESSION_ZONE).toLocalDate();
    }
}
