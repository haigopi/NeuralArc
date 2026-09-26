package com.neuralarc.agent.tools;

import com.neuralarc.agent.AgentTool;
import com.neuralarc.agent.ToolArgumentException;
import com.neuralarc.agent.ToolArguments;
import com.neuralarc.agent.ToolRegistry;
import com.neuralarc.agent.ReadOnlyToolset;
import com.neuralarc.api.AlpacaMarketDataApi;
import com.neuralarc.api.AlpacaMarketDataException;
import com.neuralarc.model.MarketBar;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.service.MarketHoursService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadOnlyToolsTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-22T15:00:00Z"), ZoneOffset.UTC);

    @Test
    void dailyBarsAsksTheWindowTheModelRequestedAndReturnsCompactRows() throws Exception {
        RecordingMarketData marketData = new RecordingMarketData();
        AgentTool tool = new DailyBarsTool(marketData, CLOCK);

        JSONObject result = tool.call(new ToolArguments(new JSONObject().put("symbol", "nvda").put("lookback_days", 10)));

        assertEquals("NVDA", marketData.symbol, "symbols reach the broker uppercase");
        assertEquals(LocalDate.of(2026, 9, 12), marketData.start);
        assertEquals(LocalDate.of(2026, 9, 22), marketData.end);
        assertEquals(1, result.getInt("count"));
        JSONArray row = result.getJSONArray("bars").getJSONArray(0);
        assertEquals("2026-09-22", row.getString(0));
        assertEquals("181.50", row.getString(4), "close keeps its exact decimal, never a double");
        assertEquals(List.of("date", "open", "high", "low", "close", "volume"),
                result.getJSONArray("columns").toList());
    }

    @Test
    void dailyBarsRefusesAnAbsurdLookbackInsteadOfHammeringTheBroker() {
        AgentTool tool = new DailyBarsTool(new RecordingMarketData(), CLOCK);

        ToolArgumentException thrown = assertThrows(ToolArgumentException.class,
                () -> tool.call(new ToolArguments(new JSONObject().put("symbol", "NVDA").put("lookback_days", 5000))));
        assertTrue(thrown.getMessage().contains("365"));
    }

    @Test
    void openPositionsReadsTheCachedBookAndFiltersByMode() throws Exception {
        PositionSnapshots snapshots = () -> List.of(
                position("AAPL", StrategyMode.LIVE, 10),
                position("NIO", StrategyMode.PAPER, 4));
        AgentTool tool = new OpenPositionsTool(snapshots);

        JSONObject all = tool.call(new ToolArguments(new JSONObject()));
        JSONObject live = tool.call(new ToolArguments(new JSONObject().put("mode", "live")));

        assertEquals(2, all.getInt("count"));
        assertEquals(1, live.getInt("count"));
        JSONObject only = live.getJSONArray("positions").getJSONObject(0);
        assertEquals("AAPL", only.getString("symbol"));
        assertEquals(10, only.getInt("shares"));
        assertEquals("180.25", only.getString("average_cost"));
    }

    @Test
    void marketSessionReportsTheRealCalendarNotTheModelsGuess() throws Exception {
        JSONObject result = new MarketSessionTool(new MarketHoursService(), CLOCK)
                .call(new ToolArguments(new JSONObject()));

        assertEquals("2026-09-22", result.getString("today"));
        assertTrue(result.has("session_open"));
        assertTrue(result.has("next_open"));
    }

    @Test
    void theToolsetIsReadOnlyAndSkipsServicesThatAreNotConfigured() {
        ToolRegistry registry = ReadOnlyToolset.create(
                null, new RecordingMarketData(), null, new MarketHoursService(), List::of, CLOCK);

        List<String> names = registry.all().stream().map(AgentTool::name).toList();
        assertEquals(List.of("market_session", "daily_bars", "auto_analyze", "open_positions"), names);
        assertFalse(names.contains("latest_price"), "no broker connection means no price tool is offered");
        assertTrue(registry.all().stream().allMatch(tool -> tool.effect() == AgentTool.Effect.READ_ONLY));
    }

    private static PositionSnapshots.PositionView position(String symbol, StrategyMode mode, int shares) {
        return new PositionSnapshots.PositionView(symbol, mode, "Growth", "ACTIVE", shares,
                new BigDecimal("180.25"), new BigDecimal("184.00"), new BigDecimal("37.50"));
    }

    private static final class RecordingMarketData implements AlpacaMarketDataApi {
        private String symbol;
        private LocalDate start;
        private LocalDate end;

        @Override
        public List<MarketBar> getDailyBars(String symbol, LocalDate startDate, LocalDate endDate) {
            this.symbol = symbol;
            this.start = startDate;
            this.end = endDate;
            List<MarketBar> bars = new ArrayList<>();
            bars.add(new MarketBar(symbol, "2026-09-22", new BigDecimal("180.00"), new BigDecimal("182.40"),
                    new BigDecimal("179.10"), new BigDecimal("181.50"), new BigDecimal("41000000")));
            return bars;
        }

        @Override
        public List<MarketBar> getIntradayBars(String symbol, LocalDate startDate, LocalDate endDate, int intervalMinutes)
                throws AlpacaMarketDataException {
            return List.of();
        }
    }
}
