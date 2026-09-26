package com.neuralarc.model;

import java.time.Instant;
import java.util.Objects;

/** One recorded AI agent tool call: what was asked, whether it worked, and how long it took. */
public record AgentToolCall(
        String id,
        String runId,
        Instant calledAt,
        String tool,
        boolean ok,
        String argumentsJson,
        String error,
        long elapsedMillis
) {
    public AgentToolCall {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(calledAt, "calledAt");
        tool = tool == null ? "" : tool;
        argumentsJson = argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson;
        elapsedMillis = Math.max(0, elapsedMillis);
    }
}
