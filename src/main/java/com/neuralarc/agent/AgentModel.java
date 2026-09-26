package com.neuralarc.agent;

import java.util.List;

/**
 * The model behind an agent run, as the loop sees it.
 *
 * <p>An interface, so {@link AgentLoop} can be tested without a network call or an API key, and so a
 * second provider can be added without touching the loop. Implementations are stateful for the
 * length of one run: they own the conversation, because every provider stores it differently.
 */
public interface AgentModel {
    /** Opens the run with the operator's question. */
    AgentTurn start(String userPrompt) throws Exception;

    /** Hands back the results of the tools the previous turn asked for. */
    AgentTurn respond(List<ToolOutcome> results) throws Exception;

    /** One finished tool call, paired with the model's own id for it. */
    record ToolOutcome(String toolUseId, ToolResult result) {
    }
}
