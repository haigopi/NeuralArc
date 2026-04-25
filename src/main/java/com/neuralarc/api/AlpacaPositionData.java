package com.neuralarc.api;

import com.neuralarc.util.Monetary;

import java.math.BigDecimal;

public record AlpacaPositionData(
        String symbol,
        BigDecimal quantity,
        BigDecimal avgEntryPrice,
        BigDecimal marketPrice,
        String rawJson
) {
    public AlpacaPositionData {
        symbol = symbol == null ? "" : symbol;
        quantity = Monetary.round(quantity);
        avgEntryPrice = Monetary.round(avgEntryPrice);
        marketPrice = Monetary.round(marketPrice);
        rawJson = rawJson == null ? "{}" : rawJson;
    }

    public boolean exists() {
        return !symbol.isBlank() && quantity.compareTo(BigDecimal.ZERO) > 0;
    }
}

