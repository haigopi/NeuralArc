package com.neuralarc.ui;

import com.neuralarc.analytics.SwingPoints;
import com.neuralarc.util.FontLoader;
import com.neuralarc.util.ThemeColors;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleFunction;

/**
 * A daily stock chart in five stacked panes sharing one time axis: price with moving averages,
 * turning points and the strategy's own levels; RSI; volume; MACD; and accumulation/distribution.
 * Paints only from the {@link StockChartData} it was given — never from the broker.
 */
final class StockChartPanel extends JComponent {
    static final int SIX_MONTHS = 126;
    static final int ONE_YEAR = 252;
    static final int THREE_YEARS = 756;

    private static final Color BACKGROUND = ThemeColors.color("NeuralArc.Detail.background", new Color(43, 46, 54));
    private static final Color GRID = new Color(255, 255, 255, 20);
    private static final Color MUTED = ThemeColors.color("NeuralArc.Detail.titleForeground", new Color(157, 166, 179));
    private static final Color TEXT = ThemeColors.color("NeuralArc.Detail.foreground", new Color(213, 218, 226));
    private static final Color UP = ThemeColors.color("NeuralArc.pnlPositive", new Color(108, 203, 129));
    private static final Color DOWN = ThemeColors.color("NeuralArc.pnlNegative", new Color(240, 113, 120));
    private static final Color RSI_LINE = new Color(179, 157, 219);
    private static final Color RSI_BAND = new Color(179, 157, 219, 26);
    private static final Color MACD_LINE = new Color(79, 195, 247);
    private static final Color SIGNAL_LINE = new Color(255, 167, 38);
    private static final Color ACCUMULATION_LINE = new Color(144, 164, 174);
    private static final Color VOLUME_AVERAGE = new Color(255, 213, 79);
    private static final Color CHIP_TEXT = new Color(24, 27, 33);
    private static final Color CROSSHAIR = new Color(255, 255, 255, 90);
    private static final Color READOUT_BACKGROUND = new Color(24, 27, 33, 225);

    private static final Font LABEL_FONT = FontLoader.ui(Font.PLAIN, 10f);
    private static final Font TITLE_FONT = FontLoader.ui(Font.BOLD, 10f);
    private static final Font SYMBOL_FONT = FontLoader.ui(Font.BOLD, 12f);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM", Locale.US);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.US);

    private static final int LEFT = 8;
    private static final int RIGHT_AXIS = 62;
    private static final int TOP = 6;
    private static final int DATE_AXIS = 20;
    private static final int PANE_GAP = 8;
    private static final double[] PANE_WEIGHTS = {0.46, 0.12, 0.14, 0.14, 0.14};
    /** Height of the symbol and moving-average legend across the top of the price pane. */
    private static final int LEGEND_BAND = 18;
    /** Room kept above the highest high and below the lowest low, so their labels are never cut off. */
    private static final int TOP_HEADROOM = LEGEND_BAND + 18;
    private static final int BOTTOM_HEADROOM = 18;
    private static final Stroke THIN = new BasicStroke(1f);
    private static final Stroke LINE = new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final Stroke DASHED = new BasicStroke(1.1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
            new float[]{6f, 4f}, 0f);

    private final StockChartData data;
    private int visibleBars;
    private int hoverIndex = -1;

    StockChartPanel(StockChartData data) {
        this.data = data;
        this.visibleBars = Math.min(data.size(), ONE_YEAR);
        setOpaque(true);
        setPreferredSize(new Dimension(820, 640));
        HoverTracker hover = new HoverTracker();
        addMouseListener(hover);
        addMouseMotionListener(hover);
    }

    static Color emaColor(int period) {
        return switch (period) {
            case 9 -> new Color(79, 195, 247);
            case 13 -> new Color(239, 83, 80);
            case 20 -> new Color(102, 187, 106);
            case 50 -> new Color(236, 64, 122);
            case 100 -> new Color(171, 71, 188);
            default -> new Color(255, 167, 38);
        };
    }

