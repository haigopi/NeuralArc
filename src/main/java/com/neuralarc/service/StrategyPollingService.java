package com.neuralarc.service;

import com.neuralarc.api.AlpacaClient;
import com.neuralarc.api.AlpacaTradeUpdateEvent;
import com.neuralarc.model.PauseReason;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyEventType;
import com.neuralarc.model.StrategyExecutionEvent;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StrategyPollingService {
    private static final Logger LOGGER = Logger.getLogger(StrategyPollingService.class.getName());
    private static final int STREAM_HEALTHY_GRACE_SECONDS = 120;
    private static final int STREAM_POLL_BACKOFF_MULTIPLIER = 3;

    private final StrategyRepository strategyRepository;
    private final StrategyExecutionEventRepository eventRepository;
    private final StrategyEngine strategyEngine;
    private final StrategyService strategyService;
    private final AppSettingsService appSettingsService;
    private final MarketHoursService marketHoursService;
    private final ExecutorService pollExecutor;
    private volatile Instant lastStreamingEventAt;
    private volatile Boolean lastTradingSessionOpen;
    private volatile PollListener pollListener = PollListener.NOOP;

    public StrategyPollingService(
            StrategyRepository strategyRepository,
            StrategyOrderRepository orderRepository,
            StrategyExecutionEventRepository eventRepository,
            AlpacaClient alpacaClient
    ) {
        this(
                strategyRepository,
                orderRepository,
                eventRepository,
                alpacaClient,
                new AppSettingsService(),
                new MarketHoursService()
        );
    }

    public StrategyPollingService(
            StrategyRepository strategyRepository,
            StrategyOrderRepository orderRepository,
            StrategyExecutionEventRepository eventRepository,
            AlpacaClient alpacaClient,
            AppSettingsService appSettingsService,
            MarketHoursService marketHoursService
    ) {
        this.strategyRepository = strategyRepository;
        this.eventRepository = eventRepository;
        this.appSettingsService = appSettingsService;
        this.marketHoursService = marketHoursService;
        StrategyEventBus eventBus = new StrategyEventBus();
        StrategyStateMachine stateMachine = new StrategyStateMachine(eventRepository, eventBus);
        this.strategyEngine = new StrategyEngine(
                strategyRepository,
                orderRepository,
                stateMachine,
                alpacaClient,
                appSettingsService,
                marketHoursService
        );
        this.strategyService = new StrategyService(
                strategyRepository,
                orderRepository,
                eventRepository,
                alpacaClient,
                new StrategyValidator(),
                true,
                null,
                appSettingsService,
                marketHoursService
        );
        int threadCount = Math.max(2, Math.min(6, Runtime.getRuntime().availableProcessors()));
        this.pollExecutor = Executors.newFixedThreadPool(threadCount, runnable -> {
            Thread thread = new Thread(runnable, "neuralarc-poll-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void pollDueStrategies() {
        Instant now = Instant.now();
        AppSettingsService.AppSettings settings = appSettingsService.load();
        if (settings.autoPausePollingWhenMarketClosed()) {
            boolean marketOpen = marketHoursService.isTradingSessionOpen(settings.extendedHoursTradingEnabled());
            handleMarketSessionTransition(marketOpen, settings.extendedHoursTradingEnabled(), now);
            if (!marketOpen) {
                return;
            }
        } else {
            lastTradingSessionOpen = null;
        }

        List<Future<?>> futures = new ArrayList<>();
        for (Strategy strategy : strategyRepository.findActive()) {
            if (shouldPoll(strategy, now)) {
                String strategyId = strategy.id();
                futures.add(pollExecutor.submit(() -> pollStrategy(strategyId)));
            }
        }
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Failed waiting for poll task completion", ex);
            }
        }
    }

    public void pollActiveStrategies() {
        pollDueStrategies();
    }

    public void pollStrategy(String strategyId) {
        Optional<Strategy> maybeStrategy = strategyRepository.findById(strategyId);
        if (maybeStrategy.isEmpty()) {
            return;
        }
        Strategy strategy = maybeStrategy.get();
        if (strategy.status() != StrategyStatus.ACTIVE) {
            return;
        }

        try {
            pollListener.onPollStarted(strategy.id());
            strategyEngine.reconcile(strategy);
            eventRepository.save(event(strategy.id(), StrategyEventType.POLL_SUCCESS,
                    "Poll completed", "{\"strategyId\":\"" + strategy.id() + "\"}"));
            pollListener.onPollCompleted(strategy.id());
        } catch (Exception ex) {
            strategy.setStatus(StrategyStatus.PAUSED);
            strategy.setCurrentState(StrategyLifecycleState.PAUSED);
            strategy.setPauseReason(PauseReason.SYSTEM_ERROR);
            strategy.setLastPolledAt(Instant.now());
            strategy.setLastError(ex.getMessage());
            strategyRepository.save(strategy);
            eventRepository.save(event(strategy.id(), StrategyEventType.POLL_ERROR, ex.getMessage(), "{}"));
            pollListener.onPollFailed(strategy.id());
            LOGGER.log(Level.WARNING, "Polling failed for strategy " + strategy.id(), ex);
        }
    }

    public void setPollListener(PollListener pollListener) {
        this.pollListener = pollListener == null ? PollListener.NOOP : pollListener;
    }

    public void shutdown() {
        pollExecutor.shutdownNow();
    }

    public void onTradeUpdate(AlpacaTradeUpdateEvent updateEvent) {
        if (updateEvent == null || updateEvent.orderData() == null) {
            return;
        }
        try {
            boolean applied = strategyEngine.applyStreamingOrderUpdate(updateEvent.orderData());
            if (applied) {
                lastStreamingEventAt = Instant.now();
                LOGGER.info(() -> "Applied trade update event "
                        + updateEvent.eventType()
                        + " for orderId=" + updateEvent.orderData().orderId()
                        + " clientOrderId=" + updateEvent.orderData().clientOrderId());
            } else {
                LOGGER.info(() -> "Ignored trade update event "
                        + updateEvent.eventType()
                        + " because no matching local order was found for orderId="
                        + updateEvent.orderData().orderId()
                        + " clientOrderId=" + updateEvent.orderData().clientOrderId());
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to process streaming trade update", ex);
        }
    }

    private boolean shouldPoll(Strategy strategy, Instant now) {
        if (strategy.lastPolledAt() == null) {
            return true;
        }
        long elapsedSeconds = Duration.between(strategy.lastPolledAt(), now).getSeconds();
        long pollInterval = Math.max(1, strategy.pollingIntervalSeconds());
        if (isStreamHealthy(now)) {
            pollInterval = pollInterval * STREAM_POLL_BACKOFF_MULTIPLIER;
        }
        return elapsedSeconds >= pollInterval;
    }

    private boolean isStreamHealthy(Instant now) {
        return lastStreamingEventAt != null
                && Duration.between(lastStreamingEventAt, now).getSeconds() <= STREAM_HEALTHY_GRACE_SECONDS;
    }

    private void handleMarketSessionTransition(boolean marketOpen, boolean extendedHoursEnabled, Instant now) {
        if (lastTradingSessionOpen == null || lastTradingSessionOpen != marketOpen) {
            lastTradingSessionOpen = marketOpen;
            if (marketOpen) {
                LOGGER.info(() -> "Market session open. Auto-resuming eligible strategies."
                        + " Extended hours enabled=" + extendedHoursEnabled);
            } else {
                LOGGER.info(() -> "Market session closed. Auto-pausing eligible strategies until "
                        + marketHoursService.nextMarketOpen(now, extendedHoursEnabled)
                        + ". Extended hours enabled=" + extendedHoursEnabled);
            }
        }
        if (marketOpen) {
            resumeAutoPausedStrategies();
        } else {
            autoPauseActiveStrategiesForMarketClose();
        }
    }

    private void autoPauseActiveStrategiesForMarketClose() {
        for (Strategy strategy : strategyRepository.findActive()) {
            strategyService.autoPauseForMarketClose(strategy.id(), "Strategy auto-paused because market is closed");
        }
    }

    private void resumeAutoPausedStrategies() {
        for (Strategy strategy : strategyRepository.findAll()) {
            if (strategy.status() != StrategyStatus.PAUSED) {
                continue;
            }
            if (strategy.pauseReason() != PauseReason.AUTO_MARKET_CLOSED) {
                continue;
            }
            strategyService.autoResumeFromMarketClose(strategy.id(), "Strategy auto-resumed because market is open");
        }
    }

    private StrategyExecutionEvent event(String strategyId, StrategyEventType type, String message, String metadataJson) {
        return new StrategyExecutionEvent(UUID.randomUUID().toString(), strategyId, type, message, metadataJson, Instant.now());
    }

    public interface PollListener {
        PollListener NOOP = new PollListener() {};

        default void onPollStarted(String strategyId) {}
        default void onPollCompleted(String strategyId) {}
        default void onPollFailed(String strategyId) {}
    }
}
