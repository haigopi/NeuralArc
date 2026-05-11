package com.neuralarc.ui;

import com.neuralarc.api.AlpacaMarketDataApi;
import com.neuralarc.model.AutoAnalyzeBundle;
import com.neuralarc.model.LuckySimulationSelection;
import com.neuralarc.model.LuckyStockAnalysis;
import com.neuralarc.model.RecommendationType;
import com.neuralarc.model.StrategyRecommendation;
import com.neuralarc.model.TrendingStock;
import com.neuralarc.model.TrendingStockGroups;
import com.neuralarc.service.AutoAnalyzeService;
import com.neuralarc.service.TrendingStocksService;
import com.neuralarc.util.FontLoader;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.Duration;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LuckyTrendingStocksDialog extends JDialog {
    private static final Logger LOGGER = Logger.getLogger(LuckyTrendingStocksDialog.class.getName());
    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter STATUS_TIME_FMT = DateTimeFormatter.ofPattern("MMM d, HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final Color DIALOG_BG = new Color(14, 20, 28);
    private static final Color DIALOG_SURFACE = new Color(24, 34, 46);
    private static final Color TEXT_MUTED = new Color(130, 130, 130);
    private static final Color TEXT_PRIMARY = new Color(226, 236, 246);
    private static final Color SECTION_GAINERS_BG = new Color(26, 46, 34);
    private static final Color SECTION_GAINERS_BORDER = new Color(65, 138, 88);
    private static final Color SECTION_LOSERS_BG = new Color(58, 40, 21);
    private static final Color SECTION_LOSERS_BORDER = new Color(196, 135, 46);
    private static final Color INPUT_BORDER = new Color(190, 190, 200);
    private static final Color TAB_BORDER = new Color(64, 81, 101);
    private static final Color TAB_SELECTED_BG = new Color(34, 122, 167);
    private static final Color TAB_UNSELECTED_BG = new Color(38, 52, 69);
    private static final Color TAB_SELECTED_TEXT = new Color(241, 247, 255);
    private static final Color TAB_UNSELECTED_TEXT = new Color(172, 188, 206);
    private static final int SECTION_WIDTH = 860;
    private static final int CARD_WIDTH = 820;
    private static final ZoneId MARKET_TIME_ZONE = ZoneId.of("America/New_York");
    private static final LocalTime MARKET_OPEN_TIME = LocalTime.of(9, 30);
    private static final LocalTime MARKET_CLOSE_TIME = LocalTime.of(16, 0);
    private static final Duration TREND_CACHE_TTL = Duration.ofHours(24);
    private static final Object DAILY_TREND_CACHE_LOCK = new Object();

    private static LoadResult cachedTrendResult;
    private static Instant cachedTrendLoadedAt;

    private final TrendingStocksService trendingStocksService;
    private final AlpacaMarketDataApi marketDataApi;
    private final Consumer<List<LuckySimulationSelection>> placementHandler;
    private final Consumer<String> logSink;
    private final JPanel cardsPanel = new JPanel();
    private final JLabel statusLabel = new JLabel("Loading trending stocks...");
    private final JProgressBar progressBar = new JProgressBar();
    private final JButton refreshButton = new JButton("Refresh");
    private final JButton placeButton = new JButton("Start Monitoring in Paper Mode");
    private final List<StockCard> cards = new ArrayList<>();
    private volatile boolean loadInFlight;

    public LuckyTrendingStocksDialog(
            JFrame owner,
            TrendingStocksService trendingStocksService,
            AlpacaMarketDataApi marketDataApi,
            Consumer<List<LuckySimulationSelection>> placementHandler,
            Consumer<String> logSink
    ) {
        super(owner, "I Am Feeling Lucky - Trending Stocks", true);
        this.trendingStocksService = Objects.requireNonNull(trendingStocksService);
        this.marketDataApi = Objects.requireNonNull(marketDataApi);
        this.placementHandler = Objects.requireNonNull(placementHandler);
        this.logSink = logSink == null ? ignored -> {} : logSink;
        buildUi();
        initializeTrendsOnOpen();
    }

    private void buildUi() {
        setLayout(new BorderLayout(12, 12));
        ((JPanel) getContentPane()).setBorder(new EmptyBorder(14, 14, 14, 14));
        ((JPanel) getContentPane()).setBackground(DIALOG_BG);

        JPanel top = new JPanel(new BorderLayout(10, 6));
        top.setOpaque(false);
        JLabel description = new JLabel("<html>Review today's top gainers and losers, remove unwanted picks, then start the remaining choices as Alpaca Paper strategies.</html>");
        description.setForeground(TEXT_MUTED);
        description.setFont(FontLoader.ui(Font.PLAIN, 11f));
        progressBar.setMinimum(0);
        progressBar.setMaximum(100);
        progressBar.setValue(0);
        progressBar.setStringPainted(true);
        progressBar.setString("Idle");
        progressBar.setVisible(false);
        top.add(description, BorderLayout.NORTH);
        top.add(progressBar, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        cardsPanel.setLayout(new BoxLayout(cardsPanel, BoxLayout.Y_AXIS));
        cardsPanel.setOpaque(true);
        cardsPanel.setBackground(DIALOG_BG);
        cardsPanel.setBorder(new EmptyBorder(6, 4, 6, 4));
        JScrollPane scrollPane = new JScrollPane(cardsPanel);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(true);
        scrollPane.getViewport().setOpaque(true);
        scrollPane.getViewport().setBackground(DIALOG_BG);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setPreferredSize(new Dimension(920, 660));
        add(scrollPane, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(12, 0));
        bottom.setOpaque(false);
        statusLabel.setText("Click Refresh to load today's trending stocks.");
        statusLabel.setForeground(TEXT_PRIMARY);
        DialogButtonStyles.apply(refreshButton, "icons/refresh.svg");
        refreshButton.addActionListener(ignored -> loadAsync(true, false));
        placeButton.setEnabled(false);
        DialogButtonStyles.apply(placeButton, "icons/apply.svg");
        placeButton.addActionListener(ignored -> placeReviewedStocks());
        JButton closeButton = new JButton("Close");
        DialogButtonStyles.apply(closeButton, "icons/close.svg");
        closeButton.addActionListener(ignored -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(refreshButton);
        buttons.add(placeButton);
        buttons.add(closeButton);
        bottom.add(statusLabel, BorderLayout.CENTER);
        bottom.add(buttons, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        pack();
        setMinimumSize(new Dimension(820, 560));
        setLocationRelativeTo(getOwner());
    }

    private void initializeTrendsOnOpen() {
        CachedDailyTrends cached = readCachedTrendsWithinTtl();
        boolean marketOpenAutoRefresh = cached != null && shouldAutoRefreshAfterMarketOpen(cached.loadedAt());
        if (cached != null && cached.result() != null) {
            render(cached.result());
            statusLabel.setText(marketOpenAutoRefresh
                    ? "Showing cached trends from " + STATUS_TIME_FMT.format(cached.loadedAt()) + ". Auto-refreshing for market open..."
                    : "Showing cached trends from " + STATUS_TIME_FMT.format(cached.loadedAt()) + ". Refresh to force reload.");
        }
        if (cached == null || marketOpenAutoRefresh) {
            loadAsync(false, marketOpenAutoRefresh);
        }
    }

    private void loadAsync(boolean forceRefresh, boolean marketOpenAutoRefresh) {
        if (loadInFlight) {
            return;
        }
        loadInFlight = true;
        log((forceRefresh ? "Force refresh" : "Auto load") + " started for trending stocks.");
        SwingWorker<LoadResult, Void> worker = new SwingWorker<>() {
            @Override
            protected LoadResult doInBackground() throws Exception {
                setProgress(5);
                TrendingStockGroups groups = trendingStocksService.topGainersAndLosers(10);
                if (groups.empty()) {
                    setProgress(100);
                    return new LoadResult(List.of(), List.of());
                }
                List<TrendingStock> stocks = new ArrayList<>();
                stocks.addAll(groups.gainers());
                stocks.addAll(groups.losers());
                log("Top movers selected. gainers=" + groups.gainers().stream().map(TrendingStock::symbol).toList()
                        + " losers=" + groups.losers().stream().map(TrendingStock::symbol).toList());
                ExecutorService executor = Executors.newFixedThreadPool(Math.min(4, stocks.size()), runnable -> {
                    Thread thread = new Thread(runnable, "neuralarc-lucky-analysis");
                    thread.setDaemon(true);
                    return thread;
                });
                try {
                    List<Callable<LuckyStockAnalysis>> gainerTasks = groups.gainers().stream()
                            .<Callable<LuckyStockAnalysis>>map(stock -> () -> analyze(stock))
                            .toList();
                    List<Callable<LuckyStockAnalysis>> loserTasks = groups.losers().stream()
                            .<Callable<LuckyStockAnalysis>>map(stock -> () -> analyze(stock))
                            .toList();
                    List<LuckyStockAnalysis> gainers = new ArrayList<>();
                    int total = gainerTasks.size() + loserTasks.size();
                    int completed = 0;
                    for (var future : executor.invokeAll(gainerTasks)) {
                        gainers.add(future.get());
                        completed++;
                        setProgress(percent(completed, total));
                    }
                    List<LuckyStockAnalysis> losers = new ArrayList<>();
                    for (var future : executor.invokeAll(loserTasks)) {
                        losers.add(future.get());
                        completed++;
                        setProgress(percent(completed, total));
                    }
                    setProgress(100);
                    return new LoadResult(gainers, losers);
                } finally {
                    executor.shutdownNow();
                }
            }

            @Override
            protected void done() {
                loadInFlight = false;
                refreshButton.setEnabled(true);
                progressBar.setVisible(false);
                try {
                    LoadResult result = get();
                    cacheResult(result);
                    render(result);
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "I Am Feeling Lucky failed", ex);
                    statusLabel.setText("Failed to load trending stocks: " + message(ex));
                    JOptionPane.showMessageDialog(
                            LuckyTrendingStocksDialog.this,
                            "Failed to load trending stocks: " + message(ex),
                            "I Am Feeling Lucky",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        };
        progressBar.setVisible(true);
        progressBar.setString("Loading trending stocks...");
        progressBar.setValue(0);
        refreshButton.setEnabled(false);
        placeButton.setEnabled(false);
        if (marketOpenAutoRefresh) {
            statusLabel.setText("Auto-refreshing trending stocks for market open...");
        } else {
            statusLabel.setText(forceRefresh ? "Refreshing trending stocks..." : "Loading trending stocks...");
        }
        worker.addPropertyChangeListener(event -> {
            if ("progress".equals(event.getPropertyName())) {
                int progress = (Integer) event.getNewValue();
                progressBar.setValue(Math.max(0, Math.min(100, progress)));
            }
        });
        worker.execute();
    }

    private LuckyStockAnalysis analyze(TrendingStock stock) {
        try {
            log("Analysis started for " + stock.symbol());
            AutoAnalyzeBundle bundle = new AutoAnalyzeService(marketDataApi).analyzeBundle(stock.symbol(), 12, 15);
            log("Analysis completed for " + stock.symbol());
            return new LuckyStockAnalysis(stock, bundle, "");
        } catch (Exception ex) {
            log("Analysis failed for " + stock.symbol() + ": " + ex.getMessage());
            return new LuckyStockAnalysis(stock, null, message(ex));
        }
    }

    private void render(LoadResult result) {
        cardsPanel.removeAll();
        cards.clear();
        if (result == null || (result.gainers().isEmpty() && result.losers().isEmpty())) {
            statusLabel.setText("No trending stocks were returned by Alpaca.");
            placeButton.setEnabled(false);
            revalidate();
            repaint();
            return;
        }
        addGroup("Top 10 Gainers", result.gainers(), SECTION_GAINERS_BG, SECTION_GAINERS_BORDER);
        cardsPanel.add(Box.createVerticalStrut(12));
        addGroup("Top 10 Losers", result.losers(), SECTION_LOSERS_BG, SECTION_LOSERS_BORDER);
        statusLabel.setText("Review " + cards.size() + " stock(s), choose quantity/term for each, then start paper monitoring.");
        placeButton.setEnabled(cards.stream().anyMatch(StockCard::placeable));
        revalidate();
        repaint();
    }

    private void addToCurrentStrategy(StockCard card) {
        if (card == null || !card.placeable()) {
            JOptionPane.showMessageDialog(this,
                    "This stock does not currently have a valid base limit buy price.",
                    "Cannot Add",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        LuckySimulationSelection selection = card.selection();
        placementHandler.accept(List.of(selection));
        log("Added " + selection.stock().symbol() + " to paper strategy placement with "
                + selection.selectedRecommendationType().name() + " qty=" + selection.buyQuantity() + ".");
    }

    private void addGroup(String title, List<LuckyStockAnalysis> analyses, Color sectionBackground, Color sectionBorder) {
        JPanel group = new JPanel(new BorderLayout(0, 10));
        group.setOpaque(true);
        group.setBackground(sectionBackground);
        group.setAlignmentX(Component.LEFT_ALIGNMENT);
        group.setMaximumSize(new Dimension(SECTION_WIDTH, Short.MAX_VALUE));
        group.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(sectionBorder, 1, true),
                new EmptyBorder(12, 12, 12, 12)
        ));

        JLabel heading = new JLabel(title);
        heading.setFont(FontLoader.ui(Font.BOLD, 14f));
        heading.setForeground(TEXT_PRIMARY);
        heading.setBorder(new EmptyBorder(0, 2, 4, 2));
        group.add(heading, BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        if (analyses == null || analyses.isEmpty()) {
            JLabel empty = new JLabel("No stocks returned.");
            empty.setForeground(TEXT_PRIMARY);
            body.add(empty);
        }
        for (LuckyStockAnalysis analysis : analyses == null ? List.<LuckyStockAnalysis>of() : analyses) {
            StockCard card = new StockCard(analysis);
            cards.add(card);
            body.add(card);
            body.add(Box.createVerticalStrut(10));
        }
        group.add(body, BorderLayout.CENTER);
        cardsPanel.add(group);
    }

    private void placeReviewedStocks() {
        List<LuckySimulationSelection> selections = cards.stream()
                .filter(StockCard::isVisible)
                .filter(StockCard::placeable)
                .map(StockCard::selection)
                .toList();
        if (selections.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No reviewed stocks have a valid base limit buy price.",
                    "Nothing to Place",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        log("Paper monitoring placement started for " + selections.size() + " reviewed stock(s).");
        placementHandler.accept(selections);
        dispose();
    }

    private void removeCard(StockCard card) {
        card.setVisible(false);
        cardsPanel.remove(card);
        cardsPanel.revalidate();
        cardsPanel.repaint();
        log("User removed trending stock " + card.analysis.stock().symbol());
        int visibleCount = (int) cards.stream().filter(Component::isVisible).count();
        statusLabel.setText(visibleCount + " reviewed stock(s) remaining.");
        placeButton.setEnabled(cards.stream().anyMatch(cardItem -> cardItem.isVisible() && cardItem.placeable()));
    }

    private String message(Exception ex) {
        Throwable cause = ex.getCause() == null ? ex : ex.getCause();
        return cause.getMessage() == null ? "Unexpected error" : cause.getMessage();
    }

    private CachedDailyTrends readCachedTrendsWithinTtl() {
        synchronized (DAILY_TREND_CACHE_LOCK) {
            if (cachedTrendResult == null || cachedTrendLoadedAt == null) {
                return null;
            }
            if (Duration.between(cachedTrendLoadedAt, Instant.now()).compareTo(TREND_CACHE_TTL) > 0) {
                cachedTrendResult = null;
                cachedTrendLoadedAt = null;
                return null;
            }
            return new CachedDailyTrends(cachedTrendResult, cachedTrendLoadedAt);
        }
    }

    private void cacheResult(LoadResult result) {
        if (result == null) {
            return;
        }
        synchronized (DAILY_TREND_CACHE_LOCK) {
            cachedTrendResult = result;
            cachedTrendLoadedAt = Instant.now();
        }
    }

    private static boolean isBeforeMarketClose() {
        return ZonedDateTime.now(MARKET_TIME_ZONE).toLocalTime().isBefore(MARKET_CLOSE_TIME);
    }

    private boolean shouldAutoRefreshAfterMarketOpen(Instant loadedAt) {
        if (loadedAt == null || !isWeekdayMarketDay()) {
            return false;
        }
        ZonedDateTime nowEt = ZonedDateTime.now(MARKET_TIME_ZONE);
        if (nowEt.toLocalTime().isBefore(MARKET_OPEN_TIME)) {
            return false;
        }
        ZonedDateTime todayOpenEt = nowEt.toLocalDate().atTime(MARKET_OPEN_TIME).atZone(MARKET_TIME_ZONE);
        return loadedAt.isBefore(todayOpenEt.toInstant()) && isBeforeMarketClose();
    }

    private boolean isWeekdayMarketDay() {
        DayOfWeek day = ZonedDateTime.now(MARKET_TIME_ZONE).getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }

    private static int percent(int completed, int total) {
        if (total <= 0) {
            return 100;
        }
        return (int) Math.round((completed * 100.0d) / total);
    }

    private void log(String message) {
        LOGGER.info(message);
        SwingUtilities.invokeLater(() -> logSink.accept("[I Am Feeling Lucky] " + message));
    }

    private final class StockCard extends JPanel {
        private final LuckyStockAnalysis analysis;
        private final JTabbedPane tabs = new JTabbedPane();
        private final JSpinner quantitySpinner = new JSpinner(new SpinnerNumberModel(10, 1, 100000, 1));

        private StockCard(LuckyStockAnalysis analysis) {
            super(new BorderLayout(0, 10));
            this.analysis = analysis;
            setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(CARD_WIDTH, Short.MAX_VALUE));
            setBorder(createBorder(analysis.stock().symbol()));
            build();
        }

        private void build() {
            add(header(), BorderLayout.NORTH);
            if (!analysis.successful()) {
                JLabel error = new JLabel("Analysis failed: " + analysis.errorMessage());
                error.setForeground(new Color(180, 30, 30));
                add(error, BorderLayout.CENTER);
                return;
            }
            tabs.addTab("High-Risk Short-Term", recommendationPanel(analysis.analysis().highRiskShortTermRecommendation()));
            tabs.addTab("Short-Term", recommendationPanel(analysis.analysis().shortTermRecommendation()));
            tabs.addTab("Long-Term", recommendationPanel(analysis.analysis().longTermRecommendation()));
            styleTabs(tabs);
            tabs.setSelectedIndex(1);
            add(tabs, BorderLayout.CENTER);
        }

        private void styleTabs(JTabbedPane tabPane) {
            tabPane.setOpaque(false);
            tabPane.setBackground(DIALOG_SURFACE);
            tabPane.setForeground(TAB_UNSELECTED_TEXT);
            tabPane.setFont(FontLoader.ui(Font.BOLD, 11f));
            tabPane.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(TAB_BORDER, 1, true),
                    new EmptyBorder(2, 2, 2, 2)
            ));
            tabPane.setUI(new BasicTabbedPaneUI() {
                @Override
                protected void installDefaults() {
                    super.installDefaults();
                    selectedTabPadInsets = new Insets(0, 0, 0, 0);
                    tabInsets = new Insets(6, 12, 6, 12);
                    contentBorderInsets = new Insets(1, 0, 0, 0);
                }

                @Override
                protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex,
                                                  int x, int y, int w, int h, boolean isSelected) {
                    g.setColor(isSelected ? TAB_SELECTED_BG : TAB_UNSELECTED_BG);
                    g.fillRoundRect(x, y, w, h, 10, 10);
                }

                @Override
                protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex,
                                              int x, int y, int w, int h, boolean isSelected) {
                    g.setColor(TAB_BORDER);
                    g.drawRoundRect(x, y, w - 1, h - 1, 10, 10);
                }

                @Override
                protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {
                    g.setColor(TAB_BORDER);
                    g.drawLine(0, calculateTabAreaHeight(tabPlacement, runCount, maxTabHeight),
                            tabPane.getWidth(), calculateTabAreaHeight(tabPlacement, runCount, maxTabHeight));
                }

                @Override
                protected void paintText(Graphics g, int tabPlacement, Font font, FontMetrics metrics,
                                         int tabIndex, String title, Rectangle textRect, boolean isSelected) {
                    g.setFont(font);
                    g.setColor(isSelected ? TAB_SELECTED_TEXT : TAB_UNSELECTED_TEXT);
                    g.drawString(title, textRect.x, textRect.y + metrics.getAscent());
                }

                @Override
                protected void paintFocusIndicator(Graphics g, int tabPlacement, Rectangle[] rects, int tabIndex,
                                                   Rectangle iconRect, Rectangle textRect, boolean isSelected) {
                    // Keep tab visuals clean.
                }
            });
        }

        private JPanel header() {
            TrendingStock stock = analysis.stock();
            JPanel panel = new JPanel(new BorderLayout(12, 6));
            panel.setOpaque(false);
            JTextArea details = textArea((stock.companyName().isBlank() ? "" : stock.companyName() + "\n")
                    + "Price: $" + stock.latestPrice().toPlainString()
                    + "    Change: " + stock.dailyChangePercent().toPlainString() + "%"
                    + "    Volume: " + stock.volume().toPlainString()
                    + "    Trades: " + stock.tradeCount().toPlainString()
                    + "\nReason: " + stock.reason());
            JButton removeButton = new JButton("Remove");
            DialogButtonStyles.apply(removeButton, "icons/delete.svg");
            removeButton.addActionListener(ignored -> removeCard(this));
            JButton addToCurrentButton = new JButton("Add to Current Strategy");
            DialogButtonStyles.apply(addToCurrentButton, "icons/add-stock-strategy.svg");
            addToCurrentButton.addActionListener(ignored -> addToCurrentStrategy(this));

            JPanel qtyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            qtyPanel.setOpaque(false);
            JLabel qtyLabel = new JLabel("Qty:");
            qtyLabel.setFont(FontLoader.ui(Font.PLAIN, 11f));
            quantitySpinner.setFont(FontLoader.ui(Font.PLAIN, 11f));
            quantitySpinner.setPreferredSize(new Dimension(84, 26));
            qtyPanel.add(qtyLabel);
            qtyPanel.add(quantitySpinner);

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            actions.setOpaque(false);
            actions.add(qtyPanel);
            actions.add(addToCurrentButton);
            actions.add(removeButton);

            JPanel left = new JPanel(new BorderLayout(0, 4));
            left.setOpaque(false);
            left.add(details, BorderLayout.CENTER);
            panel.add(left, BorderLayout.CENTER);
            panel.add(actions, BorderLayout.EAST);
            return panel;
        }

        private JPanel recommendationPanel(StrategyRecommendation recommendation) {
            JPanel panel = new JPanel(new GridLayout(0, 2, 10, 8));
            panel.setOpaque(false);
            addPair(panel, "Recommendation:", recommendation.recommendationAction().name());
            addPair(panel, "Confidence:", recommendation.confidenceScore() + "%");
            addPair(panel, "Base limit buy:", money(recommendation.baseBuyPrice()));
            addPair(panel, "Target sell:", money(recommendation.sellPrice()));
            addPair(panel, "Stop loss:", money(recommendation.stopLossPrice()));
            addPair(panel, "Generated:", DISPLAY_FMT.format(analysis.analysis().result().analyzedAt()));
            addPair(panel, "Reasoning:", recommendation.baseAdjustmentReason());
            addPair(panel, "Risks:", recommendation.warningMessage().isBlank() ? "Review market volatility and liquidity." : recommendation.warningMessage());
            return panel;
        }

        private void addPair(JPanel panel, String label, String value) {
            JLabel labelComponent = new JLabel(label);
            labelComponent.setFont(FontLoader.ui(Font.PLAIN, 11f));
            labelComponent.setForeground(TEXT_PRIMARY);
            JLabel valueComponent = new JLabel("<html>" + escape(value) + "</html>");
            valueComponent.setFont(FontLoader.ui(Font.BOLD, 11f));
            valueComponent.setForeground(TEXT_PRIMARY);
            panel.add(labelComponent);
            panel.add(valueComponent);
        }

        private boolean placeable() {
            if (!analysis.successful()) {
                return false;
            }
            return selectedRecommendation().isApplicable()
                    && selectedRecommendation().baseBuyPrice().compareTo(BigDecimal.ZERO) > 0;
        }

        private LuckySimulationSelection selection() {
            return new LuckySimulationSelection(analysis.stock(), analysis.analysis(), selectedType(), selectedQuantity());
        }

        private int selectedQuantity() {
            Object value = quantitySpinner.getValue();
            if (value instanceof Number number) {
                return Math.max(1, number.intValue());
            }
            return 10;
        }

        private StrategyRecommendation selectedRecommendation() {
            return switch (selectedType()) {
                case HIGH_RISK_SHORT_TERM -> analysis.analysis().highRiskShortTermRecommendation();
                case LONG_TERM -> analysis.analysis().longTermRecommendation();
                default -> analysis.analysis().shortTermRecommendation();
            };
        }

        private RecommendationType selectedType() {
            return switch (tabs.getSelectedIndex()) {
                case 0 -> RecommendationType.HIGH_RISK_SHORT_TERM;
                case 2 -> RecommendationType.LONG_TERM;
                default -> RecommendationType.SHORT_TERM;
            };
        }
    }

    private TitledBorder createBorder(String title) {
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(INPUT_BORDER, 1, true),
                        new EmptyBorder(10, 10, 10, 10)
                ),
                title
        );
        border.setTitleFont(FontLoader.ui(Font.BOLD, 12f));
        border.setTitleColor(TEXT_PRIMARY);
        return border;
    }

    private static JTextArea textArea(String text) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(FontLoader.ui(Font.PLAIN, 11f));
        area.setForeground(TEXT_PRIMARY);
        return area;
    }

    private static String money(BigDecimal value) {
        return value == null ? "-" : "$" + value.toPlainString();
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private record LoadResult(List<LuckyStockAnalysis> gainers, List<LuckyStockAnalysis> losers) {}

    private record CachedDailyTrends(LoadResult result, Instant loadedAt) {}
}
