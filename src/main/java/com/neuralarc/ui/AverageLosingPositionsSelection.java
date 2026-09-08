package com.neuralarc.ui;

import com.neuralarc.model.TimeInForce;

import java.math.BigDecimal;

record AverageLosingPositionsSelection(
        OrderType orderType,
        QuantityMode quantityMode,
        int quantity,
        BigDecimal limitDiscountPercent,
        TimeInForce timeInForce
) {
    AverageLosingPositionsSelection {
        limitDiscountPercent = limitDiscountPercent == null ? BigDecimal.ZERO : limitDiscountPercent;
        timeInForce = timeInForce == null ? TimeInForce.DAY : timeInForce;
    }

    AverageLosingPositionsSelection(OrderType orderType, QuantityMode quantityMode, int quantity, BigDecimal limitDiscountPercent) {
        this(orderType, quantityMode, quantity, limitDiscountPercent, TimeInForce.DAY);
    }

    enum OrderType {
        MARKET,
        LIMIT_BELOW_MARKET
    }

    enum QuantityMode {
        CURRENT_POSITION_QUANTITY,
        FIXED_INPUT_QUANTITY
    }
}
