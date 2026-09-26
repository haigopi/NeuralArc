package com.neuralarc.agent;

import org.json.JSONObject;

import java.util.List;

/**
 * One reply from the model: whatever it said, plus any tools it wants run before it can continue.
 *
 * <p>A turn with tool calls is not an answer — the loop must run them and hand the results back.
 * A refused turn ends the run: the model declined, and retrying the same request will not help.
 */
public record AgentTurn(String text, List<ToolCall> toolCalls, boolean refused) {
    /** One tool the model asked for. {@code id} ties the result back to this request. */
    public record ToolCall(String id, String name, JSONObject input) {
        public ToolCall {
            input = input == null ? new JSONObject() : input;
        }
    }

    public AgentTurn {
        text = text == null ? "" : text;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static AgentTurn answer(String text) {
        return new AgentTurn(text, List.of(), false);
    }

    public static AgentTurn refusal(String text) {
        return new AgentTurn(text, List.of(), true);
    }

    public boolean wantsTools() {
        return !toolCalls.isEmpty();
    }
}
