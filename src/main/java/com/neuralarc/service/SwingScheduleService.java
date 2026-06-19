package com.neuralarc.service;

import com.neuralarc.model.SwingSchedule;

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
 * Fires an autonomous Swing Vault scan once per trading day at its scheduled time. Swing Vault works on
 * daily bars, so unlike the intraday desks it does not re-scan on a sub-day cadence — a single scan per
 * session inside the execution window is enough to surface the day's fresh pullback setups. Weekends
 * and US market holidays are skipped via {@link MarketHoursService}.
 *
 * <p>The decision logic in {@link #evaluate(Instant)} is pure and clock-driven so it is unit-testable
 * without real waiting; {@link #start()} simply ticks it once per minute on a daemon thread.
 */
public final class SwingScheduleService {
    private static final Logger LOGGER = Logger.getLogger(SwingScheduleService.class.getName());
    private static final ZoneId US_EASTERN = ZoneId.of("America/New_York");

    /** Callback invoked (on the scheduler thread) when a scheduled scan should run. */
    public interface ScanTrigger {
        void run(SwingSchedule schedule);
    }

    private final MarketHoursService marketHours;
    private final Clock clock;
    private final ScanTrigger trigger;
    private final Consumer<String> log;

    private ScheduledExecutorService executor;
    private SwingSchedule schedule;
    private LocalDate lastScanDate;

    public SwingScheduleService(MarketHoursService marketHours, Clock clock, ScanTrigger trigger, Consumer<String> log) {
        this.marketHours = marketHours == null ? new MarketHoursService() : marketHours;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.trigger = Objects.requireNonNull(trigger, "trigger");
        this.log = log == null ? ignored -> { } : log;
    }

    public synchronized void setSchedule(SwingSchedule schedule) {
        this.schedule = schedule;
        resetFireState();
    }

    public synchronized SwingSchedule schedule() {
        return schedule;
    }

    public synchronized void clearSchedule() {
        this.schedule = null;
        resetFireState();
    }

    public synchronized void start() {
        if (executor != null) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "swing-vault-scheduler");
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
            LOGGER.log(Level.WARNING, "Swing Vault scheduler tick failed", ex);
        }
    }

    /**
     * Decide whether to fire the once-per-day scan at {@code now} and, if so, invoke the trigger.
     *
     * @return true when a scan was fired
     */
    synchronized boolean evaluate(Instant now) {
        SwingSchedule current = schedule;
        if (current == null || !current.enabled()) {
            return false;
        }
        ZonedDateTime eastern = now.atZone(US_EASTERN);
        LocalDate date = eastern.toLocalDate();
        if (marketHours.isClosedDay(date)) {
            return false;
        }
        LocalTime time = eastern.toLocalTime();
        if (time.isBefore(current.scanTimeEt()) || !time.isBefore(current.executionWindowEndEt())) {
            return false;
        }
        if (date.equals(lastScanDate)) {
            return false; // already scanned today — Swing Vault scans once per session
        }
        fire(current, date);
        return true;
    }

    private void fire(SwingSchedule current, LocalDate date) {
        lastScanDate = date;
        log.accept("[Swing Vault] Scheduled scan firing for workspace " + current.workspaceId()
                + (current.executeAfterScan() ? " (auto-execute enabled)." : " (recommendation only)."));
        trigger.run(current);
    }

    private void resetFireState() {
        lastScanDate = null;
    }
}
