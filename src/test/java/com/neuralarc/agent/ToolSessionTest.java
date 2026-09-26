package com.neuralarc.agent;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolSessionTest {
    private final List<String> audited = new ArrayList<>();
    private final ToolSession.Audit audit = (result, arguments, elapsed) ->
            audited.add(result.tool() + (result.ok() ? " ok" : " failed"));

    @Test
    void aWorkingToolCallComesBackAsContent() {
        ToolSession session = session(new StubTool("echo", args -> new JSONObject().put("said", "hello")));

        ToolResult result = session.call("echo", new JSONObject());

        assertTrue(result.ok());
        assertEquals("hello", result.content().getString("said"));
        assertEquals(List.of("echo ok"), audited);
    }

    @Test
    void aToolThatThrowsEndsTheCallNotTheRun() {
        ToolSession session = session(new StubTool("boom", args -> {
            throw new IllegalStateException("broker unreachable");
        }));

        ToolResult result = session.call("boom", new JSONObject());

        assertFalse(result.ok());
        assertTrue(result.error().contains("broker unreachable"));
        assertEquals(List.of("boom failed"), audited);
    }

    @Test
    void aHallucinatedToolNameIsAnsweredWithTheRealOnes() {
        ToolSession session = session(new StubTool("latest_price", args -> new JSONObject()));

        ToolResult result = session.call("place_order", new JSONObject());

        assertFalse(result.ok());
        assertTrue(result.error().contains("no tool named 'place_order'"));
        assertTrue(result.error().contains("latest_price"), "the model needs to see what it may call instead");
    }

    @Test
    void theBudgetStopsARunawayLoopAndSaysSo() {
        ToolSession session = new ToolSession(
                ToolRegistry.readOnly(List.of(new StubTool("echo", args -> new JSONObject()))), 2, audit);

        assertTrue(session.call("echo", new JSONObject()).ok());
        assertTrue(session.call("echo", new JSONObject()).ok());
        ToolResult third = session.call("echo", new JSONObject());

        assertFalse(third.ok());
        assertTrue(third.error().contains("budget"));
        assertEquals(0, session.callsRemaining());
    }

    @Test
    void aFailedCallStillSpendsBudgetSoRetryLoopsCannotRunForever() {
        ToolSession session = new ToolSession(
                ToolRegistry.readOnly(List.of(new StubTool("boom", args -> {
                    throw new IllegalStateException("nope");
                }))), 3, audit);

        session.call("boom", new JSONObject());

        assertEquals(2, session.callsRemaining());
    }

    @Test
    void theAuditSeesEveryCall() {
        List<Duration> elapsed = new ArrayList<>();
        ToolSession session = new ToolSession(
                ToolRegistry.readOnly(List.of(new StubTool("echo", args -> new JSONObject()))), 5,
                (result, arguments, took) -> elapsed.add(took));

        session.call("echo", new JSONObject().put("symbol", "AAPL"));
        session.call("missing", new JSONObject());

        assertEquals(2, elapsed.size());
    }

    private ToolSession session(AgentTool tool) {
        return new ToolSession(ToolRegistry.readOnly(List.of(tool)), 10, audit);
    }

    /** A tool whose body is supplied per test. */
    private record StubTool(String toolName, Body body) implements AgentTool {
        interface Body {
            JSONObject run(ToolArguments arguments) throws Exception;
        }

        @Override public String name() { return toolName; }
        @Override public String description() { return "stub"; }
        @Override public ToolParameters parameters() { return ToolParameters.none(); }
        @Override public JSONObject call(ToolArguments arguments) throws Exception { return body.run(arguments); }
    }
}
