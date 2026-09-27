package com.neuralarc.agent.tools;

import com.neuralarc.agent.AgentTool;
import com.neuralarc.agent.ToolArgumentException;
import com.neuralarc.agent.ToolArguments;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioActionsToolTest {
    private final PortfolioActionsTool tool = new PortfolioActionsTool(new FakeCatalog());

    @Test
    void theListLeavesOutWhatCannotApplyRightNow() throws Exception {
        JSONObject result = tool.call(new ToolArguments(new JSONObject()));

        assertEquals(2, result.getInt("count"));
        assertEquals(1, result.getInt("not_applicable_now"));
        assertEquals("Cancel All Stop Losses", result.getJSONArray("actions").getJSONObject(0).getString("action"));
        assertTrue(result.getString("note").contains("Reading only"),
                "the model must not come away thinking it can run these");
    }

    @Test
    void anActionThatMatchesNothingCanStillBeListedOnPurpose() throws Exception {
        JSONObject result = tool.call(new ToolArguments(new JSONObject().put("include_empty", true)));

        assertEquals(3, result.getInt("count"));
        assertFalse(result.has("not_applicable_now"));
        JSONObject empty = result.getJSONArray("actions").getJSONObject(2);
        assertEquals(0, empty.getInt("would_act_on"));
        assertTrue(empty.getString("why_nothing_matches").contains("no expired"));
    }

    @Test
    void askingForOneActionReturnsEveryRowItWouldTouch() throws Exception {
        JSONObject result = tool.call(new ToolArguments(new JSONObject().put("action", "cancel all stop losses")));

        assertEquals("Cancel All Stop Losses", result.getString("action"));
        assertEquals(2, result.getInt("would_act_on"));
        assertEquals(List.of("AAPL", "NVDA"), result.getJSONArray("symbols").toList());
        assertFalse(result.has("symbols_truncated"));
    }

    @Test
    void theListTrimsLongSymbolRunsAndSaysHowManyItHeldBack() throws Exception {
        List<String> many = new ArrayList<>();
        for (int i = 0; i < PortfolioActionsTool.SYMBOL_PREVIEW_LIMIT + 5; i++) {
            many.add("SYM" + i);
        }
        PortfolioActionsTool wide = new PortfolioActionsTool(new PortfolioActionCatalog() {
            @Override
            public List<ActionPreview> previewAll() {
                return List.of(new ActionPreview("Resume All", "resumes", many.size(), many, ""));
            }

            @Override
            public Optional<ActionPreview> preview(String actionName) {
                return Optional.empty();
            }
        });

        JSONObject listed = wide.call(new ToolArguments(new JSONObject())).getJSONArray("actions").getJSONObject(0);

        assertEquals(PortfolioActionsTool.SYMBOL_PREVIEW_LIMIT, listed.getJSONArray("symbols").length());
        assertEquals(5, listed.getInt("symbols_truncated"));
    }

    @Test
    void anInventedActionIsRefusedWithARouteToTheRealOnes() {
        ToolArgumentException thrown = assertThrows(ToolArgumentException.class,
                () -> tool.call(new ToolArguments(new JSONObject().put("action", "liquidate everything"))));

        assertTrue(thrown.getMessage().contains("no Portfolio Action called 'liquidate everything'"));
        assertTrue(thrown.getMessage().contains("no arguments"));
    }

    @Test
    void theToolOnlyEverReads() {
        assertEquals(AgentTool.Effect.READ_ONLY, tool.effect());
        assertTrue(tool.description().contains("nothing is placed, cancelled or changed"));
    }

    private static final class FakeCatalog implements PortfolioActionCatalog {
        @Override
        public List<ActionPreview> previewAll() {
            return List.of(
                    new ActionPreview("Cancel All Stop Losses", "switches stop losses off", 2,
                            List.of("AAPL", "NVDA"), ""),
                    new ActionPreview("Resume All", "resumes paused strategies", 1, List.of("NIO"), ""),
                    new ActionPreview("Clean All Expired", "clears expired rows", 0, List.of(),
                            "There are no expired rows."));
        }

        @Override
        public Optional<ActionPreview> preview(String actionName) {
            return previewAll().stream()
                    .filter(preview -> preview.name().equalsIgnoreCase(actionName.trim()))
                    .findFirst();
        }
    }
}
