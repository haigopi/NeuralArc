package com.neuralarc.ui.chart;

import com.neuralarc.util.FontLoader;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.List;

/**
 * A donut chart with a side legend, used for exposure share by strategy/workspace. The hole shows the
 * slice count. Pure custom painting — no third-party charting dependency.
 */
public final class DonutChart extends JComponent {
    public record Slice(String label, double value, String caption) {}

    private final List<Slice> slices = new ArrayList<>();
    private final double total;

    public DonutChart(List<Slice> slices) {
        if (slices != null) {
            for (Slice s : slices) {
                if (s != null && s.value() > 0) {
                    this.slices.add(s);
                }
            }
        }
        this.total = this.slices.stream().mapToDouble(Slice::value).sum();
        int height = Math.max(150, 24 + this.slices.size() * 20);
        setPreferredSize(new Dimension(360, height));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            if (slices.isEmpty() || total <= 0) {
                g2.setFont(FontLoader.ui(Font.PLAIN, 11f));
                g2.setColor(ChartPalette.TEXT_MUTED);
                g2.drawString("No open exposure.", 4, 18);
                return;
            }

            int diameter = Math.min(h - 8, Math.max(96, w / 2 - 16));
            int cx = 6;
            int cy = (h - diameter) / 2;

            double startAngle = 90.0;
            for (int i = 0; i < slices.size(); i++) {
                double extent = -360.0 * (slices.get(i).value() / total);
                g2.setColor(ChartPalette.series(i));
                g2.fill(new Arc2D.Double(cx, cy, diameter, diameter, startAngle, extent, Arc2D.PIE));
                startAngle += extent;
            }
            // Punch the hole.
            int hole = (int) (diameter * 0.56);
            g2.setColor(ChartPalette.CARD_BG);
            g2.fill(new Ellipse2D.Double(cx + (diameter - hole) / 2.0, cy + (diameter - hole) / 2.0, hole, hole));
            g2.setColor(ChartPalette.TEXT_PRIMARY);
            g2.setFont(FontLoader.ui(Font.BOLD, 16f));
            String center = String.valueOf(slices.size());
            FontMetrics cm = g2.getFontMetrics();
            int textX = cx + diameter / 2 - cm.stringWidth(center) / 2;
            g2.drawString(center, textX, cy + diameter / 2 + cm.getAscent() / 2 - 4);
            g2.setFont(FontLoader.ui(Font.PLAIN, 9f));
            g2.setColor(ChartPalette.TEXT_MUTED);
            String unit = slices.size() == 1 ? "strategy" : "strategies";
            int unitX = cx + diameter / 2 - g2.getFontMetrics().stringWidth(unit) / 2;
            g2.drawString(unit, unitX, cy + diameter / 2 + cm.getAscent() / 2 + 8);

            // Legend.
            int legendX = cx + diameter + 18;
            int legendY = cy + 6;
            g2.setStroke(new BasicStroke(1f));
            for (int i = 0; i < slices.size(); i++) {
                Slice s = slices.get(i);
                Color color = ChartPalette.series(i);
                g2.setColor(color);
                g2.fillRoundRect(legendX, legendY - 9, 10, 10, 3, 3);
                g2.setColor(ChartPalette.TEXT_PRIMARY);
                g2.setFont(FontLoader.ui(Font.BOLD, 11f));
                g2.drawString(clip(g2, s.label(), w - legendX - 96), legendX + 16, legendY);
                g2.setColor(ChartPalette.TEXT_MUTED);
                g2.setFont(FontLoader.ui(Font.PLAIN, 10f));
                String caption = s.caption() == null ? "" : s.caption();
                g2.drawString(caption, legendX + 16, legendY + 12);
                legendY += 26;
            }
        } finally {
            g2.dispose();
        }
    }

    private String clip(Graphics2D g2, String text, int maxWidth) {
        if (text == null) return "";
        FontMetrics fm = g2.getFontMetrics();
        if (maxWidth <= 0 || fm.stringWidth(text) <= maxWidth) return text;
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (fm.stringWidth(sb.toString() + c + "…") > maxWidth) break;
            sb.append(c);
        }
        return sb + "…";
    }
}
