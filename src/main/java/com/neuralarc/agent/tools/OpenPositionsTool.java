package com.neuralarc.agent.tools;

import com.neuralarc.agent.AgentTool;
import com.neuralarc.agent.ToolArguments;
import com.neuralarc.agent.ToolParameters;
import com.neuralarc.model.StrategyMode;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.List;

/**
 * What the operator currently holds, from cached snapshots — no broker call.
 *
 * <p>This is the context that keeps an agent's advice grounded: without it, a model happily proposes
 * a stock the operator already holds three times over.
 */
public final class OpenPositionsTool implements AgentTool {
    private static final List<String> MODES = List.of("PAPER", "LIVE", "ALL");

    private final PositionSnapshots snapshots;

    public OpenPositionsTool(PositionSnapshots snapshots) {
        this.snapshots = snapshots;
    }

    @Override
    public String name() {
        return "open_positions";
    }

    @Override
    public String description() {
        return "The operator's currently open positions with shares, average cost and unrealized P&L. Read this"
                + " before proposing anything: never propose a stock that is already held unless asked to add to it.";
    }

    @Override
    public ToolParameters parameters() {
        return ToolParameters.builder()
                .optionalChoice("mode", "Which book to read: PAPER, LIVE or ALL (default ALL).", MODES)
                .optional("symbol", ToolParameters.Type.STRING, "Limit to one ticker.")
                .build();
    }

    @Override
    public JSONObject call(ToolArguments arguments) throws Exception {
        String mode = arguments.choice("mode", MODES, "ALL");
        String symbol = arguments.raw().has("symbol") ? arguments.symbol("symbol") : null;
        JSONArray rows = new JSONArray();
        for (PositionSnapshots.PositionView position : snapshots.current()) {
            if (!"ALL".equals(mode) && position.mode() != StrategyMode.valueOf(mode)) {
                continue;
            }
            if (symbol != null && !symbol.equals(position.symbol())) {
                continue;
            }
            rows.put(new JSONObject()
                    .put("symbol", position.symbol())
                    .put("mode", String.valueOf(position.mode()))
                    .put("workspace", position.workspaceName())
                    .put("status", position.status())
                    .put("shares", position.shares())
                    .put("average_cost", plain(position.averageCost()))
                    .put("last_price", plain(position.lastPrice()))
                    .put("unrealized_pnl", plain(position.unrealizedPnl())));
        }
        return new JSONObject().put("mode", mode).put("count", rows.length()).put("positions", rows);
    }

    private static String plain(BigDecimal value) {
        return value == null ? "0" : value.toPlainString();
    }
}
