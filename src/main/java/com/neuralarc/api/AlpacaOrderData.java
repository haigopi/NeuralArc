package com.neuralarc.api;

import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record AlpacaOrderData(
        String orderId,
        String clientOrderId,
        String symbol,
        String side,
        String type,
        BigDecimal limitPrice,
        BigDecimal filledAveragePrice,
        BigDecimal filledQuantity,
        String status,
        String rawJson,
        Instant submittedAt
) {
    public AlpacaOrderData {
        orderId = orderId == null ? "" : orderId;
        clientOrderId = clientOrderId == null ? "" : clientOrderId;
        symbol = symbol == null ? "" : symbol;
        side = side == null ? "" : side;
        type = type == null ? "" : type;
        limitPrice = Monetary.round(limitPrice);
        filledAveragePrice = Monetary.round(filledAveragePrice);
        filledQuantity = Monetary.round(filledQuantity);
        status = status == null ? "" : status;
        rawJson = rawJson == null ? "{}" : rawJson;
    }

    public AlpacaOrderData(
            String orderId,
            String clientOrderId,
            String symbol,
            String side,
            String type,
            BigDecimal limitPrice,
            BigDecimal filledAveragePrice,
            BigDecimal filledQuantity,
            String status,
            String rawJson
    ) {
        this(orderId, clientOrderId, symbol, side, type, limitPrice, filledAveragePrice, filledQuantity, status, rawJson, null);
    }

    public static AlpacaOrderData failed(String message) {
        return new AlpacaOrderData("", "", "", "", "", Monetary.zero(), Monetary.zero(), Monetary.zero(), "failed", "{\"message\":\"" + Objects.requireNonNullElse(message, "") + "\"}", null);
    }
}
