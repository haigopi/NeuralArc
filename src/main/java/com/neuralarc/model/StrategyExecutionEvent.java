package com.neuralarc.model;

import java.time.Instant;
import java.util.Objects;

public record StrategyExecutionEvent(
        String id,
        String strategyId,
        StrategyEventType eventType,
        String message,
        String metadataJson,
        Instant createdAt
) {
    public StrategyExecutionEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(strategyId, "strategyId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(createdAt, "createdAt");
        message = message == null ? "" : message;
        metadataJson = metadataJson == null ? "{}" : metadataJson;
    }
}

