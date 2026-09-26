package com.neuralarc.agent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The set of tools one agent may use, and the definitions handed to the model.
 *
 * <p>{@link #readOnly} refuses anything that could place an order or write a strategy, so a
 * research agent cannot acquire a trading capability by a later wiring mistake — the constraint is
 * enforced here rather than trusted to the prompt.
 */
public final class ToolRegistry {
    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    private ToolRegistry(Collection<AgentTool> tools, AgentTool.Effect highestAllowedEffect) {
        for (AgentTool tool : tools) {
            if (tool.effect().ordinal() > highestAllowedEffect.ordinal()) {
                throw new IllegalArgumentException("Tool " + tool.name() + " has effect " + tool.effect()
                        + ", which this registry does not allow (" + highestAllowedEffect + ").");
            }
            if (this.tools.put(tool.name(), tool) != null) {
                throw new IllegalArgumentException("Two tools are named " + tool.name() + ".");
            }
        }
    }

    /** A registry that can only read. Nothing in it can move money or write a strategy. */
    public static ToolRegistry readOnly(Collection<AgentTool> tools) {
        return new ToolRegistry(tools, AgentTool.Effect.READ_ONLY);
    }

    /** Read-only tools plus ones that write proposals for a human to review; still never trades. */
    public static ToolRegistry proposing(Collection<AgentTool> tools) {
        return new ToolRegistry(tools, AgentTool.Effect.PROPOSAL);
    }

    public Optional<AgentTool> find(String name) {
        return Optional.ofNullable(tools.get(name == null ? "" : name.trim()));
    }

    public List<AgentTool> all() {
        return List.copyOf(tools.values());
    }

    /**
     * The tool list for the model, each as {@code {name, description, input_schema}}. Providers wrap
     * this in whatever envelope they expect.
     */
    public JSONArray definitions() {
        JSONArray definitions = new JSONArray();
        for (AgentTool tool : tools.values()) {
            definitions.put(new JSONObject()
                    .put("name", tool.name())
                    .put("description", tool.description())
                    .put("input_schema", tool.parameters().jsonSchema()));
        }
        return definitions;
    }
}
