package com.neuralarc.model;

import java.util.Locale;

public enum TimeInForce {
    DAY,
    GTC;

    public String brokerValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static TimeInForce safeValue(String value) {
        if (value == null || value.isBlank()) {
            return DAY;
        }
        try {
            return TimeInForce.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return DAY;
        }
    }
}
