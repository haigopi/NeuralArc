package com.neuralarc.ui.chart;

import com.neuralarc.util.FontLoader;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * A single key-metric tile: a small muted title, a large value in an accent color, and an optional
 * sub-line. Custom-painted (no chart library) so it matches the rest of the dashboard exactly.
 */
public final class KpiCard extends JComponent {
    private final String title;
    private final String value;
    private final Color valueColor;
    private final String sub;

    public KpiCard(String title, String value, Color valueColor, String sub) {
        this.title = title == null ? "" : title;
        this.value = value == null ? "" : value;
        this.valueColor = valueColor == null ? ChartPalette.TEXT_PRIMARY : valueColor;
        this.sub = sub == null ? "" : sub;
        setPreferredSize(new Dimension(190, 92));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();

            g2.setColor(ChartPalette.CARD_BG);
            g2.fillRoundRect(0, 0, w - 1, h - 1, 14, 14);
            g2.setColor(ChartPalette.CARD_BORDER);
            g2.drawRoundRect(0, 0, w - 1, h - 1, 14, 14);

            // Accent rail on the left edge in the value color.
            g2.setColor(valueColor);
            g2.fillRoundRect(0, 10, 4, h - 21, 4, 4);

            int x = 16;
            g2.setFont(FontLoader.ui(Font.BOLD, 10f));
            g2.setColor(ChartPalette.TEXT_MUTED);
            g2.drawString(title.toUpperCase(), x, 22);

            g2.setFont(FontLoader.ui(Font.BOLD, 22f));
            g2.setColor(valueColor);
            FontMetrics vm = g2.getFontMetrics();
            g2.drawString(value, x, 22 + vm.getAscent() + 6);

            if (!sub.isBlank()) {
                g2.setFont(FontLoader.ui(Font.PLAIN, 10f));
                g2.setColor(ChartPalette.TEXT_MUTED);
                g2.drawString(sub, x, h - 12);
            }
        } finally {
            g2.dispose();
        }
    }
}
