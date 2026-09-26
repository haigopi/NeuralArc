package com.neuralarc.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * An agent run backed by Claude, through the official Anthropic Java SDK.
 *
 * <p>Holds the conversation for one run: the operator's question, each of Claude's turns, and the
 * tool results sent back. All of a turn's tool results go back in a single user message, which is
 * what the API expects — splitting them teaches the model to stop asking for tools in parallel.
 *
 * <p>The tools offered are exactly whatever {@link ToolRegistry} holds, so a read-only registry
 * produces a read-only agent: the model is never told a trading tool exists, and could not reach one
 * if it invented the name. Blocks HTTP-long; call it from a background executor, never the EDT.
 */
public final class AnthropicAgentModel implements AgentModel {
    public static final String DEFAULT_MODEL = "claude-opus-5";
    /** Comfortably above a long analysis, below the SDK's non-streaming timeout. */
    public static final long DEFAULT_MAX_TOKENS = 16_000L;

    private final AnthropicClient client;
    private final ToolRegistry registry;
    private final List<Tool> tools;
    private final String systemPrompt;
    private final String model;
    private final long maxTokens;
    private final List<MessageParam> conversation = new ArrayList<>();

    public AnthropicAgentModel(AnthropicClient client, ToolRegistry registry, String systemPrompt,
                               String model, long maxTokens) {
        this.client = client;
        this.registry = registry;
        this.tools = declarations(registry);
        this.systemPrompt = systemPrompt == null ? "" : systemPrompt;
        this.model = model == null || model.isBlank() ? DEFAULT_MODEL : model;
        this.maxTokens = maxTokens <= 0 ? DEFAULT_MAX_TOKENS : maxTokens;
    }

    /** Builds a client from the operator's stored key. The key never leaves this process. */
    public static AnthropicAgentModel withApiKey(String apiKey, ToolRegistry registry, String systemPrompt) {
        return withApiKey(apiKey, registry, systemPrompt, DEFAULT_MODEL);
    }

    public static AnthropicAgentModel withApiKey(String apiKey, ToolRegistry registry, String systemPrompt, String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("An Anthropic API key is required to run the agent.");
        }
        return new AnthropicAgentModel(AnthropicOkHttpClient.builder().apiKey(apiKey).build(),
                registry, systemPrompt, model, DEFAULT_MAX_TOKENS);
    }

    @Override
    public AgentTurn start(String userPrompt) {
        conversation.add(MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content(userPrompt == null ? "" : userPrompt)
                .build());
        return send();
    }

    @Override
    public AgentTurn respond(List<ToolOutcome> results) {
        List<ContentBlockParam> blocks = new ArrayList<>();
        for (ToolOutcome outcome : results) {
            ToolResult result = outcome.result();
            blocks.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                    .toolUseId(outcome.toolUseId())
                    .content(result.ok() ? result.content().toString() : result.error())
                    .isError(!result.ok())
                    .build()));
        }
        conversation.add(MessageParam.builder()
                .role(MessageParam.Role.USER)
                .contentOfBlockParams(blocks)
                .build());
        return send();
    }

    private AgentTurn send() {
        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens)
                .thinking(ThinkingConfigAdaptive.builder().build())
                .messages(List.copyOf(conversation));
        if (!systemPrompt.isBlank()) {
            params.system(systemPrompt);
        }
        for (Tool tool : tools) {
            params.addTool(tool);
        }
        Message response = client.messages().create(params.build());
        conversation.add(response.toParam());
        return toTurn(response);
    }

    private static AgentTurn toTurn(Message response) {
        StringBuilder text = new StringBuilder();
        List<AgentTurn.ToolCall> calls = new ArrayList<>();
        for (ContentBlock block : response.content()) {
            block.text().ifPresent(textBlock -> text.append(textBlock.text()));
            block.toolUse().ifPresent(toolUse -> calls.add(toolCall(toolUse)));
        }
        boolean refused = response.stopReason()
                .map(reason -> reason.value() == StopReason.Value.REFUSAL)
                .orElse(false);
        return refused ? AgentTurn.refusal(text.toString()) : new AgentTurn(text.toString(), calls, false);
    }

    private static AgentTurn.ToolCall toolCall(ToolUseBlock toolUse) {
        JSONObject input;
        try {
            Map<?, ?> converted = toolUse._input().convert(Map.class);
            input = converted == null ? new JSONObject() : new JSONObject(converted);
        } catch (RuntimeException ex) {
            // A malformed input is the tool's problem to report, not a reason to end the run:
            // ToolArguments will refuse it and the model gets a correctable error back.
            input = new JSONObject();
        }
        return new AgentTurn.ToolCall(toolUse.id(), toolUse.name(), input);
    }

    /** Turns our own tool schemas into the SDK's tool definitions, once per run. */
    static List<Tool> declarations(ToolRegistry registry) {
        List<Tool> declared = new ArrayList<>();
        for (AgentTool tool : registry.all()) {
            JSONObject schema = tool.parameters().jsonSchema();
            JSONObject properties = schema.getJSONObject("properties");
            Tool.InputSchema.Properties.Builder built = Tool.InputSchema.Properties.builder();
            for (String property : properties.keySet()) {
                built.putAdditionalProperty(property, JsonValue.from(properties.getJSONObject(property).toMap()));
            }
            List<String> required = new ArrayList<>();
            JSONArray requiredNames = schema.getJSONArray("required");
            for (int i = 0; i < requiredNames.length(); i++) {
                required.add(requiredNames.getString(i));
            }
            declared.add(Tool.builder()
                    .name(tool.name())
                    .description(tool.description())
                    .inputSchema(Tool.InputSchema.builder()
                            .properties(built.build())
                            .required(required)
                            .build())
                    .build());
        }
        return declared;
    }

    ToolRegistry registry() {
        return registry;
    }
}
