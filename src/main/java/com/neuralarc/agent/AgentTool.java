package com.neuralarc.agent;

import org.json.JSONObject;

/**
 * One capability an AI agent may invoke, wrapping a service this app already owns.
 *
 * <p>A tool is the only way a model reaches the rest of NeuralArc: it never sees a broker client, a
 * repository or the EDT. Each tool declares a machine-readable parameter schema, validates what the
 * model sent, and answers with JSON — so a wrong or hallucinated argument fails as a readable error
 * the model can correct, never as a malformed broker call.
 *
 * <p>Tools run on background executors, exactly like polling and broker I/O; calling one from the
 * Swing EDT would block painting on network latency.
 */
public interface AgentTool {
    /** How far this tool can reach. The registry uses it to keep read-only runs read-only. */
    enum Effect {
        /** Reads market data, positions or analysis. Cannot change anything. */
        READ_ONLY,
        /** Writes a strategy proposal a human must review and activate. */
        PROPOSAL,
        /** Places, changes or cancels broker orders. */
        TRADING
    }

    /** Stable snake_case identifier the model calls, e.g. {@code daily_bars}. */
    String name();

    /** What the tool does and when to reach for it; this is prompt text, so it must earn its tokens. */
    String description();

    ToolParameters parameters();

    default Effect effect() {
        return Effect.READ_ONLY;
    }

    /**
     * Runs the tool. Throwing is fine and expected: {@link ToolSession} turns any failure into an
     * error result for the model rather than letting it end the run.
     */
    JSONObject call(ToolArguments arguments) throws Exception;
}
