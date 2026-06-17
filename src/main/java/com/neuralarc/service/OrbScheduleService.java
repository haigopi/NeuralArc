package com.neuralarc.service;

import com.neuralarc.model.OrbSchedule;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fires an autonomous ORB analysis after the opening range closes on each trading day, within
 * the execution window ([rangeAnalysisTimeEt, executionWindowEndEt)). The schedule fires once
 * per day; weekends and US market holidays are skipped via {@link MarketHoursService}.
 *
 * <p>The decision logic in {@link #evaluate(Instant)} is pure and clock-driven so it is
 * unit-testable without real waiting; {@link #start()} ticks it once per minute on a daemon thread.
 */
public final class OrbScheduleService {
    private static final Logger LOGGER = Logger.getLogger(OrbScheduleService.class.getName());
    private static final ZoneId US_EASTERN = ZoneId.of("America/New_York");

    /** Callback invoked (on the scheduler thread) when a scheduled analysis should run. */
    public interface AnalysisTrigger {
        void run(OrbSchedule schedule);
    }

    private final MarketHoursService marketHours;
    private final Clock clock;
    private final AnalysisTrigger trigger;
    private final Consumer<String> log;

    private ScheduledExecutorService executor;
    private OrbSchedule schedule;
    private LocalDate lastFiredDate;

    public OrbScheduleService(MarketHoursService marketHours, Clock clock, AnalysisTrigger trigger, Consumer<String> log) {
        this.marketHours = marketHours == null ? new MarketHoursService() : marketHours;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.trigger = Objects.requireNonNull(trigger, "trigger");
        this.log = log == null ? ignored -> { } : log;
    }

    public synchronized void setSchedule(OrbSchedule schedule) {
        this.schedule = schedule;
        lastFiredDate = null;
    }

    public synchronized OrbSchedule schedule() {
        return schedule;
    }

    public synchronized void clearSchedule() {
        this.schedule = null;
        lastFiredDate = null;
    }

    public synchronized void start() {
        if (executor != null) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "orb-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleAtFixedRate(this::tick, 0, 1, TimeUnit.MINUTES);
    }

    public synchronized void stop() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private void tick() {
        try {
            evaluate(Instant.now(clock));
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "ORB scheduler tick failed", ex);
        }
    }

    /**
     * Decide whether to fire an analysis at {@code now} and, if so, invoke the trigger.
     *
     * @return true when an analysis was fired
     */
    synchronized boolean evaluate(Instant now) {
        OrbSchedule current = schedule;
        if (current == null || !current.enabled()) {
            return false;
        }
        ZonedDateTime eastern = now.atZone(US_EASTERN);
        LocalDate date = eastern.toLocalDate();
        if (marketHours.isClosedDay(date)) {
            return false;
        }
        LocalTime time = eastern.toLocalTime();
        if (time.isBefore(current.rangeAnalysisTimeEt()) || !time.isBefore(current.executionWindowEndEt())) {
            return false;
        }
        if (date.equals(lastFiredDate)) {
            return false;
        }
        lastFiredDate = date;
        log.accept("[ORB] Scheduled analysis firing for workspace " + current.workspaceId()
                + " at " + current.rangeAnalysisTimeEt() + " ET"
                + (current.executeAfterRangeClose() ? " (auto-execute enabled)." : " (recommendation only)."));
        trigger.run(current);
        return true;
    }
}
