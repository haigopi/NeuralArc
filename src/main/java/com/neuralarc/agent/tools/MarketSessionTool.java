package com.neuralarc.agent.tools;

import com.neuralarc.agent.AgentTool;
import com.neuralarc.agent.ToolArguments;
import com.neuralarc.agent.ToolParameters;
import com.neuralarc.service.MarketHoursService;
import org.json.JSONObject;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Whether the market is open right now, and when it next opens.
 *
 * <p>Without this a model reasons from its own sense of time, which is wrong by construction: it does
 * not know today's date, the holiday calendar, or that this operator runs extended hours.
 */
public final class MarketSessionTool implements AgentTool {
    private final MarketHoursService marketHours;
    private final Clock clock;

    public MarketSessionTool(MarketHoursService marketHours, Clock clock) {
        this.marketHours = marketHours;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
    }

    @Override
    public String name() {
        return "market_session";
    }

    @Override
    public String description() {
        return "Whether the US market is open right now and when it next opens. Check this before proposing"
                + " anything time-sensitive; do not assume the date or the session from memory.";
    }

    @Override
    public ToolParameters parameters() {
        return ToolParameters.builder()
                .optional("extended_hours", ToolParameters.Type.BOOLEAN,
                        "Count pre-market and after-hours as open (default false).")
                .build();
    }

    @Override
    public JSONObject call(ToolArguments arguments) {
        boolean extendedHours = arguments.bool("extended_hours", false);
        LocalDate today = LocalDate.now(clock);
        return new JSONObject()
                .put("now", String.valueOf(clock.instant()))
                .put("today", String.valueOf(today))
                .put("session_open", marketHours.isTradingSessionOpen(extendedHours))
                .put("regular_hours", marketHours.isRegularMarketHours())
                .put("market_holiday_or_weekend", marketHours.isClosedDay(today))
                .put("next_open", String.valueOf(marketHours.nextMarketOpen(extendedHours)));
    }
}
