package com.neuralarc.ui;

import java.math.BigDecimal;

public record ManualLimitBuySelection(int quantity, BigDecimal limitPrice) {
    public ManualLimitBuySelection {
        limitPrice = limitPrice == null ? BigDecimal.ZERO : limitPrice;
    }
}
