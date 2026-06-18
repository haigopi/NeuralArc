package com.neuralarc.ui.chart;

import com.neuralarc.util.FontLoader;

import javax.swing.JComponent;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

/**
 * A diverging horizontal bar chart around a centered zero baseline: positive values extend right in
 * green, negative values extend left in red. Used for open P&amp;L by symbol so winners and losers are
 * legible at a glance. Pure custom painting — no third-party charting dependency.
 */
public final class DivergingBarChart extends JComponent {
    public record Bar(String label, double value, String valueCaption) {}

    private static final int ROW_HEIGHT = 26;
    private static final int LABEL_WIDTH = 72;
    private static final int CAPTION_WIDTH = 96;

    private final List<Bar> bars = new ArrayList<>();
    private final double maxAbs;

    public DivergingBarChart(List<Bar> bars) {
        if (bars != null) {
            this.bars.addAll(bars);
        }
        this.maxAbs = this.bars.stream().mapToDouble(b -> Math.abs(b.value())).max().orElse(0.0);
        int height = Math.max(ROW_HEIGHT, this.bars.size() * ROW_HEIGHT + 6);
        setPreferredSize(new Dimension(420, height));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            if (bars.isEmpty()) {
                g2.setFont(FontLoader.ui(Font.PLAIN, 11f));
                g2.setColor(ChartPalette.TEXT_MUTED);
                g2.drawString("No open positions.", 4, 18);
                return;
            }
            int plotX = LABEL_WIDTH;
            int plotW = Math.max(40, w - LABEL_WIDTH - CAPTION_WIDTH);
            int zeroX = plotX + plotW / 2;
            int halfW = plotW / 2;

            // Zero baseline.
            g2.setColor(ChartPalette.GRID);
            g2.drawLine(zeroX, 2, zeroX, getHeight() - 2);

            g2.setFont(FontLoader.ui(Font.PLAIN, 11f));
            FontMetrics fm = g2.getFontMetrics();
            int y = 3;
            for (Bar bar : bars) {
                int barH = ROW_HEIGHT - 10;
                int cy = y + (ROW_HEIGHT - barH) / 2;

                g2.setColor(ChartPalette.TEXT_PRIMARY);
                g2.setFont(FontLoader.ui(Font.BOLD, 11f));
                g2.drawString(bar.label(), 0, cy + barH - 4);

                int len = maxAbs <= 0 ? 0 : (int) Math.round(halfW * (Math.abs(bar.value()) / maxAbs));
                if (bar.value() > 0) {
                    g2.setColor(ChartPalette.POSITIVE);
                    g2.fillRoundRect(zeroX, cy, Math.max(2, len), barH, 5, 5);
                } else if (bar.value() < 0) {
                    g2.setColor(ChartPalette.NEGATIVE);
                    g2.fillRoundRect(zeroX - Math.max(2, len), cy, Math.max(2, len), barH, 5, 5);
                }

                g2.setColor(ChartPalette.signColor(bar.value()));
                g2.setFont(FontLoader.ui(Font.BOLD, 11f));
                String caption = bar.valueCaption() == null ? "" : bar.valueCaption();
                g2.drawString(caption, plotX + plotW + 8, cy + barH - 3 - (barH - fm.getAscent()) / 2);
                y += ROW_HEIGHT;
            }
        } finally {
            g2.dispose();
        }
    }
}
