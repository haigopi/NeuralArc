package com.neuralarc.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Monetary {
    private static final int SCALE = 2;

    private Monetary() {
    }

    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal round(BigDecimal value) {
        if (value == null) {
            return zero();
        }
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }
}

