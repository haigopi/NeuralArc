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
import java.util.ArrayList;
import java.util.List;

/**
 * A horizontal bar chart for non-negative magnitudes (e.g. exposure by symbol). Each row shows a
 * label, a proportional bar scaled to the largest value, and a trailing value/percent caption.
 * Pure custom painting — no third-party charting dependency.
 */
public final class HorizontalBarChart extends JComponent {
    public record Bar(String label, double value, String valueCaption, Color color) {}

    private static final int ROW_HEIGHT = 26;
    private static final int LABEL_WIDTH = 84;
    private static final int CAPTION_WIDTH = 120;

    private final List<Bar> bars = new ArrayList<>();
    private final double max;

    public HorizontalBarChart(List<Bar> bars) {
        if (bars != null) {
            this.bars.addAll(bars);
        }
        this.max = this.bars.stream().mapToDouble(b -> Math.abs(b.value())).max().orElse(0.0);
        int height = Math.max(ROW_HEIGHT, this.bars.size() * ROW_HEIGHT + 6);
        setPreferredSize(new Dimension(360, height));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            if (bars.isEmpty()) {
                drawEmpty(g2, w);
                return;
            }
            int barAreaX = LABEL_WIDTH;
            int barAreaW = Math.max(20, w - LABEL_WIDTH - CAPTION_WIDTH);
            g2.setFont(FontLoader.ui(Font.PLAIN, 11f));
            FontMetrics fm = g2.getFontMetrics();

            int y = 3;
            for (Bar bar : bars) {
                int barH = ROW_HEIGHT - 10;
                int cy = y + (ROW_HEIGHT - barH) / 2;

                g2.setColor(ChartPalette.TEXT_PRIMARY);
                g2.setFont(FontLoader.ui(Font.BOLD, 11f));
                g2.drawString(clip(g2, bar.label(), LABEL_WIDTH - 8), 0, cy + barH - 4);

                // Track + value bar.
                g2.setColor(ChartPalette.GRID);
                g2.fillRoundRect(barAreaX, cy, barAreaW, barH, 6, 6);
                int len = max <= 0 ? 0 : (int) Math.round(barAreaW * (Math.abs(bar.value()) / max));
                len = Math.max(bar.value() == 0 ? 0 : 3, len);
                g2.setColor(bar.color() == null ? ChartPalette.ACCENT : bar.color());
                g2.fillRoundRect(barAreaX, cy, len, barH, 6, 6);

                g2.setColor(ChartPalette.TEXT_MUTED);
                g2.setFont(FontLoader.ui(Font.PLAIN, 11f));
                String caption = bar.valueCaption() == null ? "" : bar.valueCaption();
                g2.drawString(caption, barAreaX + barAreaW + 8, cy + barH - 3 - (barH - fm.getAscent()) / 2);
                y += ROW_HEIGHT;
            }
        } finally {
            g2.dispose();
        }
    }

    private void drawEmpty(Graphics2D g2, int w) {
        g2.setFont(FontLoader.ui(Font.PLAIN, 11f));
        g2.setColor(ChartPalette.TEXT_MUTED);
        g2.drawString("No open exposure.", 4, 18);
    }

    private String clip(Graphics2D g2, String text, int maxWidth) {
        if (text == null) return "";
        FontMetrics fm = g2.getFontMetrics();
        if (fm.stringWidth(text) <= maxWidth) return text;
        String ellipsis = "…";
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (fm.stringWidth(sb.toString() + c + ellipsis) > maxWidth) break;
            sb.append(c);
        }
        return sb + ellipsis;
    }
}