    static Color levelColor(StockChartLevels.Kind kind) {
        return switch (kind) {
            case AVERAGE_COST -> new Color(255, 213, 79);
            case BASE_BUY, LOSS_BUY, WORKING_BUY -> new Color(100, 181, 246);
            case STOP_LOSS -> DOWN;
            case TARGET, WORKING_SELL -> UP;
        };
    }

    /** Shows the most recent {@code bars} trading days; zero or less shows all of them. */
    void showLast(int bars) {
        visibleBars = bars <= 0 ? data.size() : Math.min(bars, data.size());
        hoverIndex = -1;
        repaint();
    }

    int visibleBars() {
        return visibleBars;
    }

    void setHoverIndex(int index) {
        hoverIndex = index;
        repaint();
    }

    private int firstVisible() {
        return data.size() - visibleBars;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(BACKGROUND);
            g.fillRect(0, 0, getWidth(), getHeight());
            if (data.isEmpty() || visibleBars <= 0) {
                return;
            }
            Rectangle[] panes = layoutPanes();
            paintPrice(g, panes[0]);
            paintRsi(g, panes[1]);
            paintVolume(g, panes[2]);
            paintMacd(g, panes[3]);
            paintAccumulation(g, panes[4]);
            paintDateAxis(g, panes[4]);
            paintCrosshair(g, panes);
        } finally {
            g.dispose();
        }
    }

    private Rectangle[] layoutPanes() {
        int plotWidth = Math.max(1, getWidth() - LEFT - RIGHT_AXIS);
        int available = Math.max(PANE_WEIGHTS.length, getHeight() - TOP - DATE_AXIS - PANE_GAP * (PANE_WEIGHTS.length - 1));
        Rectangle[] panes = new Rectangle[PANE_WEIGHTS.length];
        int y = TOP;
        for (int i = 0; i < PANE_WEIGHTS.length; i++) {
            int height = Math.max(1, (int) Math.round(available * PANE_WEIGHTS[i]));
            panes[i] = new Rectangle(LEFT, y, plotWidth, height);
            y += height + PANE_GAP;
        }
        return panes;
    }

    private double slot(Rectangle pane) {
        return pane.getWidth() / visibleBars;
    }

    private double xCenter(Rectangle pane, int index) {
        return pane.x + (index - firstVisible() + 0.5) * slot(pane);
    }

    private int indexAt(int x) {
        Rectangle pane = layoutPanes()[0];
        if (data.isEmpty() || x < pane.x || x > pane.x + pane.width) {
            return -1;
        }
        int index = firstVisible() + (int) ((x - pane.x) / slot(pane));
        return Math.max(firstVisible(), Math.min(data.lastIndex(), index));
    }

    private record Scale(Rectangle pane, double min, double max) {
        double y(double value) {
            double span = max - min;
            return span <= 0 ? pane.y + pane.height / 2.0 : pane.y + (max - value) / span * pane.height;
        }
    }

    // ---------------------------------------------------------------- price

    private void paintPrice(Graphics2D g, Rectangle pane) {
        int from = firstVisible();
        int to = data.lastIndex();
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (int i = from; i <= to; i++) {
            min = Math.min(min, data.lows()[i]);
            max = Math.max(max, data.highs()[i]);
        }
        for (double[] ema : data.emas().values()) {
            for (int i = from; i <= to; i++) {
                if (!Double.isNaN(ema[i])) {
                    min = Math.min(min, ema[i]);
                    max = Math.max(max, ema[i]);
                }
            }
        }
        // A level close to the price range is brought into view; a far-off one becomes an edge label
        // instead, so a distant target does not squash the candles into a thin strip.
        double span = Math.max(max - min, max * 0.02);
        for (StockChartLevels.Level level : data.levels()) {
            if (level.price() >= min - span * 0.35 && level.price() <= max + span * 0.35) {
                min = Math.min(min, level.price());
                max = Math.max(max, level.price());
            }
        }
        double range = Math.max(max - min, Math.max(max * 0.005, 0.0001));
        double pricePerPixel = range / Math.max(1, pane.height - TOP_HEADROOM - BOTTOM_HEADROOM);
        Scale scale = new Scale(pane, min - BOTTOM_HEADROOM * pricePerPixel, max + TOP_HEADROOM * pricePerPixel);

        paintGrid(g, pane, scale, StockChartFormat::price);
        paintCandles(g, pane, scale, from, to);
        for (int period : StockChartData.EMA_PERIODS) {
            paintSeries(g, pane, scale, data.emas().get(period), emaColor(period), LINE);
        }
        paintSwings(g, pane, scale, from, to);
        List<int[]> gutterRows = new ArrayList<>();
        gutterRows.add(paintLastPriceTag(g, pane, scale));
        paintLevels(g, pane, scale, gutterRows);
        paintPriceLegend(g, pane);
    }

    private void paintCandles(Graphics2D g, Rectangle pane, Scale scale, int from, int to) {
        double bodyWidth = Math.max(1, slot(pane) * 0.62);
        g.setStroke(THIN);
        for (int i = from; i <= to; i++) {
            double x = xCenter(pane, i);
            double open = data.opens()[i];
            double close = data.closes()[i];
            g.setColor(close >= open ? UP : DOWN);
            g.draw(new Line2D.Double(x, scale.y(data.highs()[i]), x, scale.y(data.lows()[i])));
            double top = scale.y(Math.max(open, close));
            double bottom = scale.y(Math.min(open, close));
            g.fill(new Rectangle2D.Double(x - bodyWidth / 2, top, bodyWidth, Math.max(1, bottom - top)));
        }
    }

    private void paintSwings(Graphics2D g, Rectangle pane, Scale scale, int from, int to) {
        g.setFont(LABEL_FONT);
        FontMetrics metrics = g.getFontMetrics();
        List<Rectangle> occupied = new ArrayList<>();
        occupied.add(new Rectangle(pane.x, pane.y, pane.width, LEGEND_BAND)); // Keep the legend readable.
        for (SwingPoints.Swing swing : data.swings()) {
            if (swing.index() < from || swing.index() > to) {
                continue;
            }
            String text = StockChartFormat.price(swing.price());
            int width = metrics.stringWidth(text);
            int x = (int) Math.round(xCenter(pane, swing.index()) - width / 2.0);
            int baseline = swing.high()
                    ? (int) Math.round(scale.y(swing.price())) - 4
                    : (int) Math.round(scale.y(swing.price())) + metrics.getAscent() + 3;
            Rectangle box = new Rectangle(x - 2, baseline - metrics.getAscent(), width + 4, metrics.getHeight());
            if (!pane.contains(box) || occupied.stream().anyMatch(box::intersects)) {
                continue;
            }
            occupied.add(box);
            g.setColor(TEXT);
            g.drawString(text, x, baseline);
        }
    }

    /**
     * Each level is a dashed line with its name at the left end and its price tagged in the right-hand
     * axis gutter, like the last-price tag, so the labels never sit on the most recent candles. A level
     * beyond the visible range becomes a note at the top or bottom left instead.
     */
    private void paintLevels(Graphics2D g, Rectangle pane, Scale scale, List<int[]> gutterRows) {
        g.setFont(LABEL_FONT);
        FontMetrics metrics = g.getFontMetrics();
        int tagHeight = metrics.getHeight() + 2;
        int aboveNotes = 0;
        int belowNotes = 0;
        for (StockChartLevels.Level level : data.levels()) {
            Color color = levelColor(level.kind());
            double y = scale.y(level.price());
            boolean offTop = y < pane.y;
            boolean offBottom = y > pane.y + pane.height;
            if (offTop || offBottom) {
                String note = level.label() + "  " + StockChartFormat.price(level.price())
                        + (offTop ? "  – above this range" : "  – below this range");
                int noteY = offTop
                        ? pane.y + LEGEND_BAND + 2 + aboveNotes++ * (tagHeight + 2)
                        : pane.y + pane.height - tagHeight - 2 - belowNotes++ * (tagHeight + 2);
                paintChip(g, metrics, note, pane.x + 6, noteY, tagHeight, color);
                continue;
            }
            g.setStroke(DASHED);
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 210));
            g.draw(new Line2D.Double(pane.x, y, pane.x + pane.width, y));

            // Name at the left end, just above the line (below it when the line hugs the legend).
            int nameBaseline = (int) Math.round(y) - 4;
            if (nameBaseline - metrics.getAscent() < pane.y + LEGEND_BAND) {
                nameBaseline = (int) Math.round(y) + metrics.getAscent() + 3;
            }
            int nameWidth = metrics.stringWidth(level.label());
            g.setColor(new Color(BACKGROUND.getRed(), BACKGROUND.getGreen(), BACKGROUND.getBlue(), 190));
            g.fillRect(pane.x + 4, nameBaseline - metrics.getAscent(), nameWidth + 6, metrics.getHeight());
            g.setColor(color);
            g.drawString(level.label(), pane.x + 7, nameBaseline);

            // Price tag in the axis gutter.
            int tagY = freeRow(gutterRows, (int) Math.round(y - tagHeight / 2.0), tagHeight, pane);
            paintChip(g, metrics, StockChartFormat.price(level.price()), pane.x + pane.width + 2, tagY, tagHeight, color);
        }
    }

    private static void paintChip(Graphics2D g, FontMetrics metrics, String text, int x, int y, int height, Color color) {
        g.setStroke(THIN);
        g.setColor(color);
        g.fillRoundRect(x, y, metrics.stringWidth(text) + 10, height, 5, 5);
        g.setColor(CHIP_TEXT);
        g.drawString(text, x + 5, y + metrics.getAscent() + 1);
    }

    private static int freeRow(List<int[]> used, int preferred, int height, Rectangle pane) {
        int candidate = Math.max(pane.y, preferred);
        boolean moved = true;
        for (int guard = 0; moved && guard < 40; guard++) {
            moved = false;
            for (int[] row : used) {
                if (candidate < row[1] && candidate + height > row[0]) {
                    candidate = row[1] + 1;
                    moved = true;
                }
            }
        }
        candidate = Math.min(candidate, pane.y + pane.height - height);
        used.add(new int[]{candidate, candidate + height});
        return candidate;
    }

    /** Tags the latest close in the axis gutter and returns the gutter row it occupies. */
    private int[] paintLastPriceTag(Graphics2D g, Rectangle pane, Scale scale) {
        int last = data.lastIndex();
        double close = data.closes()[last];
        boolean up = last == 0 || close >= data.closes()[last - 1];
        g.setFont(TITLE_FONT);
        FontMetrics metrics = g.getFontMetrics();
        String text = StockChartFormat.price(close);
        int height = metrics.getHeight() + 2;
        int y = (int) Math.round(scale.y(close) - height / 2.0);
        int x = pane.x + pane.width + 2;
        g.setColor(up ? UP : DOWN);
        g.fillRoundRect(x, y, RIGHT_AXIS - 4, height, 4, 4);
        g.setColor(CHIP_TEXT);
        g.drawString(text, x + 4, y + metrics.getAscent() + 1);
        return new int[]{y, y + height};
    }

    private void paintPriceLegend(Graphics2D g, Rectangle pane) {
        int x = pane.x + 6;
        int baseline = pane.y + 14;
        g.setFont(SYMBOL_FONT);
        g.setColor(TEXT);
        String title = data.symbol() + "  Daily";
        g.drawString(title, x, baseline);
        x += g.getFontMetrics().stringWidth(title) + 14;
        g.setFont(LABEL_FONT);
        FontMetrics metrics = g.getFontMetrics();
        int last = data.lastIndex();
        for (int period : StockChartData.EMA_PERIODS) {
            double value = data.emas().get(period)[last];
            String text = "EMA " + period + " " + (Double.isNaN(value) ? "–" : StockChartFormat.price(value));
            int width = metrics.stringWidth(text) + 18;
            if (x + width > pane.x + pane.width - 8) {
                break;
            }
            g.setColor(emaColor(period));
            g.fillRect(x, baseline - 5, 10, 3);
            g.setColor(TEXT);
            g.drawString(text, x + 14, baseline);
            x += width;
        }
    }

    // ---------------------------------------------------------------- indicator panes

    private void paintRsi(Graphics2D g, Rectangle pane) {
        Scale scale = new Scale(pane, 0, 100);
        paintPaneSeparator(g, pane);
        g.setColor(RSI_BAND);
        g.fill(new Rectangle2D.Double(pane.x, scale.y(70), pane.width, scale.y(30) - scale.y(70)));
        g.setStroke(DASHED);
        g.setColor(GRID);
        g.draw(new Line2D.Double(pane.x, scale.y(70), pane.x + pane.width, scale.y(70)));
        g.draw(new Line2D.Double(pane.x, scale.y(30), pane.x + pane.width, scale.y(30)));
        axisLabel(g, pane, scale.y(70), "70");
        axisLabel(g, pane, scale.y(30), "30");
        paintSeries(g, pane, scale, data.rsi(), RSI_LINE, LINE);
        double value = data.rsi()[data.lastIndex()];
        String state = Double.isNaN(value) ? "" : value >= 70 ? "  Overbought" : value <= 30 ? "  Oversold" : "  Normal range";
        paintTitle(g, pane, "RSI (14)  " + (Double.isNaN(value) ? "–" : String.format(Locale.US, "%.1f", value)) + state);
    }

    private void paintVolume(Graphics2D g, Rectangle pane) {
        int from = firstVisible();
        int to = data.lastIndex();
        double max = 0;
        for (int i = from; i <= to; i++) {
            max = Math.max(max, data.volumes()[i]);
        }
        Scale scale = new Scale(pane, 0, max <= 0 ? 1 : max * 1.08);
        paintPaneSeparator(g, pane);
        double barWidth = Math.max(1, slot(pane) * 0.7);
        for (int i = from; i <= to; i++) {
            Color color = data.closes()[i] >= data.opens()[i] ? UP : DOWN;
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 170));
            double top = scale.y(data.volumes()[i]);
            g.fill(new Rectangle2D.Double(xCenter(pane, i) - barWidth / 2, top, barWidth, pane.y + pane.height - top));
        }
        paintSeries(g, pane, scale, data.volumeAverage(), VOLUME_AVERAGE, LINE);
        axisLabel(g, pane, pane.y + 8, StockChartFormat.compact(max));
        int last = data.lastIndex();
        double average = data.volumeAverage()[last];
        paintTitle(g, pane, "Volume  " + StockChartFormat.compact(data.volumes()[last])
                + (Double.isNaN(average) ? "" : "  ·  50-day avg " + StockChartFormat.compact(average))
                + "  ·  IEX feed");
    }

    private void paintMacd(Graphics2D g, Rectangle pane) {
        int from = firstVisible();
        int to = data.lastIndex();
        double[] line = data.macd().line();
        double[] signal = data.macd().signal();
        double[] histogram = data.macd().histogram();
        double min = 0;
        double max = 0;
        for (int i = from; i <= to; i++) {
            for (double value : new double[]{line[i], signal[i], histogram[i]}) {
                if (!Double.isNaN(value)) {
                    min = Math.min(min, value);
                    max = Math.max(max, value);
                }
            }
        }
        double pad = Math.max((max - min) * 0.08, 1e-6);
        Scale scale = new Scale(pane, min - pad, max + pad);
        paintPaneSeparator(g, pane);
        g.setStroke(THIN);
        g.setColor(GRID);
        g.draw(new Line2D.Double(pane.x, scale.y(0), pane.x + pane.width, scale.y(0)));
        double barWidth = Math.max(1, slot(pane) * 0.6);
        for (int i = from; i <= to; i++) {
            if (Double.isNaN(histogram[i])) {
                continue;
            }
            Color color = histogram[i] >= 0 ? UP : DOWN;
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 120));
            double y0 = scale.y(0);
            double y1 = scale.y(histogram[i]);
            g.fill(new Rectangle2D.Double(xCenter(pane, i) - barWidth / 2, Math.min(y0, y1), barWidth, Math.max(1, Math.abs(y1 - y0))));
        }
        paintSeries(g, pane, scale, line, MACD_LINE, LINE);
        paintSeries(g, pane, scale, signal, SIGNAL_LINE, LINE);
        axisLabel(g, pane, scale.y(0), "0");
        int last = data.lastIndex();
        paintTitle(g, pane, "MACD (12, 26, 9)  " + value(line[last], "%.3f") + "  ·  signal " + value(signal[last], "%.3f"));
    }

    private void paintAccumulation(Graphics2D g, Rectangle pane) {
        int from = firstVisible();
        int to = data.lastIndex();
        double[] line = data.accumulationDistribution();
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (int i = from; i <= to; i++) {
            min = Math.min(min, line[i]);
            max = Math.max(max, line[i]);
        }
        double pad = Math.max((max - min) * 0.08, 1);
        Scale scale = new Scale(pane, min - pad, max + pad);
        paintPaneSeparator(g, pane);
        paintSeries(g, pane, scale, line, ACCUMULATION_LINE, LINE);
        paintTitle(g, pane, "Accumulation / Distribution");
    }

    // ---------------------------------------------------------------- shared painting

    private void paintSeries(Graphics2D g, Rectangle pane, Scale scale, double[] series, Color color, Stroke stroke) {
        Path2D.Double path = new Path2D.Double();
        boolean drawing = false;
        for (int i = firstVisible(); i <= data.lastIndex(); i++) {
            double value = series[i];
            if (Double.isNaN(value)) {
                drawing = false;
                continue;
            }
            double x = xCenter(pane, i);
            double y = scale.y(value);
            if (drawing) {
                path.lineTo(x, y);
            } else {
                path.moveTo(x, y);
                drawing = true;
            }
        }
        g.setStroke(stroke);
        g.setColor(color);
        java.awt.Shape clip = g.getClip();
        g.clipRect(pane.x, pane.y, pane.width, pane.height);
        g.draw(path);
        g.setClip(clip);
    }

    private void paintGrid(Graphics2D g, Rectangle pane, Scale scale, DoubleFunction<String> label) {
        double step = niceStep((scale.max() - scale.min()) / 5);
        if (step <= 0) {
            return;
        }
        g.setStroke(THIN);
        for (double value = Math.ceil(scale.min() / step) * step; value <= scale.max(); value += step) {
            double y = scale.y(value);
            g.setColor(GRID);
            g.draw(new Line2D.Double(pane.x, y, pane.x + pane.width, y));
            axisLabel(g, pane, y, label.apply(value));
        }
    }

    private void paintPaneSeparator(Graphics2D g, Rectangle pane) {
        g.setStroke(THIN);
        g.setColor(GRID);
        g.draw(new Line2D.Double(pane.x, pane.y - PANE_GAP / 2.0, pane.x + pane.width + RIGHT_AXIS, pane.y - PANE_GAP / 2.0));
    }

    private void paintTitle(Graphics2D g, Rectangle pane, String text) {
        g.setFont(TITLE_FONT);
        FontMetrics metrics = g.getFontMetrics();
        g.setColor(new Color(BACKGROUND.getRed(), BACKGROUND.getGreen(), BACKGROUND.getBlue(), 210));
        g.fillRect(pane.x + 2, pane.y + 1, metrics.stringWidth(text) + 8, metrics.getHeight());
        g.setColor(TEXT);
        g.drawString(text, pane.x + 6, pane.y + metrics.getAscent() + 1);
    }

    private void axisLabel(Graphics2D g, Rectangle pane, double y, String text) {
        g.setFont(LABEL_FONT);
        FontMetrics metrics = g.getFontMetrics();
        g.setColor(MUTED);
        g.drawString(text, pane.x + pane.width + 6, (int) Math.round(y + metrics.getAscent() / 2.0 - 1));
    }

    private void paintDateAxis(Graphics2D g, Rectangle lastPane) {
        g.setFont(LABEL_FONT);
        g.setColor(MUTED);
        int baseline = lastPane.y + lastPane.height + 14;
        double lastLabelX = -Double.MAX_VALUE;
        LocalDate previous = null;
        for (int i = firstVisible(); i <= data.lastIndex(); i++) {
            LocalDate date = data.dates().get(i);
            boolean newMonth = previous == null || date.getMonthValue() != previous.getMonthValue();
            previous = date;
            if (!newMonth || i == firstVisible()) {
                continue;
            }
            double x = xCenter(lastPane, i);
            if (x - lastLabelX < 40) {
                continue;
            }
            String text = date.getMonthValue() == 1 ? String.valueOf(date.getYear()) : date.format(MONTH);
            g.drawString(text, (int) Math.round(x - g.getFontMetrics().stringWidth(text) / 2.0), baseline);
            lastLabelX = x;
        }
    }

    private void paintCrosshair(Graphics2D g, Rectangle[] panes) {
        if (hoverIndex < firstVisible() || hoverIndex > data.lastIndex()) {
            return;
        }
        Rectangle price = panes[0];
        Rectangle bottom = panes[panes.length - 1];
        double x = xCenter(price, hoverIndex);
        g.setStroke(DASHED);
        g.setColor(CROSSHAIR);
        g.draw(new Line2D.Double(x, price.y, x, bottom.y + bottom.height));

        int i = hoverIndex;
        double change = i > 0 ? (data.closes()[i] - data.closes()[i - 1]) / data.closes()[i - 1] * 100 : 0;
        List<String> lines = List.of(
                data.dates().get(i).format(DAY),
                "Open " + StockChartFormat.price(data.opens()[i]) + "   High " + StockChartFormat.price(data.highs()[i]),
                "Low " + StockChartFormat.price(data.lows()[i]) + "   Close " + StockChartFormat.price(data.closes()[i]),
                "Change " + StockChartFormat.signedPercent(change) + "   Volume " + StockChartFormat.compact(data.volumes()[i]),
                "RSI " + value(data.rsi()[i], "%.1f") + "   MACD " + value(data.macd().line()[i], "%.3f"));
        g.setFont(LABEL_FONT);
        FontMetrics metrics = g.getFontMetrics();
        int width = lines.stream().mapToInt(metrics::stringWidth).max().orElse(0) + 16;
        int height = lines.size() * metrics.getHeight() + 10;
        int boxX = x > price.x + price.width / 2.0 ? price.x + 8 : price.x + price.width - width - 8;
        int boxY = price.y + 24;
        g.setColor(READOUT_BACKGROUND);
        g.fillRoundRect(boxX, boxY, width, height, 8, 8);
        g.setColor(TEXT);
        int baseline = boxY + 6 + metrics.getAscent();
        for (String line : lines) {
            g.drawString(line, boxX + 8, baseline);
            baseline += metrics.getHeight();
        }
    }

    private static String value(double value, String format) {
        return Double.isNaN(value) ? "–" : String.format(Locale.US, format, value);
    }

    /** A round grid step – 1, 2 or 5 times a power of ten – close to the raw step. */
    static double niceStep(double raw) {
        if (!(raw > 0) || Double.isInfinite(raw)) {
            return 0;
        }
        double exponent = Math.floor(Math.log10(raw));
        double fraction = raw / Math.pow(10, exponent);
        double nice = fraction < 1.5 ? 1 : fraction < 3 ? 2 : fraction < 7 ? 5 : 10;
        return nice * Math.pow(10, exponent);
    }

    /** Moves the crosshair with the mouse and clears it when the mouse leaves the chart. */
    private final class HoverTracker extends MouseAdapter {
        @Override
        public void mouseMoved(MouseEvent event) {
            hoverIndex = indexAt(event.getX());
            repaint();
        }

        @Override
        public void mouseExited(MouseEvent event) {
            hoverIndex = -1;
            repaint();
        }
    }
}
