package com.neuralarc.rangerider;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One trading session's open/high/low/close from the Range Rider lookback window, kept per-day (not
 * just averaged) so the analyzer can replay the planned buy/sell against every session in the window
 * and report how often that plan would actually have completed inside a single day.
 */
public record RangeRiderSession(
        LocalDate date, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close
) {
    public RangeRiderSession {
        open = open == null ? BigDecimal.ZERO : open;
        high = high == null ? BigDecimal.ZERO : high;
        low = low == null ? BigDecimal.ZERO : low;
        close = close == null ? BigDecimal.ZERO : close;
    }

    /** The session's own high-minus-low range as a percentage of its low. */
    public BigDecimal rangePercent() {
        if (low.compareTo(BigDecimal.ZERO) <= 0 || high.compareTo(low) <= 0) {
            return BigDecimal.ZERO;
        }
        return high.subtract(low)
                .multiply(BigDecimal.valueOf(100))
                .divide(low, 4, java.math.RoundingMode.HALF_UP);
    }
}
