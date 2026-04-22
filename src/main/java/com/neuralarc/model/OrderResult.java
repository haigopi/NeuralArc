package com.neuralarc.model;

import java.math.BigDecimal;

public record OrderResult(boolean success, String orderId, String message, String symbol, int quantity, BigDecimal fillPrice) {
    public static OrderResult ok(String orderId, String symbol, int quantity, BigDecimal fillPrice) {
        return new OrderResult(true, orderId, "OK", symbol, quantity, fillPrice);
    }

    public static OrderResult fail(String symbol, int quantity, String message) {
        return new OrderResult(false, null, message, symbol, quantity, BigDecimal.ZERO);
    }
}
