package com.neuralarc.agent;

import com.neuralarc.agent.tools.AutoAnalyzeTool;
import com.neuralarc.agent.tools.DailyBarsTool;
import com.neuralarc.agent.tools.LatestNewsTool;
import com.neuralarc.agent.tools.LatestPriceTool;
import com.neuralarc.agent.tools.MarketSessionTool;
import com.neuralarc.agent.tools.OpenPositionsTool;
import com.neuralarc.agent.tools.PositionSnapshots;
import com.neuralarc.api.AlpacaMarketDataApi;
import com.neuralarc.api.TradingApi;
import com.neuralarc.service.AlpacaNewsClient;
import com.neuralarc.service.AutoAnalyzeService;
import com.neuralarc.service.MarketHoursService;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * The one place the app hands its services to an agent.
 *
 * <p>Everything here reads. An agent built on this toolset can study the market and the operator's
 * book and write a recommendation, and it cannot place an order, cancel one or change a strategy
 * however it is prompted — {@link ToolRegistry#readOnly} enforces that, not the instructions.
 *
 * <p>A service that is not configured (no broker connection, no news client) simply contributes no
 * tool, and the model is never told about a capability it does not have.
 */
public final class ReadOnlyToolset {
    private ReadOnlyToolset() {
    }

    public static ToolRegistry create(
            TradingApi tradingApi,
            AlpacaMarketDataApi marketDataApi,
            AlpacaNewsClient newsClient,
            MarketHoursService marketHours,
            PositionSnapshots positionSnapshots,
            Clock clock
    ) {
        List<AgentTool> tools = new ArrayList<>();
        if (marketHours != null) {
            tools.add(new MarketSessionTool(marketHours, clock));
        }
        if (tradingApi != null) {
            tools.add(new LatestPriceTool(tradingApi));
        }
        if (marketDataApi != null) {
            tools.add(new DailyBarsTool(marketDataApi, clock));
            tools.add(new AutoAnalyzeTool(new AutoAnalyzeService(marketDataApi)));
        }
        if (newsClient != null) {
            tools.add(new LatestNewsTool(newsClient));
        }
        if (positionSnapshots != null) {
            tools.add(new OpenPositionsTool(positionSnapshots));
        }
        return ToolRegistry.readOnly(tools);
    }
}
