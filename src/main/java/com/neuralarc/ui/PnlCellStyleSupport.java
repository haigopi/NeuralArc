package com.neuralarc.ui;

import com.neuralarc.util.ThemeColors;

import java.awt.Color;
import java.math.BigDecimal;

final class PnlCellStyleSupport {
    static final Color POSITIVE = ThemeColors.color("NeuralArc.pnlPositive", new Color(27, 94, 32));
    static final Color NEGATIVE = ThemeColors.color("NeuralArc.pnlNegative", new Color(183, 28, 28));

    private PnlCellStyleSupport() {
    }

    /** Green above zero, red below. Accepts an amount or a percent such as "+12.34%". */
    static Color foregroundFor(Object value, Color defaultColor) {
        if (value == null || "-".equals(value.toString())) {
            return defaultColor;
        }
        try {
            BigDecimal pnlValue = new BigDecimal(numberText(value.toString()));
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

    private static String numberText(String value) {
        String text = value.trim();
        if (text.endsWith("%")) {
            text = text.substring(0, text.length() - 1).trim();
        }
        return text.startsWith("+") ? text.substring(1).trim() : text;
    }
}
