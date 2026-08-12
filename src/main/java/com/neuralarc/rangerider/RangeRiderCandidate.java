package com.neuralarc.rangerider;

import java.math.BigDecimal;
import java.util.List;

/**
 * A single Range Rider candidate built from live market data: the last few weeks of daily bars for one
 * symbol, reduced to the average open, the average high, and the average low of those sessions, plus
 * the raw per-session breakdown the analyzer replays its planned buy/sell against.
 *
 * <p>Everything here comes from <em>completed</em> sessions only — today's still-forming bar is never
 * part of the averages and never sets the reference price, so a scan behaves identically before the
 * open, mid-session, and after the close. No hardcoded tickers or canned prices ever populate this.
 *
 * @param referencePrice       the most recent completed session's close — the anchor the daily plan is
 *                             priced from, never today's partial bar
 * @param averageRangePercent  average of each session's (high - low) / low, in percent
 * @param averageDipPercent    how far below its own open the stock typically trades, in percent —
 *                             derived from the average open and average low. This is the drift-free
 *                             form of "the average daily low": it can be applied to any future
 *                             session, whereas an absolute price from three weeks ago cannot.
 * @param averageRallyPercent  how far above its own open the stock typically trades, in percent — the
 *                             same idea for "the average daily high"
 * @param rangeStabilityPercent 100 minus the coefficient of variation of the per-session ranges,
 *                              clamped to 0–100. Higher means the daily range repeats more reliably,
 *                              which is what a same-day income plan depends on.
 * @param sessions             the per-day open/high/low/close rows that produced the averages
 */
public record RangeRiderCandidate(
        String symbol, String companyName, BigDecimal referencePrice,
        BigDecimal averageOpen, BigDecimal averageHigh, BigDecimal averageLow,
        BigDecimal averageRangePercent, BigDecimal averageDipPercent, BigDecimal averageRallyPercent,
        BigDecimal rangeStabilityPercent,
        BigDecimal previousClose, BigDecimal dayChangePercent,
        long averageVolume, BigDecimal relativeVolume,
        List<RangeRiderSession> sessions
) {
    public RangeRiderCandidate {
        sessions = sessions == null ? List.of() : List.copyOf(sessions);
    }

    public int sessionsAnalyzed() {
        return sessions.size();
    }
}
