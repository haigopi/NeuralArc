package com.neuralarc.analytics;

import com.neuralarc.model.MarketBar;

import java.math.BigDecimal;
import java.util.List;

/**
 * Today's open and low for a symbol, from whatever bars a scanner already loaded: today's daily bar
 * when the feed has one, else the intraday bars. These are two of the three prices a base buy is
 * measured from (see {@link PlannedEntryPrice#belowMarket}); both are zero when nothing has traded.
 */
public final class TodaySession {
    private TodaySession() {
    }

    /** @param todayDaily today's (forming) daily bar, or null when the feed has none yet */
    public static BigDecimal open(MarketBar todayDaily, List<MarketBar> intradayBars) {
        if (todayDaily != null && positive(todayDaily.open())) {
            return todayDaily.open();
        }
        if (intradayBars != null) {
            for (MarketBar bar : intradayBars) {
                if (bar != null && positive(bar.open())) {
                    return bar.open();
                }
            }
        }
        return BigDecimal.ZERO;
    }

    /** The lowest price traded today across the daily bar and every intraday bar. */
    public static BigDecimal low(MarketBar todayDaily, List<MarketBar> intradayBars) {
        BigDecimal lowest = todayDaily != null && positive(todayDaily.low()) ? todayDaily.low() : BigDecimal.ZERO;
        if (intradayBars != null) {
            for (MarketBar bar : intradayBars) {
                if (bar != null && positive(bar.low()) && (!positive(lowest) || bar.low().compareTo(lowest) < 0)) {
                    lowest = bar.low();
                }
            }
        }
        return lowest;
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }
}
