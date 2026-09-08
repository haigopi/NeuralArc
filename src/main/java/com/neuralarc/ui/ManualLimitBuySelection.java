package com.neuralarc.ui;

import com.neuralarc.model.TimeInForce;

import java.math.BigDecimal;

public record ManualLimitBuySelection(
        int quantity,
        BigDecimal limitPrice,
        boolean repositionAfterExpiry,
        TimeInForce timeInForce
) {
    public ManualLimitBuySelection {
        limitPrice = limitPrice == null ? BigDecimal.ZERO : limitPrice;
        timeInForce = timeInForce == null ? TimeInForce.DAY : timeInForce;
    }

    public ManualLimitBuySelection(int quantity, BigDecimal limitPrice) {
        this(quantity, limitPrice, false, TimeInForce.DAY);
    }

    public ManualLimitBuySelection(int quantity, BigDecimal limitPrice, boolean repositionAfterExpiry) {
        this(quantity, limitPrice, repositionAfterExpiry, TimeInForce.DAY);
    }
}
