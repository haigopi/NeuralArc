package com.neuralarc.service;

import com.neuralarc.model.StrategyStage;
import org.json.JSONException;
import org.json.JSONObject;

final class BrokerRejectionMessageFormatter {
    private BrokerRejectionMessageFormatter() {
    }

    static boolean isQueueableSessionRejection(String rawJson) {
        String normalized = rawJson == null ? "" : rawJson.toLowerCase();
        return normalized.contains("market is closed")
                || normalized.contains("outside market hours")
                || normalized.contains("extended_hours")
                || normalized.contains("time_in_force")
                || normalized.contains("session");
    }

    static String failureMessage(String rawJson, StrategyStage stage) {
        if (rawJson == null || rawJson.isBlank()) {
            return "Broker rejected " + stage.name() + " order";
        }
        try {
            JSONObject error = new JSONObject(rawJson);
            String msg = error.optString("message", "").trim();
            if (!msg.isBlank()) {
                String display = Character.toUpperCase(msg.charAt(0)) + msg.substring(1);
                return "Broker rejected " + stage.name() + " order: " + display;
            }
        } catch (JSONException ignored) {
            // Fall through to raw JSON fallback.
        }
        String compact = rawJson.replace('\n', ' ').replace('\r', ' ').trim();
        if (compact.length() > 220) {
            compact = compact.substring(0, 220) + "...";
        }
        return "Broker rejected " + stage.name() + " order: " + compact;
    }
}
