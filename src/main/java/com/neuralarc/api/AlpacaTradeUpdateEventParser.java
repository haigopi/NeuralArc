package com.neuralarc.api;

import com.neuralarc.util.Monetary;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public final class AlpacaTradeUpdateEventParser {
    private AlpacaTradeUpdateEventParser() {
    }

    public static Optional<AlpacaTradeUpdateEvent> parse(String payload) {
        if (payload == null || payload.isBlank()) {
            return Optional.empty();
        }
        try {
            JSONObject root = new JSONObject(payload);
            JSONObject data = root;
            if (root.has("stream") && root.has("data")) {
                data = root.optJSONObject("data");
            }
            if (data == null) {
                return Optional.empty();
            }

            String eventType = data.optString("event", "").trim();
            JSONObject orderJson = data.optJSONObject("order");
            if (orderJson == null && data.has("order_id")) {
                orderJson = data;
            }
            if (eventType.isBlank() || orderJson == null) {
                return Optional.empty();
            }

            Instant submittedAt = parseInstant(orderJson.optString("submitted_at", ""));
            if (submittedAt == null) {
                submittedAt = parseInstant(orderJson.optString("created_at", ""));
            }

            AlpacaOrderData orderData = new AlpacaOrderData(
                    orderJson.optString("id", orderJson.optString("order_id", "")),
                    orderJson.optString("client_order_id", ""),
                    orderJson.optString("symbol", ""),
                    orderJson.optString("side", ""),
                    orderJson.optString("type", "limit"),
                    parseMoney(orderJson.optString("limit_price", "0")),
                    parseMoney(orderJson.optString("filled_avg_price", "0")),
                    parseMoney(orderJson.optString("filled_qty", "0")),
                    orderJson.optString("status", eventType),
                    orderJson.toString(),
                    submittedAt
            );
            return Optional.of(new AlpacaTradeUpdateEvent(eventType, orderData));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static BigDecimal parseMoney(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
            return Monetary.zero();
        }
        try {
            return Monetary.round(new BigDecimal(value));
        } catch (NumberFormatException ex) {
            return Monetary.zero();
        }
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            return null;
        }
    }
}

