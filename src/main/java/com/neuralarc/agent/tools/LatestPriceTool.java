package com.neuralarc.agent.tools;

import com.neuralarc.agent.AgentTool;
import com.neuralarc.agent.ToolArgumentException;
import com.neuralarc.agent.ToolArguments;
import com.neuralarc.agent.ToolParameters;
import com.neuralarc.api.TradingApi;
import org.json.JSONObject;

import java.math.BigDecimal;

/** The current price for one symbol, through the same broker boundary the app trades on. */
public final class LatestPriceTool implements AgentTool {
    private final TradingApi tradingApi;

    public LatestPriceTool(TradingApi tradingApi) {
        this.tradingApi = tradingApi;
    }

    @Override
    public String name() {
        return "latest_price";
    }

    @Override
    public String description() {
        return "The latest traded price for one stock symbol. Use it before judging any level; every other"
                + " price in this conversation may be minutes old.";
    }

    @Override
    public ToolParameters parameters() {
        return ToolParameters.builder()
                .required("symbol", ToolParameters.Type.STRING, "Stock ticker, e.g. AAPL.")
                .build();
    }

    @Override
    public JSONObject call(ToolArguments arguments) throws Exception {
        String symbol = arguments.symbol("symbol");
        BigDecimal price = tradingApi.getLatestPrice(symbol);
        if (price == null || price.signum() <= 0) {
            throw new ToolArgumentException("No price is available for " + symbol
                    + ". It may not be tradable, or the market data subscription may not cover it.");
        }
        return new JSONObject().put("symbol", symbol).put("price", price.toPlainString());
    }
}
