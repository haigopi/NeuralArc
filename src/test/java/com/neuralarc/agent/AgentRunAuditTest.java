package com.neuralarc.agent;

import com.neuralarc.db.AppDatabase;
import com.neuralarc.db.SqliteAgentToolCallRepository;
import com.neuralarc.model.AgentToolCall;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRunAuditTest {
    @TempDir
    Path tempDir;

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-22T15:30:00Z"), ZoneOffset.UTC);

    @Test
    void everyToolCallOfARunIsRecordedInOrderWithItsArguments() throws Exception {
        SqliteAgentToolCallRepository repository = repository();
        ToolSession session = new ToolSession(
                ToolRegistry.readOnly(List.of(new StubTool())), 10, new AgentRunAudit(repository, "run-1", CLOCK));

        session.call("stub", new JSONObject().put("symbol", "AAPL"));
        session.call("stub", new JSONObject().put("symbol", "BOOM"));
        session.call("no_such_tool", new JSONObject());

        List<AgentToolCall> calls = repository.findByRun("run-1");
        assertEquals(3, calls.size());
        assertTrue(calls.get(0).ok());
        assertEquals("stub", calls.get(0).tool());
        assertEquals("{\"symbol\":\"AAPL\"}", calls.get(0).argumentsJson());
        assertEquals(CLOCK.instant(), calls.get(0).calledAt());
        assertFalse(calls.get(1).ok(), "a failing tool is recorded as a failure, not dropped");
        assertTrue(calls.get(1).error().contains("no data"));
        assertFalse(calls.get(2).ok());
        assertEquals("no_such_tool", calls.get(2).tool());
    }

    @Test
    void runsAreKeptApartSoOneAgentsTrailIsReadableOnItsOwn() {
        SqliteAgentToolCallRepository repository = repository();
        new AgentRunAudit(repository, "run-a", CLOCK).toolCalled(ToolResult.ok("stub", new JSONObject()), null, null);
        new AgentRunAudit(repository, "run-b", CLOCK).toolCalled(ToolResult.failed("stub", "nope"), null, null);

        assertEquals(1, repository.findByRun("run-a").size());
        assertEquals(1, repository.findByRun("run-b").size());
        assertTrue(repository.findByRun("run-c").isEmpty());
        assertEquals("{}", repository.findByRun("run-a").get(0).argumentsJson());
    }

    @Test
    void aRunCannotGrowTheDatabaseWithoutBound() {
        SqliteAgentToolCallRepository repository = repository();
        AgentRunAudit audit = new AgentRunAudit(repository, "run-loop", CLOCK);

        for (int i = 0; i < SqliteAgentToolCallRepository.MAX_CALLS_PER_RUN + 25; i++) {
            audit.toolCalled(ToolResult.ok("stub", new JSONObject().put("i", i)), null, null);
        }

        assertEquals(SqliteAgentToolCallRepository.MAX_CALLS_PER_RUN, repository.findByRun("run-loop").size());
    }

    @Test
    void anAuditFailureDoesNotTakeDownTheRun() {
        AgentRunAudit audit = new AgentRunAudit(new SqliteAgentToolCallRepository(null), "run-x", CLOCK);

        audit.toolCalled(ToolResult.ok("stub", new JSONObject()), new JSONObject(), null);
    }

    private SqliteAgentToolCallRepository repository() {
        return new SqliteAgentToolCallRepository(AppDatabase.open(tempDir.resolve("neuralarc.db")));
    }

    private static final class StubTool implements AgentTool {
        @Override public String name() { return "stub"; }
        @Override public String description() { return "stub"; }
        @Override public ToolParameters parameters() { return ToolParameters.none(); }

        @Override
        public JSONObject call(ToolArguments arguments) throws Exception {
            if ("BOOM".equals(arguments.raw().optString("symbol"))) {
                throw new IllegalStateException("no data");
            }
            return new JSONObject().put("ok", true);
        }
    }
}
