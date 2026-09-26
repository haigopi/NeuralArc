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
        filledAveragePrice = normalizeBrokerFillPrice(filledAveragePrice);
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

    /**
     * True when the broker refused this order because it had already seen its {@code client_order_id}.
     * That refusal is the duplicate guard working: the order it names is already at the broker, so the
     * right response is to leave it alone — not to retry, and not to fail the strategy.
     */
    public boolean duplicateClientOrderId() {
        String haystack = (status + " " + rawJson).toLowerCase(java.util.Locale.ROOT);
        return haystack.contains("client_order_id")
                && (haystack.contains("exist") || haystack.contains("unique") || haystack.contains("duplicate"));
    }

    public static AlpacaOrderData failed(String message) {
        return transportFailure(message);
    }

    public static AlpacaOrderData transportFailure(String message) {
        return new AlpacaOrderData("", "", "", "", "", Monetary.zero(), Monetary.zero(), Monetary.zero(), "failed_transport", "{\"message\":\"" + Objects.requireNonNullElse(message, "") + "\"}", null);
    }

    private static BigDecimal normalizeBrokerFillPrice(BigDecimal value) {
        if (value == null) {
            return Monetary.zero();
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 2 ? stripped.setScale(2) : stripped;
    }
}
