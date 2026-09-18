package com.neuralarc.ui;

import com.neuralarc.util.FontLoader;
import com.neuralarc.util.ThemeColors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.Scrollable;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The stock chart window's content: a header with the latest price and range buttons, the chart, and
 * a plain-language guide beside it that says what each part of the chart is and what it shows for this
 * stock right now. Starts in a loading state and switches to the chart, or to a message, once the
 * daily prices arrive.
 */
final class StockChartView extends JPanel {
    private static final Color BACKGROUND = ThemeColors.color("NeuralArc.Detail.background", new Color(43, 46, 54));
    private static final Color TEXT = ThemeColors.color("NeuralArc.Detail.foreground", new Color(213, 218, 226));
    private static final Color MUTED = ThemeColors.color("NeuralArc.Detail.titleForeground", new Color(157, 166, 179));
    private static final Color UP = ThemeColors.color("NeuralArc.pnlPositive", new Color(108, 203, 129));
    private static final Color DOWN = ThemeColors.color("NeuralArc.pnlNegative", new Color(240, 113, 120));
    private static final Color CAUTION = new Color(255, 183, 77);
    private static final Color RULE = ThemeColors.color("NeuralArc.Detail.border", new Color(86, 91, 99));
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US);
    private static final int GUIDE_WIDTH = 340;

    private final String symbol;
    private final String context;
    private final List<JToggleButton> rangeButtons = new ArrayList<>();
    private StockChartPanel chart;

    StockChartView(String symbol, String context) {
        super(new BorderLayout());
        this.symbol = symbol == null ? "" : symbol;
        this.context = context == null ? "" : context;
        setBackground(BACKGROUND);
        setBorder(new EmptyBorder(12, 14, 8, 14));
        showLoading();
    }

    void showLoading() {
        showMessage("Loading daily prices for " + symbol + "…",
                "Fetching about three years of history from Alpaca. This usually takes a moment.");
    }

    void showMessage(String title, String detail) {
        chart = null;
        rangeButtons.clear();
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.add(centered(label(title, FontLoader.ui(Font.BOLD, 15f), TEXT)));
        box.add(Box.createVerticalStrut(8));
        box.add(centered(paragraph(detail, 12f, MUTED, 520)));
        JPanel holder = new JPanel(new GridBagLayout());
        holder.setOpaque(false);
        holder.add(box);
        replace(header(null), holder);
    }

    void showData(StockChartData data) {
        chart = new StockChartPanel(data);
        JPanel center = new JPanel(new BorderLayout(12, 0));
        center.setOpaque(false);
        center.add(chart, BorderLayout.CENTER);
        center.add(guide(StockChartReadings.describe(data)), BorderLayout.EAST);
        replace(header(data), center);
    }

    StockChartPanel chart() {
        return chart;
    }

    List<JToggleButton> rangeButtons() {
        return List.copyOf(rangeButtons);
    }

    private void replace(JComponent header, JComponent body) {
        removeAll();
        add(header, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private JComponent header(StockChartData data) {
        JPanel panel = new JPanel(new BorderLayout(24, 0));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(0, 2, 10, 2));
        JPanel identity = new JPanel();
        identity.setOpaque(false);
        identity.setLayout(new BoxLayout(identity, BoxLayout.Y_AXIS));
        identity.add(left(label(symbol, FontLoader.ui(Font.BOLD, 20f), TEXT)));
        identity.add(left(label(context.isBlank() ? "Daily prices" : context + "  ·  Daily prices",
                FontLoader.ui(Font.PLAIN, 11f), MUTED)));
        panel.add(identity, BorderLayout.WEST);
        if (data != null && !data.isEmpty()) {
            panel.add(priceSummary(data), BorderLayout.CENTER);
            panel.add(rangeSelector(data), BorderLayout.EAST);
        }
        return panel;
    }

    private JComponent priceSummary(StockChartData data) {
        int last = data.lastIndex();
        double close = data.closes()[last];
        double change = last > 0 ? close - data.closes()[last - 1] : 0;
        double percent = last > 0 ? change / data.closes()[last - 1] * 100 : 0;
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(left(label(StockChartFormat.money(close), FontLoader.ui(Font.BOLD, 20f), TEXT)));
        String move = StockChartFormat.change(change, close)
                + "  (" + StockChartFormat.signedPercent(percent) + ")  on " + data.dates().get(last).format(DAY);
        panel.add(left(label(move, FontLoader.ui(Font.PLAIN, 11f), change >= 0 ? UP : DOWN)));
        return panel;
    }

    private JComponent rangeSelector(StockChartData data) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 6));
        panel.setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        rangeButtons.clear();
        addRange(panel, group, "6M", StockChartPanel.SIX_MONTHS, data, "Show the last six months of trading.");
        addRange(panel, group, "1Y", StockChartPanel.ONE_YEAR, data, "Show the last year of trading.");
        addRange(panel, group, "3Y", StockChartPanel.THREE_YEARS, data, "Show the last three years of trading.");
        addRange(panel, group, "All", 0, data, "Show every day of history that was loaded.");
        int shown = chart.visibleBars();
        for (JToggleButton button : rangeButtons) {
            int bars = (int) button.getClientProperty("bars");
            if ((bars == 0 && shown == data.size() && data.size() < StockChartPanel.ONE_YEAR) || bars == shown) {
                button.setSelected(true);
                break;
            }
        }
        return panel;
    }

    private void addRange(JPanel panel, ButtonGroup group, String text, int bars, StockChartData data, String tip) {
        JToggleButton button = new JToggleButton(text);
        button.setFont(FontLoader.ui(Font.BOLD, 11f));
        button.setFocusable(false);
        button.setToolTipText(TooltipStyler.text(tip));
        button.setEnabled(bars == 0 || data.size() >= bars);
        button.putClientProperty("bars", bars);
        button.addActionListener(event -> {
            if (chart != null) {
                chart.showLast(bars);
            }
        });
        group.add(button);
        panel.add(button);
        rangeButtons.add(button);
    }

    private JComponent guide(List<StockChartReadings.Reading> readings) {
        GuideColumn column = new GuideColumn();
        column.add(left(label("How to read this chart", FontLoader.ui(Font.BOLD, 14f), TEXT)));
        column.add(Box.createVerticalStrut(4));
        column.add(wrapping("Each section below says what one part of the chart is and what it shows for "
                + symbol + " right now. Hover over the chart to see any day's prices and readings.", 11f, MUTED));
        column.add(Box.createVerticalStrut(12));
        for (StockChartReadings.Reading reading : readings) {
            column.add(left(label(reading.title(), FontLoader.ui(Font.BOLD, 12f), TEXT)));
            column.add(Box.createVerticalStrut(4));
            column.add(wrapping(reading.now(), 12f, toneColor(reading.tone())));
            column.add(Box.createVerticalStrut(5));
            column.add(wrapping(reading.whatItIs(), 11f, MUTED));
            column.add(Box.createVerticalStrut(10));
            JSeparator rule = new JSeparator();
            rule.setForeground(RULE);
            rule.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
            column.add(left(rule));
            column.add(Box.createVerticalStrut(10));
        }
        column.add(wrapping("These notes describe what the chart shows. They are not a recommendation to buy "
                + "or sell.", 10.5f, MUTED));

        JScrollPane scroll = new JScrollPane(column,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, RULE));
        scroll.getViewport().setBackground(BACKGROUND);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setPreferredSize(new Dimension(GUIDE_WIDTH, 0));
        return scroll;
    }

    /**
     * The guide's column always takes the scroll pane's width, so its paragraphs wrap to fit instead of
     * running off the edge. (Swing's HTML width hint scales with the font and overflowed the column.)
     */
    private static final class GuideColumn extends JPanel implements Scrollable {
        private GuideColumn() {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(new EmptyBorder(2, 6, 8, 10));
        }

        /** When the column's width changes, every paragraph's line count can change with it. */
        @Override
        public void setBounds(int x, int y, int width, int height) {
            boolean widthChanged = width != getWidth();
            super.setBounds(x, y, width, height);
            if (widthChanged) {
                for (Component child : getComponents()) {
                    child.invalidate();
                }
                revalidate();
            }
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(16, visible.height - 32);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private static JTextArea wrapping(String text, float size, Color color) {
        return new WrappingText(text, size, color, GUIDE_WIDTH - 40);
    }

    private static Color toneColor(StockChartReadings.Tone tone) {
        return switch (tone) {
            case POSITIVE -> UP;
            case NEGATIVE -> DOWN;
            case CAUTION -> CAUTION;
            case NEUTRAL -> TEXT;
        };
    }

    private static JLabel label(String text, Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    private static JLabel paragraph(String text, float size, Color color, int width) {
        JLabel label = new JLabel("<html><body style='width:" + width + "px'>" + escape(text) + "</body></html>");
        label.setFont(FontLoader.ui(Font.PLAIN, size));
        label.setForeground(color);
        return label;
    }

    private static <T extends JComponent> T left(T component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        return component;
    }

    private static <T extends JComponent> T centered(T component) {
        component.setAlignmentX(Component.CENTER_ALIGNMENT);
        return component;
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
