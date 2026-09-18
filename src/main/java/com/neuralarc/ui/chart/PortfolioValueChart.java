package com.neuralarc.ui.chart;

import com.formdev.flatlaf.FlatLaf;
import com.neuralarc.model.PortfolioValueSample;
import com.neuralarc.util.FontLoader;
import com.neuralarc.util.Monetary;

import javax.swing.JComponent;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Today's total portfolio value as a single line, one point per minute.
 *
 * <p>The header states the latest value and the change since the day's first sample, with an explicit
 * sign so the direction never rests on colour alone. The plot keeps a thin line over a faint area,
 * three recessive gridlines and time labels at the ends; hovering shows a crosshair and the exact
 * reading for that minute. One series, so the title names it and there is no legend.
 */
public final class PortfolioValueChart extends JComponent {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.US);
    private static final int LEFT_AXIS = 58;
    private static final int HEADER = 40;
    private static final int BOTTOM_AXIS = 20;

    private final ZoneId displayZone;
    private List<PortfolioValueSample> samples = List.of();
    private String scopeLabel = "";
    private int hoverIndex = -1;

    public PortfolioValueChart(ZoneId displayZone) {
        this.displayZone = displayZone == null ? ZoneId.systemDefault() : displayZone;
        setOpaque(false);
        setPreferredSize(new Dimension(420, 180));
        setMinimumSize(new Dimension(220, 120));
        getAccessibleContext().setAccessibleName("Portfolio value today");
        MouseAdapter hover = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                setHoverIndex(indexAt(event.getX()));
            }

            @Override
            public void mouseExited(MouseEvent event) {
                setHoverIndex(-1);
            }
        };
        addMouseListener(hover);
        addMouseMotionListener(hover);
    }

    /** A bare JComponent has no accessible context; screen readers get the headline through this one. */
    @Override
    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJComponent() {
                @Override
                public javax.accessibility.AccessibleRole getAccessibleRole() {
                    return javax.accessibility.AccessibleRole.PANEL;
                }
            };
        }
        return accessibleContext;
    }

    /** Replaces the series; {@code scopeLabel} names what it covers, e.g. "Paper · all workspaces". */
    public void setSamples(List<PortfolioValueSample> samples, String scopeLabel) {
        List<PortfolioValueSample> next = samples == null ? List.of() : List.copyOf(samples);
        String nextLabel = scopeLabel == null ? "" : scopeLabel;
        if (next.equals(this.samples) && nextLabel.equals(this.scopeLabel)) {
            return;
        }
        this.samples = next;
        this.scopeLabel = nextLabel;
        if (hoverIndex >= next.size()) {
            hoverIndex = -1;
        }
        getAccessibleContext().setAccessibleDescription(headline());
        repaint();
    }

    List<PortfolioValueSample> samples() {
        return samples;
    }

    /** "$20,512.40  +$312.10 (+1.54%) today", or a waiting message before the first sample. */
    public String headline() {
        if (samples.isEmpty()) {
            return "Waiting for the first reading";
        }
        PortfolioValueSample last = samples.get(samples.size() - 1);
        return money(last.marketValue()) + "  " + change(samples.get(0).marketValue(), last.marketValue()) + " today";
    }

    /** Signed change from {@code start} to {@code value}, with its percent: "+$312.10 (+1.54%)". */
    static String change(BigDecimal start, BigDecimal value) {
        BigDecimal delta = Monetary.round(value.subtract(start));
        String sign = delta.signum() < 0 ? "-" : "+";
        String text = sign + "$" + String.format(Locale.US, "%,.2f", delta.abs());
        if (start.signum() > 0) {
            BigDecimal percent = delta.multiply(BigDecimal.valueOf(100)).divide(start, 2, RoundingMode.HALF_UP);
            text += " (" + sign + percent.abs().toPlainString() + "%)";
        }
        return text;
    }

    /** Index of the sample nearest to plot x-coordinate {@code x}, or -1 with nothing to point at. */
    int indexAt(int x) {
        if (samples.isEmpty()) {
            return -1;
        }
        Insets insets = getInsets();
        int left = insets.left + LEFT_AXIS;
        int width = getWidth() - left - insets.right - 8;
        if (width <= 0 || samples.size() == 1) {
            return 0;
        }
        double fraction = Math.max(0, Math.min(1, (x - left) / (double) width));
        long start = samples.get(0).minute().getEpochSecond();
        long span = samples.get(samples.size() - 1).minute().getEpochSecond() - start;
        long target = start + Math.round(fraction * span);
        int best = 0;
        for (int i = 1; i < samples.size(); i++) {
            long distance = Math.abs(samples.get(i).minute().getEpochSecond() - target);
            if (distance < Math.abs(samples.get(best).minute().getEpochSecond() - target)) {
                best = i;
            }
        }
        return best;
    }

    private void setHoverIndex(int index) {
        if (index != hoverIndex) {
            hoverIndex = index;
            repaint();
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Insets insets = getInsets();
            int x0 = insets.left + 8;
            int y0 = insets.top + 6;
            int right = getWidth() - insets.right - 8;
            paintHeader(g, x0, y0, right);
            int plotLeft = insets.left + LEFT_AXIS;
            int plotTop = y0 + HEADER;
            int plotBottom = getHeight() - insets.bottom - BOTTOM_AXIS;
            if (plotBottom - plotTop < 30 || right - plotLeft < 60) {
                return;
            }
            if (samples.isEmpty()) {
                g.setFont(FontLoader.ui(Font.PLAIN, 11f));
                g.setColor(mutedText());
                String message = "The first point appears within a minute.";
                FontMetrics metrics = g.getFontMetrics();
                g.drawString(message, (plotLeft + right - metrics.stringWidth(message)) / 2, (plotTop + plotBottom) / 2);
                return;
            }
            paintPlot(g, plotLeft, plotTop, right, plotBottom);
        } finally {
            g.dispose();
        }
    }

    private void paintHeader(Graphics2D g, int x, int y, int right) {
        g.setFont(FontLoader.ui(Font.BOLD, 12f));
        FontMetrics titleMetrics = g.getFontMetrics();
        g.setColor(primaryText());
        g.drawString("Portfolio Value", x, y + titleMetrics.getAscent());
        int afterTitle = x + titleMetrics.stringWidth("Portfolio Value") + 8;
        g.setFont(FontLoader.ui(Font.PLAIN, 11f));
        g.setColor(mutedText());
        g.drawString("Today" + (scopeLabel.isBlank() ? "" : " · " + scopeLabel), afterTitle, y + titleMetrics.getAscent());
        if (samples.isEmpty()) {
            return;
        }
        PortfolioValueSample last = samples.get(samples.size() - 1);
        int line2 = y + titleMetrics.getHeight() + g.getFontMetrics().getAscent() + 2;
        g.setFont(FontLoader.ui(Font.BOLD, 15f));
        String value = money(last.marketValue());
        g.setColor(primaryText());
        g.drawString(value, x, line2 + 2);
        int afterValue = x + g.getFontMetrics().stringWidth(value) + 10;
        g.setFont(FontLoader.ui(Font.BOLD, 11f));
        BigDecimal delta = last.marketValue().subtract(samples.get(0).marketValue());
        g.setColor(ChartPalette.signColor(delta.doubleValue()));
        g.drawString(change(samples.get(0).marketValue(), last.marketValue()), afterValue, line2 + 2);
    }

    private void paintPlot(Graphics2D g, int left, int top, int right, int bottom) {
        double min = samples.stream().mapToDouble(s -> s.marketValue().doubleValue()).min().orElse(0);
        double max = samples.stream().mapToDouble(s -> s.marketValue().doubleValue()).max().orElse(0);
        double pad = Math.max((max - min) * 0.12, Math.max(1, Math.abs(max) * 0.002));
        double low = min - pad;
        double high = max + pad;

        g.setFont(FontLoader.ui(Font.PLAIN, 10f));
        FontMetrics metrics = g.getFontMetrics();
        for (int i = 0; i <= 2; i++) {
            double value = low + (high - low) * i / 2.0;
            int y = yFor(value, low, high, top, bottom);
            g.setColor(gridColor());
            g.drawLine(left, y, right, y);
            g.setColor(mutedText());
            // On a quiet day "$20.5K" would repeat on every gridline; whole dollars keep them distinct.
            String label = high - low < 300 ? String.format(Locale.US, "$%,.0f", value) : compactMoney(value);
            g.drawString(label, left - 6 - metrics.stringWidth(label), y + metrics.getAscent() / 2 - 1);
        }

        long start = samples.get(0).minute().getEpochSecond();
        long span = Math.max(1, samples.get(samples.size() - 1).minute().getEpochSecond() - start);
        int[] xs = new int[samples.size()];
        int[] ys = new int[samples.size()];
        for (int i = 0; i < samples.size(); i++) {
            xs[i] = samples.size() == 1
                    ? right
                    : left + (int) Math.round((samples.get(i).minute().getEpochSecond() - start) * (right - left) / (double) span);
            ys[i] = yFor(samples.get(i).marketValue().doubleValue(), low, high, top, bottom);
        }

        Color line = lineColor();
        if (samples.size() > 1) {
            Path2D.Double area = new Path2D.Double();
            Path2D.Double path = new Path2D.Double();
            area.moveTo(xs[0], bottom);
            for (int i = 0; i < xs.length; i++) {
                area.lineTo(xs[i], ys[i]);
                if (i == 0) {
                    path.moveTo(xs[i], ys[i]);
                } else {
                    path.lineTo(xs[i], ys[i]);
                }
            }
            area.lineTo(xs[xs.length - 1], bottom);
            area.closePath();
            g.setPaint(new GradientPaint(0, top, withAlpha(line, 70), 0, bottom, withAlpha(line, 0)));
            g.fill(area);
            g.setColor(line);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(path);
        }
        int lastIndex = samples.size() - 1;
        dot(g, xs[lastIndex], ys[lastIndex], line);

        g.setColor(mutedText());
        String first = TIME.format(samples.get(0).minute().atZone(displayZone));
        String last = TIME.format(samples.get(lastIndex).minute().atZone(displayZone));
        int labelY = bottom + metrics.getAscent() + 4;
        g.drawString(first, left, labelY);
        if (lastIndex > 0 && right - metrics.stringWidth(last) > left + metrics.stringWidth(first) + 12) {
            g.drawString(last, right - metrics.stringWidth(last), labelY);
        }

        if (hoverIndex >= 0 && hoverIndex < samples.size()) {
            paintHover(g, xs[hoverIndex], ys[hoverIndex], top, bottom, left, right, line);
        }
    }

    private void paintHover(Graphics2D g, int x, int y, int top, int bottom, int left, int right, Color line) {
        g.setColor(withAlpha(mutedText(), 150));
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{3f, 3f}, 0f));
        g.drawLine(x, top, x, bottom);
        g.setStroke(new BasicStroke(1f));
        dot(g, x, y, line);

        PortfolioValueSample sample = samples.get(hoverIndex);
        String[] rows = {
                TIME.format(sample.minute().atZone(displayZone)),
                money(sample.marketValue()),
                change(samples.get(0).marketValue(), sample.marketValue()) + " since first reading",
                "Unrealized P/L " + signedMoney(sample.unrealizedPnl())
        };
        g.setFont(FontLoader.ui(Font.PLAIN, 11f));
        FontMetrics metrics = g.getFontMetrics();
        int width = 0;
        for (String row : rows) {
            width = Math.max(width, metrics.stringWidth(row));
        }
        width += 16;
        int height = rows.length * metrics.getHeight() + 10;
        int boxX = x + 12 + width > right ? x - 12 - width : x + 12;
        boxX = Math.max(left, boxX);
        int boxY = Math.max(top, Math.min(y - height / 2, bottom - height));
        g.setColor(tooltipBackground());
        g.fillRoundRect(boxX, boxY, width, height, 8, 8);
        g.setColor(gridColor());
        g.drawRoundRect(boxX, boxY, width, height, 8, 8);
        int textY = boxY + 5 + metrics.getAscent();
        for (int i = 0; i < rows.length; i++) {
            g.setFont(FontLoader.ui(i == 1 ? Font.BOLD : Font.PLAIN, 11f));
            g.setColor(i == 1 ? primaryText() : mutedText());
            g.drawString(rows[i], boxX + 8, textY);
            textY += metrics.getHeight();
        }
    }

    private void dot(Graphics2D g, int x, int y, Color fill) {
        g.setColor(tooltipBackground());
        g.fillOval(x - 6, y - 6, 12, 12);
        g.setColor(fill);
        g.fillOval(x - 4, y - 4, 8, 8);
    }

    private static int yFor(double value, double low, double high, int top, int bottom) {
        double fraction = high == low ? 0.5 : (value - low) / (high - low);
        return (int) Math.round(bottom - fraction * (bottom - top));
    }

    static String compactMoney(double value) {
        double abs = Math.abs(value);
        String sign = value < 0 ? "-" : "";
        if (abs >= 1_000_000) {
            return sign + "$" + String.format(Locale.US, "%.2fM", abs / 1_000_000);
        }
        if (abs >= 10_000) {
            return sign + "$" + String.format(Locale.US, "%.1fK", abs / 1_000);
        }
        return sign + "$" + String.format(Locale.US, "%,.0f", abs);
    }

    private static String money(BigDecimal value) {
        return (value.signum() < 0 ? "-$" : "$") + String.format(Locale.US, "%,.2f", Monetary.round(value).abs());
    }

    private static String signedMoney(BigDecimal value) {
        return (value.signum() < 0 ? "-$" : "+$") + String.format(Locale.US, "%,.2f", Monetary.round(value).abs());
    }

    private static boolean dark() {
        return FlatLaf.isLafDark();
    }

    private static Color primaryText() {
        Color color = UIManager.getColor("Label.foreground");
        return color == null ? ChartPalette.TEXT_PRIMARY : color;
    }

    private static Color mutedText() {
        return dark() ? new Color(150, 156, 168) : ChartPalette.TEXT_MUTED;
    }

    private static Color gridColor() {
        return dark() ? new Color(255, 255, 255, 28) : ChartPalette.GRID;
    }

    private static Color lineColor() {
        // The dashboard accent, lifted for the dark surface so the 2px line keeps its contrast.
        return dark() ? new Color(102, 144, 227) : ChartPalette.ACCENT;
    }

    private static Color tooltipBackground() {
        Color panel = UIManager.getColor("Panel.background");
        return panel == null ? Color.WHITE : panel;
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }
}
