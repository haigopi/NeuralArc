package com.neuralarc.service;

import com.neuralarc.gaprocket.GapRocketConfig;
import com.neuralarc.model.GapAndGoSchedule;

import java.time.Clock;
import java.time.Duration;
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
 * Fires an autonomous gap-and-go scan at its scheduled premarket time on trading days, then
 * optionally re-scans on a cadence through the post-open execution window. Weekends and US market
 * holidays are skipped via {@link MarketHoursService}.
 *
 * <p>The decision logic in {@link #evaluate(Instant)} is pure and clock-driven so it is unit-testable
 * without real waiting; {@link #start()} simply ticks it once per minute on a daemon thread.
 */
public final class GapAndGoScheduleService {
    private static final Logger LOGGER = Logger.getLogger(GapAndGoScheduleService.class.getName());
    private static final ZoneId US_EASTERN = ZoneId.of("America/New_York");

    /** Callback invoked (on the scheduler thread) when a scheduled scan should run. */
    public interface ScanTrigger {
        void run(GapAndGoSchedule schedule);
    }

    private final MarketHoursService marketHours;
    private final Clock clock;
    private final ScanTrigger trigger;
    private final Consumer<String> log;

    private ScheduledExecutorService executor;
    private GapAndGoSchedule schedule;
    private LocalDate lastInitialScanDate;
    private Instant lastScanAt;

    public GapAndGoScheduleService(MarketHoursService marketHours, Clock clock, ScanTrigger trigger, Consumer<String> log) {
        this.marketHours = marketHours == null ? new MarketHoursService() : marketHours;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.trigger = Objects.requireNonNull(trigger, "trigger");
        this.log = log == null ? ignored -> { } : log;
    }

    public synchronized void setSchedule(GapAndGoSchedule schedule) {
        this.schedule = schedule;
        resetFireState();
    }

    public synchronized GapAndGoSchedule schedule() {
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
            Thread thread = new Thread(runnable, "gap-and-go-scheduler");
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
            LOGGER.log(Level.WARNING, "Gap-and-Go scheduler tick failed", ex);
        }
    }

    /**
     * Decide whether to fire a scan at {@code now} and, if so, invoke the trigger.
     *
     * @return true when a scan was fired
     */
    synchronized boolean evaluate(Instant now) {
        GapAndGoSchedule current = schedule;
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
        if (!date.equals(lastInitialScanDate)) {
            fire(current, now, date, "initial premarket scan");
            return true;
        }
        Duration cadence = rescanCadence(current.config().executionFrequency());
        if (cadence == null) {
            return false;
        }
        if (lastScanAt == null || Duration.between(lastScanAt, now).compareTo(cadence) >= 0) {
            fire(current, now, date, "re-scan");
            return true;
        }
        return false;
    }

    private void fire(GapAndGoSchedule current, Instant now, LocalDate date, String reason) {
        lastInitialScanDate = date;
        lastScanAt = now;
        log.accept("[Gap Rocket] Scheduled " + reason + " firing for workspace " + current.workspaceId()
                + (current.executeAfterScan() ? " (auto-execute enabled)." : " (recommendation only)."));
        trigger.run(current);
    }

    private static Duration rescanCadence(GapRocketConfig.ExecutionFrequency frequency) {
        return switch (frequency) {
            case EVERY_5_MINUTES -> Duration.ofMinutes(5);
            case EVERY_15_MINUTES -> Duration.ofMinutes(15);
            case MANUAL, MARKET_OPEN_ONLY -> null;
        };
    }

    private void resetFireState() {
        lastInitialScanDate = null;
        lastScanAt = null;
    }
}
