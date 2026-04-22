package com.neuralarc.analytics;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class AnalyticsEvent {
    private final String type;
    private final Instant timestamp;
    private final Map<String, Object> payload = new LinkedHashMap<>();

    public AnalyticsEvent(String type) {
        this.type = type;
        this.timestamp = Instant.now();
    }

    public AnalyticsEvent put(String key, Object value) {
        if (value != null) {
            payload.put(key, value);
        }
        return this;
    }

    public String type() { return type; }
    public Instant timestamp() { return timestamp; }
    public Map<String, Object> payload() { return payload; }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"").append(type).append("\",");
        sb.append("\"timestamp\":\"").append(timestamp).append("\",");
        sb.append("\"payload\":{");
        boolean first = true;
        for (Map.Entry<String, Object> e : payload.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(escape(e.getKey())).append('"').append(':');
            Object v = e.getValue();
            if (v instanceof Number || v instanceof Boolean) {
                sb.append(v);
            } else {
                sb.append('"').append(escape(String.valueOf(v))).append('"');
            }
        }
        sb.append("}}");
        return sb.toString();
    }

    private static String escape(String v) {
        return v.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
