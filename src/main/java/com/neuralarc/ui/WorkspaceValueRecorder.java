package com.neuralarc.ui;

import com.neuralarc.db.SqliteWorkspaceValueRepository;
import com.neuralarc.model.IntradayValueSample;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Keeps today's minute-by-minute holdings value per workspace and persists each minute.
 *
 * <p>The day is measured from the workspace's previous close — its last value on an earlier session
 * day — the workspace counterpart of Alpaca's last-close equity. Before a workspace has a previous
 * day, today's first reading stands in, and the chart names it "Day start" rather than pretending it
 * is a close. The last reading of a minute is the one kept; writes go to the database on
 * {@code writer}, off the EDT.
 */
final class WorkspaceValueRecorder {
    static final String PREVIOUS_CLOSE = "Prev close";
    static final String DAY_START = "Day start";

    private final SqliteWorkspaceValueRepository repository;
    private final Executor writer;
    private final Clock clock;
    private final Consumer<String> log;
    private final Map<String, List<SqliteWorkspaceValueRepository.Point>> today = new HashMap<>();
    private LocalDate loadedDay;

    WorkspaceValueRecorder(SqliteWorkspaceValueRepository repository, Executor writer, Clock clock, Consumer<String> log) {
        this.repository = repository;
        this.writer = writer;
        this.clock = clock;
        this.log = log == null ? ignored -> { } : log;
    }

    /**
     * Records the workspace's value for the current minute. Zero is dropped: it is what a workspace reads
     * before its positions load at startup, and plotting it would draw a false crash.
     */
    synchronized void record(StrategyMode mode, String workspaceId, BigDecimal value) {
        if (mode == null || workspaceId == null || value == null || value.signum() <= 0) {
            return;
        }
        Instant now = Instant.now(clock);
        LocalDate day = sessionDay(now);
        List<SqliteWorkspaceValueRepository.Point> series = series(mode, workspaceId, day);
        SqliteWorkspaceValueRepository.Point point =
                new SqliteWorkspaceValueRepository.Point(now.truncatedTo(ChronoUnit.MINUTES), Monetary.round(value));
        if (!series.isEmpty() && series.get(series.size() - 1).minute().equals(point.minute())) {
            series.set(series.size() - 1, point);
        } else {
            series.add(point);
        }
        writer.execute(() -> {
            try {
                repository.save(mode, workspaceId, day, point);
            } catch (RuntimeException ex) {
                log.accept("[Workspace Chart] Could not save the workspace value sample: " + ex.getMessage());
            }
        });
    }

    /** Today's line for the workspace, each point measured against the day's baseline. */
    synchronized List<IntradayValueSample> today(StrategyMode mode, String workspaceId) {
        LocalDate day = sessionDay(Instant.now(clock));
        List<SqliteWorkspaceValueRepository.Point> series = series(mode, workspaceId, day);
        if (series.isEmpty()) {
            return List.of();
        }
        BigDecimal baseline = previousClose(mode, workspaceId, day).orElse(series.get(0).value());
        return series.stream()
                .map(point -> new IntradayValueSample(point.minute(), point.value(), baseline))
                .toList();
    }

    /** What the dashed baseline is: the previous close, or today's first reading when there is none yet. */
    synchronized String baselineLabel(StrategyMode mode, String workspaceId) {
        return previousClose(mode, workspaceId, sessionDay(Instant.now(clock))).isPresent() ? PREVIOUS_CLOSE : DAY_START;
    }

    private Optional<BigDecimal> previousClose(StrategyMode mode, String workspaceId, LocalDate day) {
        if (mode == null || workspaceId == null) {
            return Optional.empty();
        }
        return repository.previousClose(mode, workspaceId, day);
    }

    private List<SqliteWorkspaceValueRepository.Point> series(StrategyMode mode, String workspaceId, LocalDate day) {
        if (!day.equals(loadedDay)) {
            today.clear();
            loadedDay = day;
            writer.execute(() -> {
                try {
                    repository.pruneBefore(day.minusDays(SqliteWorkspaceValueRepository.RETENTION_DAYS));
                } catch (RuntimeException ex) {
                    log.accept("[Workspace Chart] Could not prune old workspace value samples: " + ex.getMessage());
                }
            });
        }
        if (mode == null || workspaceId == null) {
            return new ArrayList<>();
        }
        return today.computeIfAbsent(mode.name() + "|" + workspaceId,
                ignored -> new ArrayList<>(repository.findDay(mode, workspaceId, day)));
    }

    private static LocalDate sessionDay(Instant instant) {
        return instant.atZone(AccountEquityRecorder.SESSION_ZONE).toLocalDate();
    }
}
