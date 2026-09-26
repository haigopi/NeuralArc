package com.neuralarc.service;

import com.neuralarc.model.HistoryReentrySchedule;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fires the standing Trade History re-entry scan on its chosen weekday, at or after its time and
 * before the regular-session close. Weekends and US market holidays are skipped via
 * {@link MarketHoursService}.
 *
 * <p>A fortnightly cadence cannot be read off the clock, so it is enforced against the schedule's own
 * {@code lastRunDate}: a run only fires once the cadence's minimum gap has passed. The caller records
 * that date when the run actually places something, which means an attempt that failed before placing
 * does not cost the operator a fortnight.
 *
 * <p>{@link #evaluate(Instant)} is pure and clock-driven so it can be tested without waiting;
 * {@link #start()} ticks it once a minute on a daemon thread, so a run whose exact minute was missed
 * — the app was starting up — still happens that day.
 */
public final class HistoryReentryScheduleService {
    private static final Logger LOGGER = Logger.getLogger(HistoryReentryScheduleService.class.getName());
    private static final ZoneId US_EASTERN = ZoneId.of("America/New_York");
    /** Nothing starts after the close: the entry price is read off the live session. */
    static final LocalTime SESSION_CLOSE_ET = LocalTime.of(16, 0);

    /** Invoked on the scheduler thread when a run should happen. */
    public interface RunTrigger {
        void run(HistoryReentrySchedule schedule);
    }

    private final MarketHoursService marketHours;
    private final Clock clock;
    private final RunTrigger trigger;
    private final Consumer<String> log;
    private ScheduledExecutorService executor;
    private HistoryReentrySchedule schedule;
    private LocalDate lastFiredDate;

    public HistoryReentryScheduleService(MarketHoursService marketHours, Clock clock, RunTrigger trigger,
                                         Consumer<String> log) {
        this.marketHours = marketHours == null ? new MarketHoursService() : marketHours;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.trigger = Objects.requireNonNull(trigger, "trigger");
        this.log = log == null ? ignored -> { } : log;
    }

    public synchronized void setSchedule(HistoryReentrySchedule schedule) {
        this.schedule = schedule;
        lastFiredDate = null;
    }

    public synchronized HistoryReentrySchedule schedule() {
        return schedule;
    }

    public synchronized void start() {
        if (executor != null) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "history-reentry-scheduler");
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

    /** The run could not start — the broker was not connected yet. Let a later tick try again today. */
    public synchronized void deferToday() {
        lastFiredDate = null;
    }

    private void tick() {
        try {
            evaluate(Instant.now(clock));
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "History re-entry scheduler tick failed", ex);
        }
    }

    /** @return true when a run was fired */
    synchronized boolean evaluate(Instant now) {
        HistoryReentrySchedule current = schedule;
        if (current == null || !current.enabled()) {
            return false;
        }
        ZonedDateTime eastern = now.atZone(US_EASTERN);
        LocalDate date = eastern.toLocalDate();
        if (marketHours.isClosedDay(date) || date.getDayOfWeek() != current.day() || date.equals(lastFiredDate)) {
            return false;
        }
        LocalTime time = eastern.toLocalTime();
        if (time.isBefore(current.scanTimeEt()) || !time.isBefore(SESSION_CLOSE_ET)) {
            return false;
        }
        if (!cadenceElapsed(current, date)) {
            return false;
        }
        lastFiredDate = date;
        log.accept("[History Re-entry] Scheduled run firing: " + current.summary() + ".");
        trigger.run(current);
        return true;
    }

    /** True when enough days have passed since the last run for this cadence to come round again. */
    private static boolean cadenceElapsed(HistoryReentrySchedule schedule, LocalDate today) {
        LocalDate last = schedule.lastRunDate();
        return last == null
                || ChronoUnit.DAYS.between(last, today) >= schedule.cadence().minimumDaysBetweenRuns();
    }
}
