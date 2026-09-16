package com.neuralarc.analytics;

import com.neuralarc.model.MarketBar;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The lowest price a stock has actually traded over the last week of sessions. A planned buy uses it
 * as a floor: a limit under everything the stock has traded all week rarely fills, so it would leave
 * the operator with no position rather than a good price.
 */
public final class RecentLow {
    /** Sessions in a trading week. */
    public static final int WEEK_SESSIONS = 5;

    private RecentLow() {
    }

    /**
     * @param sessions completed daily bars, oldest first; only the last {@link #WEEK_SESSIONS} are read
     * @param todayLow the low so far today, or zero when the session has not traded yet
     * @return the lowest traded price, or zero when none is known
     */
    public static BigDecimal weekLow(List<MarketBar> sessions, BigDecimal todayLow) {
        BigDecimal low = positive(todayLow) ? todayLow : null;
        if (sessions != null && !sessions.isEmpty()) {
            int from = Math.max(0, sessions.size() - WEEK_SESSIONS);
            for (MarketBar bar : sessions.subList(from, sessions.size())) {
                BigDecimal barLow = bar == null ? null : bar.low();
                if (positive(barLow) && (low == null || barLow.compareTo(low) < 0)) {
                    low = barLow;
                }
            }
        }
        return low == null ? BigDecimal.ZERO : low;
    }

    /**
     * The low so far in a single session, read from that day's own daily bar. A limit placed at a price
     * the stock has already traded today can fill; one placed under it is a guess.
     *
     * @param sessions daily bars in any order; each bar's timestamp starts with its ISO date
     * @param day      the session to read, normally today
     * @return that session's low, or zero when the day has no bar yet
     */
    public static BigDecimal sessionLow(List<MarketBar> sessions, LocalDate day) {
        if (sessions == null || day == null) {
            return BigDecimal.ZERO;
        }
        String isoDate = day.toString();
        for (int index = sessions.size() - 1; index >= 0; index--) {
            MarketBar bar = sessions.get(index);
            if (bar != null && bar.timestamp() != null && bar.timestamp().startsWith(isoDate) && positive(bar.low())) {
                return bar.low();
            }
        }
        return BigDecimal.ZERO;
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }
}
