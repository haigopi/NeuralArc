package com.neuralarc.agent;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolRegistryTest {
    @Test
    void aReadOnlyRegistryRefusesATradingTool() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> ToolRegistry.readOnly(List.of(new FixedTool("place_order", AgentTool.Effect.TRADING))));

        assertTrue(thrown.getMessage().contains("place_order"));
    }

    @Test
    void aReadOnlyRegistryAlsoRefusesAToolThatWritesProposals() {
        assertThrows(IllegalArgumentException.class,
                () -> ToolRegistry.readOnly(List.of(new FixedTool("propose_strategy", AgentTool.Effect.PROPOSAL))));
    }

    @Test
    void aProposingRegistryTakesProposalsButStillNotTrades() {
        ToolRegistry registry = ToolRegistry.proposing(List.of(
                new FixedTool("auto_analyze", AgentTool.Effect.READ_ONLY),
                new FixedTool("propose_strategy", AgentTool.Effect.PROPOSAL)));

        assertEquals(2, registry.all().size());
        assertThrows(IllegalArgumentException.class,
                () -> ToolRegistry.proposing(List.of(new FixedTool("sell_all", AgentTool.Effect.TRADING))));
    }

    @Test
    void twoToolsCannotShareAName() {
        assertThrows(IllegalArgumentException.class, () -> ToolRegistry.readOnly(List.of(
                new FixedTool("latest_price", AgentTool.Effect.READ_ONLY),
                new FixedTool("latest_price", AgentTool.Effect.READ_ONLY))));
    }

    @Test
    void definitionsCarryTheSchemaTheModelNeeds() {
        ToolRegistry registry = ToolRegistry.readOnly(List.of(new FixedTool("daily_bars", AgentTool.Effect.READ_ONLY)));

        JSONObject definition = registry.definitions().getJSONObject(0);

        assertEquals("daily_bars", definition.getString("name"));
        JSONObject schema = definition.getJSONObject("input_schema");
        assertEquals("object", schema.getString("type"));
        assertEquals("string", schema.getJSONObject("properties").getJSONObject("symbol").getString("type"));
        assertEquals("symbol", schema.getJSONArray("required").getString(0));
        assertEquals(1, schema.getJSONArray("required").length(), "optional arguments must not be required");
    }

    private record FixedTool(String toolName, Effect toolEffect) implements AgentTool {
        @Override public String name() { return toolName; }
        @Override public String description() { return "fixture"; }
        @Override public Effect effect() { return toolEffect; }

        @Override
        public ToolParameters parameters() {
            return ToolParameters.builder()
                    .required("symbol", ToolParameters.Type.STRING, "Ticker.")
                    .optional("lookback_days", ToolParameters.Type.INTEGER, "Days back.")
                    .build();
        }

        @Override public JSONObject call(ToolArguments arguments) { return new JSONObject(); }
    }
}
