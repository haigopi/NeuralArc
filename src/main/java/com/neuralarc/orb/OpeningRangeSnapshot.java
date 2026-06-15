package com.neuralarc.orb;

import java.math.BigDecimal;
import java.time.Instant;

public record OpeningRangeSnapshot(
        String symbol,
        Instant rangeStart,
        Instant rangeEnd,
        BigDecimal high,
        BigDecimal low,
        BigDecimal volume,
        int barCount,
        boolean complete,
        String rejectionReason
) {
    public OpeningRangeSnapshot {
        symbol = symbol == null ? "" : symbol.trim().toUpperCase();
        volume = volume == null ? BigDecimal.ZERO : volume;
        rejectionReason = rejectionReason == null ? "" : rejectionReason;
    }

    public BigDecimal rangePercent() {
        if (high == null || low == null || low.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        return high.subtract(low).multiply(BigDecimal.valueOf(100)).divide(low, 4, java.math.RoundingMode.HALF_UP);
    }
}
