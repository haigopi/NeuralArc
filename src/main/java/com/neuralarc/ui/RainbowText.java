package com.neuralarc.ui;

import java.awt.Color;
import java.util.Locale;

/**
 * Renders a string as rainbow-colored HTML for Swing labels (each visible character cycles through
 * the spectrum). Used for the prominent P&L total text so the rainbow styling is centralized and
 * unit-testable rather than hand-rolled at each call site.
 */
final class RainbowText {
    // Red, orange, yellow-ish, green, blue, indigo, violet.
    private static final Color[] SPECTRUM = {
            new Color(0xE6, 0x39, 0x46),
            new Color(0xF3, 0x72, 0x2C),
            new Color(0xF8, 0x96, 0x1E),
            new Color(0x43, 0xAA, 0x8B),
            new Color(0x27, 0x7D, 0xA1),
            new Color(0x57, 0x5F, 0xCF),
            new Color(0x9B, 0x5D, 0xE5)
    };

    private RainbowText() {
    }

    static String toHtml(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : "";
        }
        StringBuilder html = new StringBuilder("<html>");
        int colorIndex = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                html.append(c == '\n' ? "<br>" : "&nbsp;");
                continue;
            }
            Color color = SPECTRUM[colorIndex % SPECTRUM.length];
            colorIndex++;
            html.append("<font color='#")
                    .append(hex(color))
                    .append("'>")
                    .append(escape(c))
                    .append("</font>");
        }
        return html.append("</html>").toString();
    }

    private static String hex(Color color) {
        return String.format(Locale.ROOT, "%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    private static String escape(char c) {
        return switch (c) {
            case '&' -> "&amp;";
            case '<' -> "&lt;";
            case '>' -> "&gt;";
            default -> String.valueOf(c);
        };
    }
}
