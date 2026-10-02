package com.neuralarc.service;

import com.neuralarc.model.PreOpenRepositionSettings;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fires the pre-open reposition once each trading morning, in the window before the bell.
 *
 * <p>The window is {@code [open - minutesBeforeOpen, open)}, taken from {@link MarketHoursService} so
 * holidays, weekends and half days are the market's business rather than this class's. It fires once
 * a day: a run that has already happened is not repeated when the next tick lands in the same window.
 *
 * <p>{@link #evaluate(Instant)} is pure and clock-driven so the timing can be tested without waiting
 * for a morning; {@link #start()} ticks it once a minute, which is also what lets a late start still
 * catch the window.
 */
public final class PreOpenRepositionService {
    private static final Logger LOGGER = Logger.getLogger(PreOpenRepositionService.class.getName());
    private static final ZoneId US_EASTERN = ZoneId.of("America/New_York");

    /** Invoked on the scheduler thread when the morning's reposition should run. */
    public interface RepositionTrigger {
        void run(PreOpenRepositionSettings settings);
    }

    private final MarketHoursService marketHours;
    private final Clock clock;
    private final RepositionTrigger trigger;
    private final Consumer<String> log;
    private ScheduledExecutorService executor;
    private PreOpenRepositionSettings settings = PreOpenRepositionSettings.defaults();
    private LocalDate lastFiredDate;

    public PreOpenRepositionService(MarketHoursService marketHours, Clock clock, RepositionTrigger trigger,
                                    Consumer<String> log) {
        this.marketHours = marketHours == null ? new MarketHoursService() : marketHours;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.trigger = Objects.requireNonNull(trigger, "trigger");
        this.log = log == null ? ignored -> { } : log;
    }

    public synchronized void setSettings(PreOpenRepositionSettings settings) {
        this.settings = settings == null ? PreOpenRepositionSettings.defaults() : settings;
        lastFiredDate = null;
    }

    public synchronized PreOpenRepositionSettings settings() {
        return settings;
    }

    public synchronized void start() {
        if (executor != null) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "pre-open-reposition");
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

    /** The run could not start — the broker was not connected yet. Let a later tick try again. */
    public synchronized void deferToday() {
        lastFiredDate = null;
    }

    private void tick() {
        try {
            evaluate(Instant.now(clock));
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Pre-open reposition tick failed", ex);
        }
    }

    /** @return true when the morning's reposition was fired */
    synchronized boolean evaluate(Instant now) {
        PreOpenRepositionSettings current = settings;
        if (current == null || !current.enabled()) {
            return false;
        }
        LocalDate today = now.atZone(US_EASTERN).toLocalDate();
        if (today.equals(lastFiredDate) || marketHours.isClosedDay(today)) {
            return false;
        }
        Instant open = marketHours.nextMarketOpen(now, false);
        if (open == null) {
            return false;
        }
        // Only the run-up to today's open counts. After the bell the window has passed, and tomorrow's
        // open is a day away.
        if (!open.atZone(US_EASTERN).toLocalDate().equals(today)) {
            return false;
        }
        Duration untilOpen = Duration.between(now, open);
        // Once the bell has rung, nextMarketOpen answers with the moment asked about, so a zero gap means
        // the market is already trading — the window is behind us, not in front of us.
        if (untilOpen.isNegative() || untilOpen.isZero() || untilOpen.toMinutes() > current.minutesBeforeOpen()) {
            return false;
        }
        lastFiredDate = today;
        log.accept("[Pre-open Reposition] Running: " + current.summary() + ".");
        trigger.run(current);
        return true;
    }
}
