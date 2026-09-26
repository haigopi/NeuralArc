package com.neuralarc.agent.tools;

import com.neuralarc.agent.AgentTool;
import com.neuralarc.agent.ToolArguments;
import com.neuralarc.agent.ToolParameters;
import com.neuralarc.model.AutoAnalyzeResult;
import com.neuralarc.service.AutoAnalyzeService;
import org.json.JSONObject;

import java.math.BigDecimal;

/**
 * The app's own Auto Analyze levels for one symbol.
 *
 * <p>This is the tool an agent should reach for first: it returns the same support, resistance and
 * average levels the operator sees in the Auto Analyze tab, computed by the same service. An agent
 * that re-derives levels from raw bars will eventually disagree with the screen the operator is
 * looking at, and that disagreement is indistinguishable from a bug.
 */
public final class AutoAnalyzeTool implements AgentTool {
    private final AutoAnalyzeService autoAnalyzeService;

    public AutoAnalyzeTool(AutoAnalyzeService autoAnalyzeService) {
        this.autoAnalyzeService = autoAnalyzeService;
    }

    @Override
    public String name() {
        return "auto_analyze";
    }

    @Override
    public String description() {
        return "NeuralArc's own technical read on one stock: recent highs and lows over several windows,"
                + " average daily prices and today's price. These are the exact levels the operator sees.";
    }

    @Override
    public ToolParameters parameters() {
        return ToolParameters.builder()
                .required("symbol", ToolParameters.Type.STRING, "Stock ticker, e.g. AAPL.")
                .optional("months_back", ToolParameters.Type.INTEGER, "History to analyze, 1 to 12 months (default 3).")
                .optional("interval_minutes", ToolParameters.Type.INTEGER, "Intraday bar interval, 1 to 60 (default 15).")
                .build();
    }

    @Override
    public JSONObject call(ToolArguments arguments) throws Exception {
        String symbol = arguments.symbol("symbol");
        int monthsBack = arguments.integer("months_back", 1, 12, 3);
        int intervalMinutes = arguments.integer("interval_minutes", 1, 60, 15);
        AutoAnalyzeResult result = autoAnalyzeService.analyze(symbol, monthsBack, intervalMinutes);
        return new JSONObject()
                .put("symbol", result.symbol())
                .put("today_price", plain(result.todayStockPrice()))
                .put("average_daily_close", plain(result.averageDailyClose()))
                .put("one_week_low", plain(result.oneWeekLow()))
                .put("one_week_high", plain(result.oneWeekHigh()))
                .put("two_week_low", plain(result.twoWeekLow()))
                .put("two_week_high", plain(result.twoWeekHigh()))
                .put("one_month_low", plain(result.oneMonthLow()))
                .put("one_month_high", plain(result.oneMonthHigh()))
                .put("one_year_low", plain(result.oneYearLow()))
                .put("one_year_high", plain(result.oneYearHigh()))
                .put("analyzed_from", String.valueOf(result.startDate()))
                .put("analyzed_to", String.valueOf(result.endDate()));
    }

    private static String plain(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }
}
