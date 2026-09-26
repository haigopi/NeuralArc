package com.neuralarc.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * The agent loop: ask the model, run the tools it asks for, hand back the results, repeat.
 *
 * <p>Three things end a run — the model answers, it refuses, or it exhausts a limit. Both limits are
 * hard: {@code maxTurns} here, and the tool-call budget inside {@link ToolSession}. An agent that
 * cannot finish within them returns what it has, with {@code completed} false, rather than running
 * on; an unbounded loop against a paid API and a rate-limited broker is the failure mode worth
 * engineering against, not the rare one.
 *
 * <p>Runs on a background executor. It blocks on network I/O and must never be called from the EDT.
 */
public final class AgentLoop {
    private static final Logger LOG = Logger.getLogger(AgentLoop.class.getName());

    /** What a finished run produced. {@code completed} is false when a limit stopped it. */
    public record Result(String text, int turns, int toolCalls, boolean completed, boolean refused) {
    }

    private final AgentModel model;
    private final ToolSession tools;
    private final int maxTurns;

    public AgentLoop(AgentModel model, ToolSession tools, int maxTurns) {
        this.model = model;
        this.tools = tools;
        this.maxTurns = Math.max(1, maxTurns);
    }

    public Result run(String userPrompt) throws Exception {
        AgentTurn turn = model.start(userPrompt);
        int turns = 1;
        int toolCalls = 0;
        while (turn.wantsTools()) {
            if (turns >= maxTurns) {
                LOG.warning(() -> "Agent run stopped at the " + maxTurns + "-turn limit.");
                return new Result(turn.text(), turns, toolCalls, false, false);
            }
            List<AgentModel.ToolOutcome> outcomes = new ArrayList<>();
            for (AgentTurn.ToolCall call : turn.toolCalls()) {
                outcomes.add(new AgentModel.ToolOutcome(call.id(), tools.call(call.name(), call.input())));
                toolCalls++;
            }
            turn = model.respond(outcomes);
            turns++;
        }
        return new Result(turn.text(), turns, toolCalls, !turn.refused(), turn.refused());
    }
}
