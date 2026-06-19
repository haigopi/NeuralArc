package com.neuralarc.service;

import com.neuralarc.db.SqliteStrategyRepository;
import com.neuralarc.model.Strategy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs the Auto Adjust Risk &amp; Stop Loss feature after each regular-session close: for every strategy
 * with the feature active, it nudges the stop-loss and loss buy levels by the configured percentage in
 * the direction the stock moved, persists the result, and stops after the configured number of days.
 *
 * <p>The per-strategy decision is delegated to the pure {@link AutoRiskAdjustmentEngine}; this class
 * owns the once-per-minute daemon tick, the "after market close" gating, the latest-price lookup, and
 * persistence. Re-running after close on the same day is safe and idempotent — the engine guards
 * against a second adjustment for the same market date.
 */
public final class AutoRiskAdjustmentService {
    private static final Logger LOGGER = Logger.getLogger(AutoRiskAdjustmentService.class.getName());
    private static final ZoneId US_EASTERN = ZoneId.of("America/New_York");
    private static final LocalTime REGULAR_CLOSE = LocalTime.of(16, 0);

    private final SqliteStrategyRepository strategyRepository;
    private final MarketHoursService marketHours;
    private final Clock clock;
    private final Function<Strategy, BigDecimal> latestPriceProvider;
    private final Consumer<String> log;

    private ScheduledExecutorService executor;

    public AutoRiskAdjustmentService(SqliteStrategyRepository strategyRepository, MarketHoursService marketHours,
                                     Clock clock, Function<Strategy, BigDecimal> latestPriceProvider,
                                     Consumer<String> log) {
        this.strategyRepository = Objects.requireNonNull(strategyRepository, "strategyRepository");
        this.marketHours = marketHours == null ? new MarketHoursService() : marketHours;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.latestPriceProvider = latestPriceProvider == null ? strategy -> null : latestPriceProvider;
        this.log = log == null ? ignored -> { } : log;
    }

    public synchronized void start() {
        if (executor != null) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "auto-risk-adjust-scheduler");
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
            runAfterCloseAdjustments(Instant.now(clock));
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Auto Adjust Risk & Stop Loss tick failed", ex);
        }
    }

    /**
     * Apply after-close adjustments for the moment {@code now}. Returns the number of strategies
     * adjusted (advanced) in this pass. Package-private and pure-ish (only repository + price provider
     * side effects) so it is unit-testable with a fixed clock.
     */
    int runAfterCloseAdjustments(Instant now) {
        if (!isAfterRegularClose(now)) {
            return 0;
        }
        LocalDate marketDate = now.atZone(US_EASTERN).toLocalDate();
        int adjusted = 0;
        for (Strategy strategy : strategyRepository.findAll()) {
            if (!strategy.autoAdjustRiskConfig().isActive() || !strategy.automatedStopLossEnabled()) {
                continue;
            }
            BigDecimal price = latestPriceProvider.apply(strategy);
            if (price == null || price.signum() <= 0) {
                log.accept("[Auto Adjust] Skipped " + strategy.symbol()
                        + ": no live price available for after-close adjustment.");
                continue;
            }
            Optional<AutoRiskAdjustment> decision = AutoRiskAdjustmentEngine.evaluate(strategy, price, marketDate);
            if (decision.isEmpty()) {
                continue;
            }
            apply(strategy, decision.get());
            strategyRepository.save(strategy);
            adjusted++;
            log.accept("[Auto Adjust] " + strategy.symbol() + ": " + decision.get().description());
        }
        return adjusted;
    }

    /** Whether {@code now} falls after the regular-session close on a trading day (ET). */
    boolean isAfterRegularClose(Instant now) {
        ZonedDateTime eastern = now.atZone(US_EASTERN);
        if (marketHours.isClosedDay(eastern.toLocalDate())) {
            return false;
        }
        return !eastern.toLocalTime().isBefore(REGULAR_CLOSE);
    }

    private void apply(Strategy strategy, AutoRiskAdjustment adjustment) {
        if (adjustment.changedValues()) {
            strategy.setStopLossPrice(adjustment.newStopLossPrice());
            strategy.setBuyLimit1Price(adjustment.newBuyLimit1Price());
            strategy.setBuyLimit2Price(adjustment.newBuyLimit2Price());
        }
        strategy.setAutoAdjustDayCount(adjustment.newDayCount());
        strategy.setAutoAdjustLastAdjustedDate(adjustment.marketDate());
        strategy.setAutoAdjustReferencePrice(adjustment.newReferencePrice());
        strategy.setLastEvent(adjustment.description());
    }
}
