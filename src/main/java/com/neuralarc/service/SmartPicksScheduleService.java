package com.neuralarc.service;

import com.neuralarc.model.SmartPicksSchedule;

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
 * Fires a Smart Picks workspace's autonomous scan once on each chosen weekday, at or after its scan time
 * and before the regular-session close. Weekends and US market holidays are skipped via
 * {@link MarketHoursService}. The decision in {@link #evaluate(Instant)} is pure and clock-driven so it
 * is unit-testable; {@link #start()} ticks it once a minute on a daemon thread, so a scan whose exact
 * minute was missed (the app started late) still runs that day.
 */
public final class SmartPicksScheduleService {
    private static final Logger LOGGER = Logger.getLogger(SmartPicksScheduleService.class.getName());
    private static final ZoneId US_EASTERN = ZoneId.of("America/New_York");
    /** No scan starts after the regular session closes: picks are priced off the live session. */
    static final LocalTime SESSION_CLOSE_ET = LocalTime.of(16, 0);

    /** Invoked on the scheduler thread when a scan should run. */
    public interface ScanTrigger {
        void run(SmartPicksSchedule schedule);
    }

    private final MarketHoursService marketHours;
    private final Clock clock;
    private final ScanTrigger trigger;
    private final Consumer<String> log;
    private ScheduledExecutorService executor;
    private SmartPicksSchedule schedule;
    private LocalDate lastFiredDate;

    public SmartPicksScheduleService(MarketHoursService marketHours, Clock clock, ScanTrigger trigger, Consumer<String> log) {
        this.marketHours = marketHours == null ? new MarketHoursService() : marketHours;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.trigger = Objects.requireNonNull(trigger, "trigger");
        this.log = log == null ? ignored -> { } : log;
    }

    public synchronized void setSchedule(SmartPicksSchedule schedule) {
        this.schedule = schedule;
        lastFiredDate = null;
    }

    public synchronized SmartPicksSchedule schedule() {
        return schedule;
    }

    public synchronized void clearSchedule() {
        schedule = null;
        lastFiredDate = null;
    }

    public synchronized void start() {
        if (executor != null) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "smart-picks-scheduler");
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

    /**
     * The scan could not start — for example the broker was not connected yet. Forget today's fire so a
     * later tick runs it, instead of the day's scan being silently lost.
     */
    public synchronized void deferToday() {
        lastFiredDate = null;
    }

    private void tick() {
        try {
            evaluate(Instant.now(clock));
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Smart Picks scheduler tick failed", ex);
        }
    }

    /** @return true when a scan was fired */
    synchronized boolean evaluate(Instant now) {
        SmartPicksSchedule current = schedule;
        if (current == null || !current.enabled()) {
            return false;
        }
        ZonedDateTime eastern = now.atZone(US_EASTERN);
        LocalDate date = eastern.toLocalDate();
        if (marketHours.isClosedDay(date) || !current.days().contains(date.getDayOfWeek())) {
            return false;
        }
        LocalTime time = eastern.toLocalTime();
        if (time.isBefore(current.scanTimeEt()) || !time.isBefore(SESSION_CLOSE_ET) || date.equals(lastFiredDate)) {
            return false;
        }
        lastFiredDate = date;
        log.accept("[Smart Picks] Scheduled scan firing for workspace " + current.workspaceId()
                + " (" + current.workspaceCode() + ")"
                + (current.executeAfterScan() ? " with orders placed automatically." : " (recommendation only)."));
        trigger.run(current);
        return true;
    }
}
