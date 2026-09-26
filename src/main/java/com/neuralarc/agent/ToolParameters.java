package com.neuralarc.agent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A tool's accepted arguments, as JSON Schema.
 *
 * <p>Provider-neutral on purpose: OpenAI, Anthropic and a local model each wrap tool definitions
 * differently, but all of them want this schema underneath, so the wrapping stays in the provider
 * and the tools stay portable.
 */
public record ToolParameters(List<Parameter> parameters) {
    public enum Type {
        STRING("string"),
        INTEGER("integer"),
        NUMBER("number"),
        BOOLEAN("boolean");

        private final String schemaName;

        Type(String schemaName) {
            this.schemaName = schemaName;
        }

        String schemaName() {
            return schemaName;
        }
    }

    public record Parameter(String name, Type type, String description, boolean required, List<String> allowedValues) {
        public Parameter {
            allowedValues = allowedValues == null ? List.of() : List.copyOf(allowedValues);
        }
    }

    public ToolParameters {
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
    }

    public static ToolParameters none() {
        return new ToolParameters(List.of());
    }

    public static Builder builder() {
        return new Builder();
    }

    public JSONObject jsonSchema() {
        JSONObject properties = new JSONObject();
        JSONArray required = new JSONArray();
        for (Parameter parameter : parameters) {
            JSONObject spec = new JSONObject()
                    .put("type", parameter.type().schemaName())
                    .put("description", parameter.description());
            if (!parameter.allowedValues().isEmpty()) {
                spec.put("enum", new JSONArray(parameter.allowedValues()));
            }
            properties.put(parameter.name(), spec);
            if (parameter.required()) {
                required.put(parameter.name());
            }
        }
        return new JSONObject()
                .put("type", "object")
                .put("properties", properties)
                .put("required", required);
    }

    public static final class Builder {
        private final List<Parameter> parameters = new ArrayList<>();

        public Builder required(String name, Type type, String description) {
            parameters.add(new Parameter(name, type, description, true, List.of()));
            return this;
        }

        public Builder optional(String name, Type type, String description) {
            parameters.add(new Parameter(name, type, description, false, List.of()));
            return this;
        }

        public Builder optionalChoice(String name, String description, List<String> allowedValues) {
            parameters.add(new Parameter(name, Type.STRING, description, false, allowedValues));
            return this;
        }

        public ToolParameters build() {
            return new ToolParameters(parameters);
        }
    }
}
