package com.neuralarc.ui;

import java.awt.Color;
import java.util.Locale;

/**
 * Renders a string as rainbow-colored HTML for Swing labels. The spectrum is applied once across
 * the full visible text so the gradient reads as one seamless band rather than a repeating pattern.
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
        int visibleCount = visibleCharacterCount(text);
        StringBuilder html = new StringBuilder("<html>");
        int visibleIndex = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                html.append(c == '\n' ? "<br>" : "&nbsp;");
                continue;
            }
            Color color = gradientColor(visibleIndex, visibleCount);
            visibleIndex++;
            html.append("<font color='#")
                    .append(hex(color))
                    .append("'>")
                    .append(escape(c))
                    .append("</font>");
        }
        return html.append("</html>").toString();
    }

    private static int visibleCharacterCount(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                count++;
            }
        }
        return count;
    }

    private static Color gradientColor(int visibleIndex, int visibleCount) {
        if (visibleCount <= 1) {
            return SPECTRUM[0];
        }
        double scaled = (visibleIndex / (double) (visibleCount - 1)) * (SPECTRUM.length - 1);
        int lower = (int) Math.floor(scaled);
        int upper = Math.min(SPECTRUM.length - 1, lower + 1);
        double fraction = scaled - lower;
        return blend(SPECTRUM[lower], SPECTRUM[upper], fraction);
    }

    private static Color blend(Color start, Color end, double fraction) {
        int red = (int) Math.round(start.getRed() + (end.getRed() - start.getRed()) * fraction);
        int green = (int) Math.round(start.getGreen() + (end.getGreen() - start.getGreen()) * fraction);
        int blue = (int) Math.round(start.getBlue() + (end.getBlue() - start.getBlue()) * fraction);
        return new Color(red, green, blue);
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
