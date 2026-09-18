package com.neuralarc.ui;

import com.neuralarc.model.MarketBar;
import com.neuralarc.util.FontLoader;
import com.neuralarc.util.ThemeColors;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * The column beside Position and Rules Triggered: daily bars for one position, with a plain read on how
 * the market is treating the stock. It follows the selected grid row, or the first losing position when
 * no row is selected. A click anywhere in the column opens the full stock chart.
 *
 * <p>Bars are fetched off the EDT with the same history the full chart uses, so the indicators behind
 * the summary match that chart exactly, and are cached per symbol for {@link #CACHE_TTL}: the panels
 * refresh many times a minute and must not refetch each time.
 */
final class PositionBarsColumn extends JPanel {
    static final Duration CACHE_TTL = Duration.ofMinutes(10);

    /** Reads daily bars; returns null from {@link Host#barsSource()} while not connected. */
    interface BarsSource {
        List<MarketBar> dailyBars(String symbol, LocalDate start, LocalDate end) throws Exception;
    }

    interface Host {
        BarsSource barsSource();
        void openFullChart(ManagedStrategy entry);
    }

    private static final Color TEXT = ThemeColors.color("NeuralArc.Detail.foreground", new Color(213, 218, 226));
    private static final Color MUTED = ThemeColors.color("NeuralArc.Detail.titleForeground", new Color(157, 166, 179));
    private static final Color UP = ThemeColors.color("NeuralArc.pnlPositive", new Color(108, 203, 129));
    private static final Color DOWN = ThemeColors.color("NeuralArc.pnlNegative", new Color(240, 113, 120));

    private final Host host;
    private final Executor loader;
    private final Clock clock;
    private final Map<String, CachedBars> cache = new HashMap<>();
    private final JLabel symbolLabel = new JLabel(" ");
    private final JLabel sourceLabel = new JLabel(" ");
    private final JButton openChart = new JButton("Open Chart");
    private final PositionBarsChart chart = new PositionBarsChart();
    private final JLabel verdictLabel = new JLabel(" ");
    private final JTextArea summary = new WrappingText("", 10.5f, TEXT, 320);
    private ManagedStrategy entry;
    private String loadingSymbol;
    private List<StockChartLevels.Level> shownLevels = List.of();

    PositionBarsColumn(Host host, Executor loader, Clock clock) {
        super(new BorderLayout(0, 6));
        this.host = Objects.requireNonNull(host, "host");
        this.loader = Objects.requireNonNull(loader, "loader");
        this.clock = clock == null ? Clock.systemUTC() : clock;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        symbolLabel.setFont(FontLoader.ui(Font.BOLD, 12f));
        symbolLabel.setForeground(TEXT);
        sourceLabel.setFont(FontLoader.ui(Font.PLAIN, 10f));
        sourceLabel.setForeground(MUTED);
        JPanel titles = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titles.setOpaque(false);
        titles.add(symbolLabel);
        titles.add(sourceLabel);
        openChart.setFont(FontLoader.ui(Font.PLAIN, 10f));
        openChart.setFocusable(false);
        openChart.setToolTipText("Open the full chart with RSI, volume, MACD and a guide to each panel");
        openChart.addActionListener(event -> openFullChart());
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(titles, BorderLayout.CENTER);
        header.add(openChart, BorderLayout.EAST);

        verdictLabel.setFont(FontLoader.ui(Font.BOLD, 11f));
        summary.setCursor(getCursor());
        JPanel reading = new JPanel(new BorderLayout(0, 2));
        reading.setOpaque(false);
        reading.add(verdictLabel, BorderLayout.NORTH);
        reading.add(summary, BorderLayout.CENTER);

        add(header, BorderLayout.NORTH);
        add(chart, BorderLayout.CENTER);
        add(reading, BorderLayout.SOUTH);

        MouseAdapter expand = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event)) {
                    openFullChart();
                }
            }
        };
        for (Component component : List.of(this, chart, summary, verdictLabel, symbolLabel, sourceLabel)) {
            component.addMouseListener(expand);
        }
        getAccessibleContext().setAccessibleName("Position bars");
        showMessage("No position", "Select a row in the grid to see its daily bars.");
    }

    /**
     * Shows {@code next}'s bars. {@code defaulted} marks the first-losing fallback, so the column says
     * why it shows that stock. Cheap to call on every panel refresh: it only fetches when the symbol
     * changes or its cached bars have expired.
     */
    void show(ManagedStrategy next, boolean defaulted) {
        entry = next;
        if (next == null || next.strategy == null) {
            showMessage("No position", "Select a row in the grid to see its daily bars. With no row selected,"
                    + " the first losing position is shown here.");
            return;
        }
        String symbol = next.strategy.symbol();
        symbolLabel.setText(symbol);
        sourceLabel.setText(defaulted ? "First losing position · select a row to change" : "Selected row");
        openChart.setEnabled(true);
        List<StockChartLevels.Level> levels = StockChartLevels.from(next);
        CachedBars cached = cache.get(symbol);
        if (cached != null && !cached.expired(clock.instant())) {
            if (chart.data() == null || !symbol.equals(chart.data().symbol()) || !levels.equals(shownLevels)) {
                display(StockChartData.from(symbol, cached.bars(), levels), levels);
            }
            return;
        }
        if (symbol.equals(loadingSymbol)) {
            return;
        }
        BarsSource source = host.barsSource();
        if (source == null) {
            showMessage(symbol, "Connect your Alpaca account in Settings to see daily bars.");
            return;
        }
        if (chart.data() == null || !symbol.equals(chart.data().symbol())) {
            chart.setData(null);
            setReading(null, "Loading daily bars…");
        }
        loadingSymbol = symbol;
        loader.execute(() -> {
            List<MarketBar> bars;
            String failure = null;
            try {
                LocalDate end = LocalDate.now(clock);
                bars = source.dailyBars(symbol, end.minusDays(StockChartOpener.HISTORY_CALENDAR_DAYS), end);
            } catch (Exception ex) {
                bars = null;
                failure = ex.getMessage();
            }
            List<MarketBar> loaded = bars;
            String error = failure;
            SwingUtilities.invokeLater(() -> onLoaded(symbol, loaded, error));
        });
    }

    /** A new width can change how many lines the summary wraps to, and so the room the chart keeps. */
    @Override
    public void setBounds(int x, int y, int width, int height) {
        boolean widthChanged = width != getWidth();
        super.setBounds(x, y, width, height);
        if (widthChanged) {
            summary.invalidate();
            revalidate();
        }
    }

    ManagedStrategy entry() {
        return entry;
    }

    String verdictText() {
        return verdictLabel.getText();
    }

    String summaryText() {
        return summary.getText();
    }

    PositionBarsChart chart() {
        return chart;
    }

    private void onLoaded(String symbol, List<MarketBar> bars, String error) {
        if (symbol.equals(loadingSymbol)) {
            loadingSymbol = null;
        }
        if (bars != null) {
            cache.put(symbol, new CachedBars(List.copyOf(bars), clock.instant()));
        }
        if (entry == null || entry.strategy == null || !symbol.equals(entry.strategy.symbol())) {
            return; // The selection moved on while this loaded; its own load will paint.
        }
        if (bars == null) {
            chart.setData(null);
            setReading(null, "Couldn't load daily bars for " + symbol + (error == null ? "." : ": " + error));
            return;
        }
        List<StockChartLevels.Level> levels = StockChartLevels.from(entry);
        StockChartData data = StockChartData.from(symbol, bars, levels);
        if (data.isEmpty()) {
            chart.setData(null);
            setReading(null, "Alpaca returned no daily prices for " + symbol + ".");
            return;
        }
        display(data, levels);
    }

    private void display(StockChartData data, List<StockChartLevels.Level> levels) {
        shownLevels = levels;
        chart.setData(data);
        MarketReactionSummary.Summary reaction = MarketReactionSummary.summarize(data);
        setReading(reaction.verdict(), reaction.text());
    }

    private void setReading(MarketReactionSummary.Verdict verdict, String text) {
        if (verdict == null) {
            verdictLabel.setText(" ");
        } else {
            verdictLabel.setText("How the market is treating it: " + verdict.label());
            verdictLabel.setForeground(switch (verdict) {
                case BUYERS -> UP;
                case SELLERS -> DOWN;
                case UNDECIDED -> MUTED;
            });
        }
        summary.setForeground(verdict == null ? MUTED : TEXT);
        summary.setText(text == null ? "" : text);
        summary.invalidate();
        revalidate();
        chart.setToolTipText(verdict == null ? null : "Click to open the full chart");
    }

    private void showMessage(String title, String detail) {
        symbolLabel.setText(title);
        sourceLabel.setText(" ");
        openChart.setEnabled(false);
        chart.setData(null);
        shownLevels = List.of();
        setReading(null, detail);
    }

    private void openFullChart() {
        if (entry != null && entry.strategy != null) {
            host.openFullChart(entry);
        }
    }

    private record CachedBars(List<MarketBar> bars, Instant fetchedAt) {
        boolean expired(Instant now) {
            return Duration.between(fetchedAt, now).compareTo(CACHE_TTL) >= 0;
        }
    }
}
