package com.neuralarc.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One minute of an intraday value line: the value at that minute and the baseline the day is measured
 * from — the previous session's close for account equity — so the day's change is their difference.
 */
public record IntradayValueSample(Instant minute, BigDecimal value, BigDecimal baseline) {
    public BigDecimal dayChange() {
        return value.subtract(baseline);
    }
}
