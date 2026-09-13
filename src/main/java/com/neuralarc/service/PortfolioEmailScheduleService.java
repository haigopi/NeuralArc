package com.neuralarc.service;

import com.neuralarc.model.PortfolioEmailSettings;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sends the portfolio snapshot email at the times in {@link PortfolioEmailSettings} on US trading
 * days; weekends and market holidays are skipped via {@link MarketHoursService}. Each time fires once
 * a day, and only within {@link #GRACE} of it: when the app was closed or the computer asleep at that
 * time, the email is skipped rather than sent late with stale figures.
 *
 * <p>{@link #evaluate(Instant)} is pure and clock-driven so it is unit-testable without waiting;
 * {@link #start()} ticks it on a daemon thread.
 */
public final class PortfolioEmailScheduleService {
    private static final Logger LOGGER = Logger.getLogger(PortfolioEmailScheduleService.class.getName());
    private static final ZoneId US_EASTERN = ZoneId.of("America/New_York");
    /** How late a time may still send; later than this and that email is skipped. */
    static final Duration GRACE = Duration.ofMinutes(3);
    private static final long TICK_SECONDS = 20;

    /** Called on the scheduler thread when a send time is reached. */
    public interface SendTrigger {
        void send(PortfolioEmailSettings.Slot slot);
    }

    private final MarketHoursService marketHours;
    private final Clock clock;
    private final SendTrigger trigger;
    private final Consumer<String> log;
    private final Set<LocalTime> sentToday = new HashSet<>();
    private LocalDate sentDate;
    private PortfolioEmailSettings settings = PortfolioEmailSettings.defaults();
    private ScheduledExecutorService executor;

    public PortfolioEmailScheduleService(MarketHoursService marketHours, Clock clock, SendTrigger trigger, Consumer<String> log) {
        this.marketHours = marketHours == null ? new MarketHoursService() : marketHours;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.trigger = Objects.requireNonNull(trigger, "trigger");
        this.log = log == null ? ignored -> { } : log;
    }

    /** Applies new times; a time already sent today is not sent again after re-saving. */
    public synchronized void setSettings(PortfolioEmailSettings settings) {
        this.settings = settings == null ? PortfolioEmailSettings.defaults() : settings;
        log.accept("[EMAIL] Portfolio snapshot emails " + this.settings.describe() + ".");
    }

    public synchronized PortfolioEmailSettings settings() {
        return settings;
    }

    public synchronized void start() {
        if (executor != null) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "portfolio-email-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleAtFixedRate(this::tick, 5, TICK_SECONDS, TimeUnit.SECONDS);
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
            LOGGER.log(Level.WARNING, "Portfolio email scheduler tick failed", ex);
        }
    }

    /**
     * Sends the first due time at {@code now}, if any.
     *
     * @return the time that was sent
     */
    synchronized Optional<PortfolioEmailSettings.Slot> evaluate(Instant now) {
        ZonedDateTime eastern = now.atZone(US_EASTERN);
        LocalDate date = eastern.toLocalDate();
        if (!date.equals(sentDate)) {
            sentDate = date;
            sentToday.clear();
        }
        if (marketHours.isClosedDay(date)) {
            return Optional.empty();
        }
        LocalTime time = eastern.toLocalTime();
        for (PortfolioEmailSettings.Slot slot : settings.activeSlots()) {
            if (sentToday.contains(slot.timeEt())) {
                continue;
            }
            long late = Duration.between(slot.timeEt(), time).getSeconds();
            if (late >= 0 && late < GRACE.getSeconds()) {
                sentToday.add(slot.timeEt());
                log.accept("[EMAIL] Portfolio snapshot time reached: " + slot.label() + " (" + slot.timeEt() + " ET).");
                trigger.send(slot);
                return Optional.of(slot);
            }
        }
        return Optional.empty();
    }
}
