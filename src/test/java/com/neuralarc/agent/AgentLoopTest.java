package com.neuralarc.agent;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLoopTest {
    private final List<String> calledTools = new ArrayList<>();

    @Test
    void theLoopRunsTheRequestedToolsAndFeedsTheResultsBack() throws Exception {
        ScriptedModel model = new ScriptedModel(
                turnAsking("latest_price", "AAPL"),
                AgentTurn.answer("AAPL is at 184.00 and holding its averages."));

        AgentLoop.Result result = loop(model, 10, 10).run("What is AAPL doing?");

        assertEquals(List.of("latest_price"), calledTools);
        assertEquals("AAPL is at 184.00 and holding its averages.", result.text());
        assertEquals(2, result.turns());
        assertEquals(1, result.toolCalls());
        assertTrue(result.completed());
        JSONObject fedBack = model.received.get(0).result().content();
        assertEquals("184.00", fedBack.getString("price"), "the tool's answer has to reach the model");
    }

    @Test
    void allOfATurnsToolResultsGoBackTogether() throws Exception {
        AgentTurn parallel = new AgentTurn("", List.of(
                new AgentTurn.ToolCall("t1", "latest_price", new JSONObject().put("symbol", "AAPL")),
                new AgentTurn.ToolCall("t2", "latest_price", new JSONObject().put("symbol", "NVDA"))), false);
        ScriptedModel model = new ScriptedModel(parallel, AgentTurn.answer("Both look fine."));

        AgentLoop.Result result = loop(model, 10, 10).run("Compare them");

        assertEquals(2, result.toolCalls());
        assertEquals(1, model.responses, "two results, one response — not one response each");
        assertEquals(List.of("t1", "t2"), model.received.stream().map(AgentModel.ToolOutcome::toolUseId).toList());
    }

    @Test
    void aModelThatKeepsCallingToolsIsStoppedByTheTurnLimit() throws Exception {
        ScriptedModel model = new ScriptedModel(
                turnAsking("latest_price", "AAPL"),
                turnAsking("latest_price", "AAPL"),
                turnAsking("latest_price", "AAPL"),
                turnAsking("latest_price", "AAPL"));

        AgentLoop.Result result = loop(model, 3, 50).run("Loop forever");

        assertFalse(result.completed(), "a run cut short must not look like an answer");
        assertEquals(3, result.turns());
    }

    @Test
    void theToolBudgetStopsTheRunEvenWhenTurnsRemain() throws Exception {
        ScriptedModel model = new ScriptedModel(
                turnAsking("latest_price", "AAPL"),
                turnAsking("latest_price", "AAPL"),
                AgentTurn.answer("Enough."));

        AgentLoop.Result result = loop(model, 10, 1).run("Check twice");

        assertTrue(result.completed());
        assertEquals("Enough.", result.text());
        assertFalse(model.received.get(1).result().ok(), "the second call is refused by the budget");
        assertTrue(model.received.get(1).result().error().contains("budget"));
    }

    @Test
    void aRefusedTurnEndsTheRunAndIsReportedAsSuch() throws Exception {
        AgentLoop.Result result = loop(new ScriptedModel(AgentTurn.refusal("I can't help with that.")), 10, 10)
                .run("Do something disallowed");

        assertTrue(result.refused());
        assertFalse(result.completed());
        assertEquals(0, result.toolCalls());
    }

    private AgentLoop loop(AgentModel model, int maxTurns, int toolBudget) {
        ToolRegistry registry = ToolRegistry.readOnly(List.of(new PriceTool()));
        return new AgentLoop(model, new ToolSession(registry, toolBudget, ToolSession.Audit.NONE), maxTurns);
    }

    private static AgentTurn turnAsking(String tool, String symbol) {
        return new AgentTurn("", List.of(new AgentTurn.ToolCall("t" + symbol, tool,
                new JSONObject().put("symbol", symbol))), false);
    }

    /** Replies from a fixed script, recording what it was told. */
    private static final class ScriptedModel implements AgentModel {
        private final Deque<AgentTurn> script;
        private final List<ToolOutcome> received = new ArrayList<>();
        private int responses;

        private ScriptedModel(AgentTurn... turns) {
            this.script = new ArrayDeque<>(List.of(turns));
        }

        @Override
        public AgentTurn start(String userPrompt) {
            return script.poll();
        }

        @Override
        public AgentTurn respond(List<ToolOutcome> results) {
            received.addAll(results);
            responses++;
            AgentTurn next = script.poll();
            return next == null ? AgentTurn.answer("") : next;
        }
    }

    private final class PriceTool implements AgentTool {
        @Override public String name() { return "latest_price"; }
        @Override public String description() { return "price"; }

        @Override
        public ToolParameters parameters() {
            return ToolParameters.builder().required("symbol", ToolParameters.Type.STRING, "Ticker.").build();
        }

        @Override
        public JSONObject call(ToolArguments arguments) throws Exception {
            calledTools.add(name());
            return new JSONObject().put("symbol", arguments.symbol("symbol")).put("price", "184.00");
        }
    }
}
