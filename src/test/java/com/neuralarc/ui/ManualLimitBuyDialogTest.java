package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ManualLimitBuyDialogTest {
    @Test
    void defaultLimitPriceUsesCurrentPrice() {
        assertEquals("123.46", ManualLimitBuyDialog.defaultLimitPrice(new BigDecimal("123.456")));
    }

    @Test
    void defaultLimitPriceIsBlankWhenCurrentPriceUnavailable() {
        assertEquals("", ManualLimitBuyDialog.defaultLimitPrice(null));
        assertEquals("", ManualLimitBuyDialog.defaultLimitPrice(BigDecimal.ZERO));
    }
}
