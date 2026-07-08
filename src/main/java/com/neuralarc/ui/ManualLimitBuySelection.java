package com.neuralarc.ui;

import java.math.BigDecimal;

public record ManualLimitBuySelection(int quantity, BigDecimal limitPrice, boolean repositionAfterExpiry) {
    public ManualLimitBuySelection {
        limitPrice = limitPrice == null ? BigDecimal.ZERO : limitPrice;
    }

    public ManualLimitBuySelection(int quantity, BigDecimal limitPrice) {
        this(quantity, limitPrice, false);
    }
}
