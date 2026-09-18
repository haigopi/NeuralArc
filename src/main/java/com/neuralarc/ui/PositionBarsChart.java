package com.neuralarc.ui;

import com.neuralarc.util.FontLoader;
import com.neuralarc.util.ThemeColors;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Path2D;
import java.util.List;
import java.util.Set;

/**
 * A compact daily-candle strip for the position beside the grid: the most recent sessions that fit the
 * width, the 20- and 50-day averages, the position's own price levels, and volume underneath. Colours
 * come from the full stock chart so the two read as one; clicking opens that full chart.
 */
final class PositionBarsChart extends JComponent {
    /** Candle slot width, body plus gap. */
    static final int SLOT = 7;
    static final int MAX_BARS = 90;
    private static final int[] AVERAGES = {20, 50};
    /** The levels worth a line at this size: what was paid, where it exits for a loss, where for a gain. */
    private static final Set<StockChartLevels.Kind> LEVELS = Set.of(
            StockChartLevels.Kind.AVERAGE_COST, StockChartLevels.Kind.STOP_LOSS, StockChartLevels.Kind.TARGET);
    private static final Color UP = ThemeColors.color("NeuralArc.pnlPositive", new Color(108, 203, 129));
    private static final Color DOWN = ThemeColors.color("NeuralArc.pnlNegative", new Color(240, 113, 120));
    private static final Color MUTED = ThemeColors.color("NeuralArc.Detail.titleForeground", new Color(157, 166, 179));
    private static final Color GRID = new Color(255, 255, 255, 20);
    private static final int RIGHT_AXIS = 54;
    private static final int VOLUME_SHARE_PERCENT = 18;
    private static final Stroke DASHED = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
            new float[]{4f, 3f}, 0f);

    private StockChartData data;

    PositionBarsChart() {
        setOpaque(false);
        setPreferredSize(new Dimension(360, 150));
        setMinimumSize(new Dimension(160, 90));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    void setData(StockChartData data) {
        this.data = data;
        repaint();
    }

    StockChartData data() {
        return data;
    }

    /** How many of the latest sessions fit {@code plotWidth}, never more than there are. */
    static int visibleBars(int plotWidth, int available) {
        return Math.max(0, Math.min(Math.min(available, MAX_BARS), plotWidth / SLOT));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        if (data == null || data.isEmpty()) {
            return;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            paintBars(g);
        } finally {
            g.dispose();
        }
    }

    private void paintBars(Graphics2D g) {
        int width = getWidth() - RIGHT_AXIS;
        int height = getHeight();
        int count = visibleBars(width, data.size());
        if (count < 2 || height < 40) {
            return;
        }
        int from = data.size() - count;
        int volumeHeight = height * VOLUME_SHARE_PERCENT / 100;
        int priceBottom = height - volumeHeight - 4;
        int priceTop = 4;

        double low = Double.MAX_VALUE;
        double high = -Double.MAX_VALUE;
        double maxVolume = 0;
        for (int i = from; i < data.size(); i++) {
            low = Math.min(low, data.lows()[i]);
            high = Math.max(high, data.highs()[i]);
            maxVolume = Math.max(maxVolume, data.volumes()[i]);
        }
        List<StockChartLevels.Level> levels = data.levels().stream().filter(level -> LEVELS.contains(level.kind())).toList();
        for (StockChartLevels.Level level : levels) {
            // Keep a level in view only when it is near the action; a far-off target would flatten the candles.
            if (level.price() > low * 0.8 && level.price() < high * 1.25) {
                low = Math.min(low, level.price());
                high = Math.max(high, level.price());
            }
        }
        double pad = (high - low) * 0.06;
        low -= pad;
        high += pad;

        g.setFont(FontLoader.ui(Font.PLAIN, 10f));
        FontMetrics metrics = g.getFontMetrics();
        for (int i = 0; i <= 2; i++) {
            double price = low + (high - low) * i / 2.0;
            int y = y(price, low, high, priceTop, priceBottom);
            g.setColor(GRID);
            g.drawLine(0, y, width, y);
            g.setColor(MUTED);
            g.drawString(StockChartFormat.money(price), width + 6, y + metrics.getAscent() / 2 - 1);
        }

        for (int i = from; i < data.size(); i++) {
            int x = (i - from) * SLOT + SLOT / 2;
            boolean up = data.closes()[i] >= data.opens()[i];
            Color color = up ? UP : DOWN;
            g.setColor(color);
            g.drawLine(x, y(data.highs()[i], low, high, priceTop, priceBottom), x, y(data.lows()[i], low, high, priceTop, priceBottom));
            int open = y(data.opens()[i], low, high, priceTop, priceBottom);
            int close = y(data.closes()[i], low, high, priceTop, priceBottom);
            g.fillRect(x - 2, Math.min(open, close), 5, Math.max(1, Math.abs(open - close)));
            if (maxVolume > 0) {
                int bar = (int) Math.round(data.volumes()[i] / maxVolume * volumeHeight);
                g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 110));
                g.fillRect(x - 2, height - bar, 5, bar);
            }
        }

        g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int period : AVERAGES) {
            double[] ema = data.emas().get(period);
            if (ema == null) {
                continue;
            }
            Path2D.Double path = new Path2D.Double();
            boolean started = false;
            for (int i = from; i < data.size(); i++) {
                if (Double.isNaN(ema[i])) {
                    continue;
                }
                double x = (i - from) * SLOT + SLOT / 2.0;
                double yy = y(ema[i], low, high, priceTop, priceBottom);
                if (started) {
                    path.lineTo(x, yy);
                } else {
                    path.moveTo(x, yy);
                    started = true;
                }
            }
            g.setColor(StockChartPanel.emaColor(period));
            g.draw(path);
        }

        g.setStroke(DASHED);
        int offScaleAbove = 0;
        int offScaleBelow = 0;
        for (StockChartLevels.Level level : levels) {
            if (level.price() < low || level.price() > high) {
                // Too far to draw without flattening the candles, but still worth knowing: for a losing
                // position the average cost is usually exactly this far above. Name it at the edge.
                boolean above = level.price() > high;
                String marker = shortLabel(level.kind()) + " " + StockChartFormat.money(level.price()) + (above ? " ↑" : " ↓");
                int markerY = above
                        ? priceTop + metrics.getAscent() + offScaleAbove++ * metrics.getHeight()
                        : priceBottom - 2 - offScaleBelow++ * metrics.getHeight();
                g.setColor(StockChartPanel.levelColor(level.kind()));
                g.drawString(marker, width - metrics.stringWidth(marker) - 2, markerY);
                continue;
            }
            int y = y(level.price(), low, high, priceTop, priceBottom);
            Color color = StockChartPanel.levelColor(level.kind());
            g.setColor(color);
            g.drawLine(0, y, width, y);
            String label = shortLabel(level.kind());
            g.drawString(label, width - metrics.stringWidth(label) - 2, y - 2);
        }
    }

    private static String shortLabel(StockChartLevels.Kind kind) {
        return switch (kind) {
            case AVERAGE_COST -> "Avg cost";
            case STOP_LOSS -> "Stop";
            case TARGET -> "Target";
            default -> "";
        };
    }

    private static int y(double price, double low, double high, int top, int bottom) {
        double fraction = high == low ? 0.5 : (price - low) / (high - low);
        return (int) Math.round(bottom - fraction * (bottom - top));
    }
}
