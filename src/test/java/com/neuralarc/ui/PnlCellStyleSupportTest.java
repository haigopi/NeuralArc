package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PnlCellStyleSupportTest {
    private static final Color DEFAULT = Color.BLACK;

    @Test
    void positivePnlUsesGainColor() {
        assertEquals(PnlCellStyleSupport.POSITIVE, PnlCellStyleSupport.foregroundFor("12.34", DEFAULT));
    }

    @Test
    void negativePnlUsesLossColor() {
        assertEquals(PnlCellStyleSupport.NEGATIVE, PnlCellStyleSupport.foregroundFor("-12.34", DEFAULT));
    }

    @Test
    void blankAndZeroPnlUseDefaultColor() {
        assertEquals(DEFAULT, PnlCellStyleSupport.foregroundFor("-", DEFAULT));
        assertEquals(DEFAULT, PnlCellStyleSupport.foregroundFor("0.00", DEFAULT));
    }
}
