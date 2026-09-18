package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RollingDigitsTest {
    private static final String VALUE = "+$1,234.50 (12%)";

    @Test
    void atTheStartEveryDigitSpinsButTheNumbersShapeStays() {
        assertEquals("+$7,777.77 (77%)", RollingDigits.frame(VALUE, 0, () -> 7));
    }

    @Test
    void reelsStopFromLeftToRight() {
        // 8 digits: digit k locks at (k + 1) / 9, so at 0.5 the first four have landed.
        assertEquals("+$1,234.00 (00%)", RollingDigits.frame(VALUE, 0.5, () -> 0));
    }

    @Test
    void itSettlesOnTheRealValue() {
        assertEquals(VALUE, RollingDigits.frame(VALUE, 1, () -> 9));
        assertEquals(VALUE, RollingDigits.frame(VALUE, 0.95, () -> 9), "every reel has stopped just before the end");
    }

    @Test
    void textWithoutDigitsIsLeftAlone() {
        assertEquals("—", RollingDigits.frame("—", 0.2, () -> 3));
    }
}
