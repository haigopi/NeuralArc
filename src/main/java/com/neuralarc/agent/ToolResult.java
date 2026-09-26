package com.neuralarc.agent;

import org.json.JSONObject;

/**
 * What one tool call produced, in the shape that goes back to the model and into the audit log.
 *
 * <p>A failure is a result, not an exception: the model is told what went wrong and gets to try
 * again, and the run keeps its shape for the operator reading it afterwards.
 */
public record ToolResult(String tool, boolean ok, JSONObject content, String error) {
    public static ToolResult ok(String tool, JSONObject content) {
        return new ToolResult(tool, true, content == null ? new JSONObject() : content, null);
    }

    public static ToolResult failed(String tool, String error) {
        return new ToolResult(tool, false, new JSONObject(), error == null ? "Tool call failed." : error);
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject().put("tool", tool).put("ok", ok);
        return ok ? json.put("content", content) : json.put("error", error);
    }
}
