package com.neuralarc.service;

import com.neuralarc.api.AlpacaClient;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyEventType;
import com.neuralarc.model.StrategyExecutionEvent;
import com.neuralarc.model.StrategyStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StrategyPollingService {
    private static final Logger LOGGER = Logger.getLogger(StrategyPollingService.class.getName());

    private final StrategyRepository strategyRepository;
    private final StrategyExecutionEventRepository eventRepository;
    private final StrategyEngine strategyEngine;

    public StrategyPollingService(
            StrategyRepository strategyRepository,
            StrategyOrderRepository orderRepository,
            StrategyExecutionEventRepository eventRepository,
            AlpacaClient alpacaClient
    ) {
        this.strategyRepository = strategyRepository;
        this.eventRepository = eventRepository;
        StrategyEventBus eventBus = new StrategyEventBus();
        this.strategyEngine = new StrategyEngine(
                strategyRepository,
                orderRepository,
                new StrategyStateMachine(eventRepository, eventBus),
                alpacaClient
        );
    }

    public void pollDueStrategies() {
        Instant now = Instant.now();
        for (Strategy strategy : strategyRepository.findActive()) {
            if (shouldPoll(strategy, now)) {
                pollStrategy(strategy.id());
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
            strategyEngine.reconcile(strategy);
            eventRepository.save(event(strategy.id(), StrategyEventType.POLL_SUCCESS,
                    "Poll completed", "{\"strategyId\":\"" + strategy.id() + "\"}"));
        } catch (Exception ex) {
            strategy.setLastPolledAt(Instant.now());
            strategy.setLastError(ex.getMessage());
            strategyRepository.save(strategy);
            eventRepository.save(event(strategy.id(), StrategyEventType.POLL_ERROR, ex.getMessage(), "{}"));
            LOGGER.log(Level.WARNING, "Polling failed for strategy " + strategy.id(), ex);
        }
    }

    private boolean shouldPoll(Strategy strategy, Instant now) {
        if (strategy.lastPolledAt() == null) {
            return true;
        }
        long elapsedSeconds = Duration.between(strategy.lastPolledAt(), now).getSeconds();
        return elapsedSeconds >= Math.max(1, strategy.pollingIntervalSeconds());
    }

    private StrategyExecutionEvent event(String strategyId, StrategyEventType type, String message, String metadataJson) {
        return new StrategyExecutionEvent(UUID.randomUUID().toString(), strategyId, type, message, metadataJson, Instant.now());
    }
}
