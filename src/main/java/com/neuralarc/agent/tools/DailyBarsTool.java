package com.neuralarc.agent.tools;

import com.neuralarc.agent.AgentTool;
import com.neuralarc.agent.ToolArguments;
import com.neuralarc.agent.ToolParameters;
import com.neuralarc.api.AlpacaMarketDataApi;
import com.neuralarc.model.MarketBar;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Daily OHLCV bars for one symbol.
 *
 * <p>Bars are the most token-expensive thing an agent can ask for, so the look-back is capped and the
 * rows are emitted as compact arrays rather than named objects: a year of bars costs roughly a third
 * as many tokens this way, and the header tells the model what each position means.
 */
public final class DailyBarsTool implements AgentTool {
    static final int MAX_LOOKBACK_DAYS = 365;
    static final int DEFAULT_LOOKBACK_DAYS = 60;

    private final AlpacaMarketDataApi marketDataApi;
    private final Clock clock;

    public DailyBarsTool(AlpacaMarketDataApi marketDataApi, Clock clock) {
        this.marketDataApi = marketDataApi;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
    }

    @Override
    public String name() {
        return "daily_bars";
    }

    @Override
    public String description() {
        return "Daily open/high/low/close/volume bars for one stock over a recent window. Ask for the shortest"
                + " window that answers the question; prefer auto_analyze when you only need summary levels.";
    }

    @Override
    public ToolParameters parameters() {
        return ToolParameters.builder()
                .required("symbol", ToolParameters.Type.STRING, "Stock ticker, e.g. AAPL.")
                .optional("lookback_days", ToolParameters.Type.INTEGER,
                        "Calendar days back from today, 1 to " + MAX_LOOKBACK_DAYS + " (default " + DEFAULT_LOOKBACK_DAYS + ").")
                .build();
    }

    @Override
    public JSONObject call(ToolArguments arguments) throws Exception {
        String symbol = arguments.symbol("symbol");
        int lookbackDays = arguments.integer("lookback_days", 1, MAX_LOOKBACK_DAYS, DEFAULT_LOOKBACK_DAYS);
        LocalDate end = LocalDate.now(clock);
        List<MarketBar> bars = marketDataApi.getDailyBars(symbol, end.minusDays(lookbackDays), end);
        JSONArray rows = new JSONArray();
        for (MarketBar bar : bars) {
            rows.put(new JSONArray(List.of(
                    bar.timestamp() == null ? "" : bar.timestamp(),
                    bar.open().toPlainString(),
                    bar.high().toPlainString(),
                    bar.low().toPlainString(),
                    bar.close().toPlainString(),
                    bar.volume().toPlainString())));
        }
        return new JSONObject()
                .put("symbol", symbol)
                .put("columns", new JSONArray(List.of("date", "open", "high", "low", "close", "volume")))
                .put("count", rows.length())
                .put("bars", rows);
    }
}
