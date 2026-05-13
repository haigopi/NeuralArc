package com.neuralarc.util;

import java.util.Locale;

public final class BrokerOrderStatusUtil {
    private BrokerOrderStatusUtil() {
    }

    public static String normalize(String status) {
        if (status == null || status.isBlank()) {
            return "";
        }
        return status.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    public static boolean isWaitingForFill(String status) {
        String normalized = normalize(status);
        return "new".equals(normalized)
                || "accepted".equals(normalized)
                || "accepted_for_bidding".equals(normalized)
                || "pending_new".equals(normalized)
                || "pending_cancel".equals(normalized)
                || "pending_replace".equals(normalized)
                || "calculated".equals(normalized)
                || "submitted".equals(normalized)
                || "pending".equals(normalized)
                || "partially_filled".equals(normalized);
    }

    public static String displayLabel(String status) {
        String normalized = normalize(status);
        if (normalized.isBlank()) {
            return "-";
        }
        if ("failed_transport".equals(normalized)) {
            return "Unable To Reach Broker";
        }
        if ("api_error".equals(normalized)) {
            return "Broker API Error";
        }
        String[] parts = normalized.split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.isEmpty() ? "-" : builder.toString();
    }
}

