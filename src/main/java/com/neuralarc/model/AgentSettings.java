package com.neuralarc.model;

/**
 * Operator settings for the read-only AI analyst agent.
 *
 * <p>Both limits are cost controls with teeth: a run stops at {@code maxTurns} exchanges or
 * {@code maxToolCalls} tool calls, whichever comes first. They are clamped here rather than trusted
 * from the settings screen, because a stray zero or a pasted large number would otherwise become
 * either a useless agent or an expensive one.
 */
public record AgentSettings(boolean enabled, String apiKey, String model, int maxTurns, int maxToolCalls) {
    public static final String DEFAULT_MODEL = "claude-opus-5";
    public static final int DEFAULT_MAX_TURNS = 8;
    public static final int DEFAULT_MAX_TOOL_CALLS = 25;
    public static final int MAX_TURNS_CEILING = 20;
    public static final int MAX_TOOL_CALLS_CEILING = 100;

    public AgentSettings {
        apiKey = apiKey == null ? "" : apiKey.trim();
        model = model == null || model.isBlank() ? DEFAULT_MODEL : model.trim();
        maxTurns = clamp(maxTurns, DEFAULT_MAX_TURNS, MAX_TURNS_CEILING);
        maxToolCalls = clamp(maxToolCalls, DEFAULT_MAX_TOOL_CALLS, MAX_TOOL_CALLS_CEILING);
    }

    public static AgentSettings defaults() {
        return new AgentSettings(false, "", DEFAULT_MODEL, DEFAULT_MAX_TURNS, DEFAULT_MAX_TOOL_CALLS);
    }

    /** True when a run can actually start: switched on, with a key to call. */
    public boolean ready() {
        return enabled && !apiKey.isBlank();
    }

    private static int clamp(int value, int fallback, int ceiling) {
        return value <= 0 ? fallback : Math.min(value, ceiling);
    }
}
