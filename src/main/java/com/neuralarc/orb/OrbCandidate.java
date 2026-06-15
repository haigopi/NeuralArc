package com.neuralarc.orb;

import java.math.BigDecimal;

public record OrbCandidate(
        String symbol,
        BigDecimal latestPrice,
        BigDecimal regularSessionOpen,
        BigDecimal relativeVolume,
        BigDecimal averageVolume,
        BigDecimal spreadPercent,
        String discoverySource
) {
    public OrbCandidate {
        symbol = symbol == null ? "" : symbol.trim().toUpperCase();
        discoverySource = discoverySource == null || discoverySource.isBlank() ? "manual" : discoverySource;
    }
}
