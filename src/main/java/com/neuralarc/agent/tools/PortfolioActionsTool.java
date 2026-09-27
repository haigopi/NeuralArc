package com.neuralarc.agent.tools;

import com.neuralarc.agent.AgentTool;
import com.neuralarc.agent.ToolArgumentException;
import com.neuralarc.agent.ToolArguments;
import com.neuralarc.agent.ToolParameters;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * Lets the analyst read the Portfolio Actions menu and see what each action would do — never run one.
 *
 * <p>Without an action name it lists what currently applies, so the answer can end with the action
 * the operator should reach for instead of a description of a problem. With one it returns that
 * action's full match list, which is how "cancel the staged buys" turns into "these seven".
 */
public final class PortfolioActionsTool implements AgentTool {
    private static final java.util.logging.Logger LOGGER =
            java.util.logging.Logger.getLogger(PortfolioActionsTool.class.getName());

    /** Enough symbols to be useful in a list; the single-action view returns them all. */
    static final int SYMBOL_PREVIEW_LIMIT = 8;

    private final PortfolioActionCatalog catalog;

    public PortfolioActionsTool(PortfolioActionCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public String name() {
        return "portfolio_actions";
    }

    @Override
    public String description() {
        return "What the operator's Portfolio Actions menu can do, and how many of their positions each action would"
                + " act on right now. Call it without arguments to see what currently applies, or with an action name"
                + " for the exact rows. This only looks: nothing is placed, cancelled or changed, and the operator"
                + " still has to run the action themselves.";
    }

    @Override
    public ToolParameters parameters() {
        return ToolParameters.builder()
                .optional("action", ToolParameters.Type.STRING,
                        "One action's name, e.g. \"Cancel All Stop Losses\". Omit to list them all.")
                .optional("include_empty", ToolParameters.Type.BOOLEAN,
                        "Include actions that match nothing right now (default false).")
                .build();
    }

    @Override
    public JSONObject call(ToolArguments arguments) throws Exception {
        String requested = arguments.raw().optString("action", "").trim();
        LOGGER.info(() -> "[AGENT][TOOL][portfolio_actions] " + (requested.isEmpty()
                ? "listing what applies now" : "previewing \"" + requested + "\""));
        if (!requested.isEmpty()) {
            PortfolioActionCatalog.ActionPreview preview = catalog.preview(requested)
                    .orElseThrow(() -> new ToolArgumentException("There is no Portfolio Action called '" + requested
                            + "'. Call this tool with no arguments to see the ones that exist."));
            return one(preview, Integer.MAX_VALUE);
        }
        boolean includeEmpty = arguments.bool("include_empty", false);
        JSONArray actions = new JSONArray();
        int skipped = 0;
        for (PortfolioActionCatalog.ActionPreview preview : catalog.previewAll()) {
            if (preview.matchCount() == 0 && !includeEmpty) {
                skipped++;
                continue;
            }
            actions.put(one(preview, SYMBOL_PREVIEW_LIMIT));
        }
        JSONObject result = new JSONObject()
                .put("actions", actions)
                .put("count", actions.length())
                .put("note", "Reading only. The operator runs these from the Portfolio Actions menu.");
        if (skipped > 0) {
            result.put("not_applicable_now", skipped);
        }
        return result;
    }

    private static JSONObject one(PortfolioActionCatalog.ActionPreview preview, int symbolLimit) {
        List<String> symbols = preview.symbols();
        JSONObject json = new JSONObject()
                .put("action", preview.name())
                .put("does", preview.description())
                .put("would_act_on", preview.matchCount());
        if (!symbols.isEmpty()) {
            json.put("symbols", new JSONArray(symbols.subList(0, Math.min(symbols.size(), symbolLimit))));
            if (symbols.size() > symbolLimit) {
                json.put("symbols_truncated", symbols.size() - symbolLimit);
            }
        } else if (preview.emptyReason() != null && !preview.emptyReason().isBlank()) {
            json.put("why_nothing_matches", preview.emptyReason());
        }
        return json;
    }
}
