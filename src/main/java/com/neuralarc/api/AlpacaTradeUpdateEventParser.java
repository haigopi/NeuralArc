package com.neuralarc.api;

import com.neuralarc.util.Monetary;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

public final class AlpacaTradeUpdateEventParser {
    private static final Logger LOGGER = Logger.getLogger(AlpacaTradeUpdateEventParser.class.getName());

    private AlpacaTradeUpdateEventParser() {
    }

    public static Optional<AlpacaTradeUpdateEvent> parse(String payload) {
        List<AlpacaTradeUpdateEvent> events = parseAll(payload);
        return events.isEmpty() ? Optional.empty() : Optional.of(events.get(0));
    }

    public static List<AlpacaTradeUpdateEvent> parseAll(String payload) {
        List<AlpacaTradeUpdateEvent> events = new ArrayList<>();
        if (payload == null || payload.isBlank()) {
            return events;
        }
        try {
            String normalized = payload.trim();
            if (normalized.startsWith("[")) {
                JSONArray array = new JSONArray(normalized);
                for (int i = 0; i < array.length(); i++) {
                    if (!array.isNull(i)) {
                        parseObject(array.optJSONObject(i)).ifPresent(events::add);
                    }
                }
                return events;
            }
            parseObject(new JSONObject(normalized)).ifPresent(events::add);
        } catch (Exception ex) {
            LOGGER.fine(() -> "[STREAM][PARSE] Ignored non-trade or malformed payload: "
                    + payloadSummary(payload) + " error=" + ex.getClass().getSimpleName());
            return events;
        }
        if (events.isEmpty()) {
            LOGGER.fine(() -> "[STREAM][PARSE] Payload contained no trade update: " + payloadSummary(payload));
        }
        return events;
    }

    private static Optional<AlpacaTradeUpdateEvent> parseObject(JSONObject root) {
        if (root == null) {
            return Optional.empty();
        }
        try {
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
        } catch (Exception ex) {
            LOGGER.fine(() -> "[STREAM][PARSE] Failed to parse trade update object: "
                    + payloadSummary(root.toString()) + " error=" + ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static String payloadSummary(String payload) {
        if (payload == null) {
            return "null";
        }
        String trimmed = payload.replaceAll("\\s+", " ").trim();
        if (trimmed.length() <= 240) {
            return trimmed;
        }
        return trimmed.substring(0, 240) + "...";
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
