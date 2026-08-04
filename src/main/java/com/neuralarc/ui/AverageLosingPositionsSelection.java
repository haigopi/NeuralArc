package com.neuralarc.ui;

import java.math.BigDecimal;

record AverageLosingPositionsSelection(OrderType orderType, QuantityMode quantityMode, int quantity, BigDecimal limitDiscountPercent) {
    AverageLosingPositionsSelection {
        limitDiscountPercent = limitDiscountPercent == null ? BigDecimal.ZERO : limitDiscountPercent;
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
