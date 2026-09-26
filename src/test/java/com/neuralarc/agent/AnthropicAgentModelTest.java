package com.neuralarc.agent;

import com.anthropic.models.messages.Tool;
import com.neuralarc.agent.tools.MarketSessionTool;
import com.neuralarc.service.MarketHoursService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the mapping from our tool schemas to the SDK's tool definitions — the part that decides
 * whether Claude sees the right arguments. Nothing here calls the API.
 */
class AnthropicAgentModelTest {
    @Test
    void ourToolSchemasBecomeSdkToolDefinitions() {
        ToolRegistry registry = ToolRegistry.readOnly(List.of(new MarketSessionTool(new MarketHoursService(), Clock.systemUTC())));

        List<Tool> declared = AnthropicAgentModel.declarations(registry);

        assertEquals(1, declared.size());
        Tool tool = declared.get(0);
        assertEquals("market_session", tool.name());
        assertTrue(tool.description().orElse("").contains("open"));
        Map<String, ?> properties = tool.inputSchema().properties().orElseThrow()._additionalProperties();
        assertTrue(properties.containsKey("extended_hours"));
        assertEquals(List.of(), tool.inputSchema().required().orElse(List.of()),
                "an all-optional tool must declare nothing required");
    }

    @Test
    void requiredArgumentsSurviveTheMapping() {
        ToolRegistry registry = ToolRegistry.readOnly(List.of(new TwoArgTool()));

        Tool tool = AnthropicAgentModel.declarations(registry).get(0);

        assertEquals(List.of("symbol"), tool.inputSchema().required().orElse(List.of()));
        assertEquals(2, tool.inputSchema().properties().orElseThrow()._additionalProperties().size());
    }

    @Test
    void aRunWithoutAKeyFailsClearlyRatherThanCallingTheApi() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> AnthropicAgentModel.withApiKey("  ", ToolRegistry.readOnly(List.of()), "system"));

        assertTrue(thrown.getMessage().contains("API key"));
    }

    private static final class TwoArgTool implements AgentTool {
        @Override public String name() { return "daily_bars"; }
        @Override public String description() { return "bars"; }

        @Override
        public ToolParameters parameters() {
            return ToolParameters.builder()
                    .required("symbol", ToolParameters.Type.STRING, "Ticker.")
                    .optional("lookback_days", ToolParameters.Type.INTEGER, "Days.")
                    .build();
        }

        @Override public org.json.JSONObject call(ToolArguments arguments) { return new org.json.JSONObject(); }
    }
}
