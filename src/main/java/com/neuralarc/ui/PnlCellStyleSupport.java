package com.neuralarc.ui;

import java.awt.Color;
import java.math.BigDecimal;

final class PnlCellStyleSupport {
    static final Color POSITIVE = new Color(27, 94, 32);
    static final Color NEGATIVE = new Color(183, 28, 28);

    private PnlCellStyleSupport() {
    }

    static Color foregroundFor(Object value, Color defaultColor) {
        if (value == null || "-".equals(value.toString())) {
            return defaultColor;
        }
        try {
            BigDecimal pnlValue = new BigDecimal(value.toString());
            if (pnlValue.compareTo(BigDecimal.ZERO) > 0) {
                return POSITIVE;
            }
            if (pnlValue.compareTo(BigDecimal.ZERO) < 0) {
                return NEGATIVE;
            }
            return defaultColor;
        } catch (Exception ex) {
            return defaultColor;
        }
    }
}
