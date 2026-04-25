package com.neuralarc.service;

import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyEventType;
import com.neuralarc.model.StrategyExecutionEvent;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyStatus;

import java.time.Instant;
import java.util.UUID;

public class StrategyStateMachine {
    private final StrategyExecutionEventRepository eventRepository;
    private final StrategyEventBus eventBus;

    public StrategyStateMachine(StrategyExecutionEventRepository eventRepository, StrategyEventBus eventBus) {
        this.eventRepository = eventRepository;
        this.eventBus = eventBus;
    }

    public void transition(Strategy strategy, StrategyLifecycleState state, StrategyEventType type, String message, String metadataJson) {
        strategy.setCurrentState(state);
        strategy.setLastEvent(message);
        if (state == StrategyLifecycleState.PAUSED) {
            strategy.setStatus(StrategyStatus.PAUSED);
        } else if (state == StrategyLifecycleState.COMPLETED) {
            strategy.setStatus(StrategyStatus.COMPLETED);
        } else if (state == StrategyLifecycleState.STOPPED) {
            strategy.setStatus(StrategyStatus.STOPPED);
        } else if (state == StrategyLifecycleState.FAILED) {
            strategy.setStatus(StrategyStatus.FAILED);
        } else if (strategy.status() == StrategyStatus.CREATED || strategy.status() == StrategyStatus.PAUSED) {
            strategy.setStatus(StrategyStatus.ACTIVE);
        }
        StrategyExecutionEvent event = new StrategyExecutionEvent(
                UUID.randomUUID().toString(),
                strategy.id(),
                type,
                message,
                metadataJson,
                Instant.now()
        );
        eventRepository.save(event);
        eventBus.publish(event);
    }
}
