package com.neuralarc.agent;

import org.json.JSONObject;

import java.time.Duration;
import java.time.Instant;

/**
 * One agent run's use of the tools: dispatch, a hard call budget, and an audit hook.
 *
 * <p>A model that loops — re-reading the same quote, retrying a failing tool — is the normal failure
 * mode of an agent, and the cost is real money in tokens and real load on the broker's rate limit.
 * The budget is counted here, where it cannot be argued with, and a used-up budget is reported to the
 * model as a result so it stops and summarizes rather than being cut off mid-thought.
 *
 * <p>Not thread-safe by design: one session belongs to one run, on one background thread.
 */
public final class ToolSession {
    /** Notified after every call, successful or not. Wire it to the strategy event store. */
    public interface Audit {
        void toolCalled(ToolResult result, JSONObject arguments, Duration elapsed);

        Audit NONE = (result, arguments, elapsed) -> {
        };
    }

    private final ToolRegistry registry;
    private final Audit audit;
    private final int maxCalls;
    private int callCount;

    public ToolSession(ToolRegistry registry, int maxCalls, Audit audit) {
        this.registry = registry;
        this.maxCalls = Math.max(1, maxCalls);
        this.audit = audit == null ? Audit.NONE : audit;
    }

    public int callsRemaining() {
        return Math.max(0, maxCalls - callCount);
    }

    /** Runs one model-requested tool call. Never throws: every outcome comes back as a result. */
    public ToolResult call(String toolName, JSONObject arguments) {
        if (callCount >= maxCalls) {
            return report(ToolResult.failed(toolName, "Tool call budget of " + maxCalls
                    + " is used up. Stop calling tools and answer with what you have."), arguments, Duration.ZERO);
        }
        callCount++;
        AgentTool tool = registry.find(toolName).orElse(null);
        if (tool == null) {
            return report(ToolResult.failed(toolName, "There is no tool named '" + toolName + "'. Available tools: "
                    + String.join(", ", registry.all().stream().map(AgentTool::name).toList()) + "."), arguments, Duration.ZERO);
        }
        Instant start = Instant.now();
        try {
            JSONObject content = tool.call(new ToolArguments(arguments));
            return report(ToolResult.ok(tool.name(), content), arguments, Duration.between(start, Instant.now()));
        } catch (ToolArgumentException ex) {
            return report(ToolResult.failed(tool.name(), ex.getMessage()), arguments, Duration.between(start, Instant.now()));
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            return report(ToolResult.failed(tool.name(), tool.name() + " failed: " + message), arguments,
                    Duration.between(start, Instant.now()));
        }
    }

    private ToolResult report(ToolResult result, JSONObject arguments, Duration elapsed) {
        audit.toolCalled(result, arguments == null ? new JSONObject() : arguments, elapsed);
        return result;
    }
}
