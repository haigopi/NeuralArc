package com.neuralarc.agent;

import com.neuralarc.db.SqliteAgentToolCallRepository;
import com.neuralarc.model.AgentToolCall;
import org.json.JSONObject;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Records every tool call of one agent run in the database.
 *
 * <p>Without this an agent run is unaccountable: a recommendation arrives with no way to see which
 * prices it read, which tools failed, or how long it spent. A failed write is logged and swallowed —
 * losing an audit row must not take down the run that produced it, and the run's own result is still
 * returned to the operator.
 */
public final class AgentRunAudit implements ToolSession.Audit {
    private static final Logger LOG = Logger.getLogger(AgentRunAudit.class.getName());

    private final SqliteAgentToolCallRepository repository;
    private final String runId;
    private final Clock clock;

    public AgentRunAudit(SqliteAgentToolCallRepository repository, String runId, Clock clock) {
        this.repository = repository;
        this.runId = runId == null || runId.isBlank() ? UUID.randomUUID().toString() : runId;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public String runId() {
        return runId;
    }

    @Override
    public void toolCalled(ToolResult result, JSONObject arguments, Duration elapsed) {
        try {
            repository.save(new AgentToolCall(
                    UUID.randomUUID().toString(),
                    runId,
                    clock.instant(),
                    result.tool(),
                    result.ok(),
                    arguments == null ? "{}" : arguments.toString(),
                    result.error(),
                    elapsed == null ? 0L : elapsed.toMillis()));
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "Could not record agent tool call " + result.tool(), ex);
        }
    }
}
