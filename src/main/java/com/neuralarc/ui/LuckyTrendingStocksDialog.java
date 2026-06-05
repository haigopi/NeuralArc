package com.neuralarc.ui;

import com.neuralarc.api.AlpacaMarketDataApi;
import com.neuralarc.model.AutoAnalyzeBundle;
import com.neuralarc.model.LuckySimulationSelection;
import com.neuralarc.model.LuckyStockAnalysis;
import com.neuralarc.model.RecommendationType;
import com.neuralarc.model.StrategyMode;
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
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LuckyTrendingStocksDialog extends JDialog {
    private static final Logger LOGGER = Logger.getLogger(LuckyTrendingStocksDialog.class.getName());
    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter STATUS_TIME_FMT = DateTimeFormatter.ofPattern("MMM d, HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final Color DIALOG_BG = UIManager.getColor("Panel.background") != null
            ? UIManager.getColor("Panel.background")
            : Color.WHITE;
    private static final Color INPUT_BG = UIManager.getColor("TextField.background") != null
            ? UIManager.getColor("TextField.background")
            : Color.WHITE;
    private static final Color TEXT_MUTED = UIManager.getColor("Label.disabledForeground") != null
            ? UIManager.getColor("Label.disabledForeground")
            : new Color(130, 130, 130);
    private static final Color TEXT_PRIMARY = UIManager.getColor("Label.foreground") != null
            ? UIManager.getColor("Label.foreground")
            : new Color(45, 45, 50);
    private static final Color SECTION_HEADER_TEXT = Color.WHITE;
    private static final Color SECTION_GAINERS_BG = new Color(232, 247, 236);
    private static final Color SECTION_GAINERS_BORDER = new Color(195, 220, 200);
    private static final Color SECTION_LOSERS_BG = new Color(255, 242, 228);
    private static final Color SECTION_LOSERS_BORDER = new Color(235, 210, 170);
    private static final Color INPUT_BORDER = new Color(210, 210, 220);
    private static final Color TAB_BORDER = new Color(204, 214, 225);
    private static final Color TAB_SELECTED_BG = new Color(235, 244, 252);
    private static final Color TAB_UNSELECTED_BG = new Color(246, 248, 250);
    private static final Color TAB_SELECTED_TEXT = new Color(11, 84, 132);
    private static final Color TAB_UNSELECTED_TEXT = new Color(75, 85, 99);
    private static final int SECTION_MAX_WIDTH = 660;
    private static final ZoneId MARKET_TIME_ZONE = ZoneId.of("America/New_York");
    private static final LocalTime MARKET_OPEN_TIME = LocalTime.of(9, 30);
    private static final LocalTime MARKET_CLOSE_TIME = LocalTime.of(16, 0);
    private static final Duration TREND_CACHE_TTL = Duration.ofHours(24);
    private static final Object DAILY_TREND_CACHE_LOCK = new Object();

    private static LoadResult cachedTrendResult;
    private static Instant cachedTrendLoadedAt;
    private static final List<DiversifiedStock> TOP_20_DIVERSIFIED_STOCKS = List.of(
            new DiversifiedStock("MSFT", "Microsoft Corporation", "Diversified top 20 - technology"),
            new DiversifiedStock("AAPL", "Apple Inc.", "Diversified top 20 - technology"),
            new DiversifiedStock("NVDA", "NVIDIA Corporation", "Diversified top 20 - technology"),
            new DiversifiedStock("AMZN", "Amazon.com, Inc.", "Diversified top 20 - technology"),
            new DiversifiedStock("GOOGL", "Alphabet Inc.", "Diversified top 20 - communication services"),
            new DiversifiedStock("META", "Meta Platforms, Inc.", "Diversified top 20 - communication services"),
            new DiversifiedStock("AVGO", "Broadcom Inc.", "Diversified top 20 - technology"),
            new DiversifiedStock("ORCL", "Oracle Corporation", "Diversified top 20 - technology"),
            new DiversifiedStock("BRK.B", "Berkshire Hathaway Inc.", "Diversified top 20 - financials"),
            new DiversifiedStock("JPM", "JPMorgan Chase & Co.", "Diversified top 20 - financials"),
            new DiversifiedStock("V", "Visa Inc.", "Diversified top 20 - financials"),
            new DiversifiedStock("MA", "Mastercard Incorporated", "Diversified top 20 - financials"),
            new DiversifiedStock("JNJ", "Johnson & Johnson", "Diversified top 20 - healthcare"),
            new DiversifiedStock("UNH", "UnitedHealth Group Incorporated", "Diversified top 20 - healthcare"),
            new DiversifiedStock("LLY", "Eli Lilly and Company", "Diversified top 20 - healthcare"),
            new DiversifiedStock("TSLA", "Tesla, Inc.", "Diversified top 20 - consumer discretionary"),
            new DiversifiedStock("WMT", "Walmart Inc.", "Diversified top 20 - consumer staples"),
            new DiversifiedStock("PG", "The Procter & Gamble Company", "Diversified top 20 - consumer staples"),
            new DiversifiedStock("XOM", "Exxon Mobil Corporation", "Diversified top 20 - energy"),
            new DiversifiedStock("CAT", "Caterpillar Inc.", "Diversified top 20 - industrials")
    );

    private final TrendingStocksService trendingStocksService;
    private final AlpacaMarketDataApi marketDataApi;
    private final Consumer<List<LuckySimulationSelection>> placementHandler;
    private final Consumer<String> logSink;
    private final StrategyMode targetMode;
    private final StrategyUniverse universe;
    private transient Consumer<LuckySimulationSelection> reviewHandler;
    private final JPanel cardsPanel = new JPanel();
    private final JLabel statusLabel = new JLabel("Loading trending stocks...");
    private final JProgressBar progressBar = new JProgressBar();
    private final JButton refreshButton = new JButton("Refresh");
    private final JButton placeButton = new JButton();
    private final List<StockCard> cards = new ArrayList<>();
    private volatile boolean loadInFlight;

    public LuckyTrendingStocksDialog(
            JFrame owner,
            TrendingStocksService trendingStocksService,
            AlpacaMarketDataApi marketDataApi,
            Consumer<List<LuckySimulationSelection>> placementHandler,
            Consumer<String> logSink
    ) {
        this(owner, trendingStocksService, marketDataApi, placementHandler, logSink, StrategyMode.PAPER);
    }

    public LuckyTrendingStocksDialog(
            JFrame owner,
            TrendingStocksService trendingStocksService,
            AlpacaMarketDataApi marketDataApi,
            Consumer<List<LuckySimulationSelection>> placementHandler,
            Consumer<String> logSink,
            StrategyMode targetMode
    ) {
        this(owner, trendingStocksService, marketDataApi, placementHandler, logSink, targetMode, StrategyUniverse.VOLATILE);
    }

    public LuckyTrendingStocksDialog(
            JFrame owner,
            TrendingStocksService trendingStocksService,
            AlpacaMarketDataApi marketDataApi,
            Consumer<List<LuckySimulationSelection>> placementHandler,
            Consumer<String> logSink,
            StrategyMode targetMode,
            StrategyUniverse universe
    ) {
        super(owner, "I Am Feeling Lucky - " + (universe == StrategyUniverse.DIVERSIFIED_TOP_20
                ? "Top 20 Diversified Stocks"
                : "Volatile Strategy"), true);
        this.trendingStocksService = Objects.requireNonNull(trendingStocksService);
        this.marketDataApi = Objects.requireNonNull(marketDataApi);
        this.placementHandler = Objects.requireNonNull(placementHandler);
        this.logSink = logSink == null ? ignored -> {} : logSink;
        this.targetMode = targetMode == null ? StrategyMode.PAPER : targetMode;
        this.universe = universe == null ? StrategyUniverse.VOLATILE : universe;
        buildUi();
        initializeTrendsOnOpen();
    }

    public void setReviewHandler(Consumer<LuckySimulationSelection> reviewHandler) {
        this.reviewHandler = reviewHandler;
    }

    private void buildUi() {
        setLayout(new BorderLayout(8, 8));
        ((JPanel) getContentPane()).setBorder(new EmptyBorder(8, 6, 8, 6));
        ((JPanel) getContentPane()).setBackground(DIALOG_BG);

        JPanel top = new JPanel(new BorderLayout(8, 6));
        top.setOpaque(false);
        JLabel description = new JLabel(dialogDescriptionHtml());
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
        cardsPanel.setOpaque(false);
        cardsPanel.setBorder(new EmptyBorder(0, 0, 0, 0));
        JScrollPane scrollPane = new JScrollPane(cardsPanel);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getViewport().setBackground(DIALOG_BG);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setPreferredSize(new Dimension(700, 660));
        add(scrollPane, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(8, 0));
        bottom.setOpaque(false);
        statusLabel.setText(initialStatusText());
        statusLabel.setForeground(TEXT_MUTED);
        statusLabel.setFont(FontLoader.ui(Font.PLAIN, 10f));
        DialogButtonStyles.apply(refreshButton, "icons/refresh.svg");
        refreshButton.addActionListener(ignored -> loadAsync(true, false));
        placeButton.setText("Start Monitoring in " + modeLabel() + " Mode");
        placeButton.setEnabled(false);
        DialogButtonStyles.apply(placeButton, "icons/apply.svg");
        placeButton.addActionListener(ignored -> placeReviewedStocks());
        JButton closeButton = new JButton("Close");
        DialogButtonStyles.apply(closeButton, "icons/close.svg");
        closeButton.addActionListener(ignored -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        buttons.setOpaque(false);
        buttons.add(refreshButton);
        buttons.add(placeButton);
        buttons.add(closeButton);
        bottom.add(statusLabel, BorderLayout.CENTER);
        bottom.add(buttons, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        pack();
        setMinimumSize(new Dimension(700, 560));
        setSize(700, 720);
        setLocationRelativeTo(getOwner());
    }

    private String modeLabel() {
        return targetMode == StrategyMode.LIVE ? "Live" : "Paper";
    }

    private void initializeTrendsOnOpen() {
        if (universe == StrategyUniverse.DIVERSIFIED_TOP_20) {
            loadAsync(false, false);
            return;
        }
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
        log((forceRefresh ? "Force refresh" : "Auto load") + " started for " + sourceLogLabel() + ".");
        SwingWorker<LoadResult, Void> worker = new SwingWorker<>() {
            @Override
            protected LoadResult doInBackground() throws Exception {
                setProgress(5);
                return universe == StrategyUniverse.DIVERSIFIED_TOP_20
                        ? loadDiversifiedTop20(value -> setProgress(value))
                        : loadVolatileTopMovers(value -> setProgress(value));
            }

            @Override
            protected void done() {
                loadInFlight = false;
                refreshButton.setEnabled(true);
                progressBar.setVisible(false);
                try {
                    LoadResult result = get();
                    if (universe == StrategyUniverse.VOLATILE) {
                        cacheResult(result);
                    }
                    render(result);
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "I Am Feeling Lucky failed", ex);
                    statusLabel.setText("Failed to load " + sourceDisplayName().toLowerCase() + ": " + message(ex));
                    JOptionPane.showMessageDialog(
                            LuckyTrendingStocksDialog.this,
                            "Failed to load " + sourceDisplayName().toLowerCase() + ": " + message(ex),
                            "I Am Feeling Lucky",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        };
        progressBar.setVisible(true);
        progressBar.setString("Loading " + sourceDisplayName().toLowerCase() + "...");
        progressBar.setValue(0);
        refreshButton.setEnabled(false);
        placeButton.setEnabled(false);
        if (marketOpenAutoRefresh) {
            statusLabel.setText("Auto-refreshing volatile movers for market open...");
        } else {
            statusLabel.setText(forceRefresh
                    ? "Refreshing " + sourceDisplayName().toLowerCase() + "..."
                    : "Loading " + sourceDisplayName().toLowerCase() + "...");
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
            AutoAnalyzeBundle bundle = new AutoAnalyzeService(marketDataApi)
                    .analyzeBundle(stock.symbol(), 12, 15, stock.latestPrice());
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
            statusLabel.setText("No stocks were returned for " + sourceDisplayName().toLowerCase() + ".");
            placeButton.setEnabled(false);
            revalidate();
            repaint();
            return;
        }
        addGroup(result.primaryTitle() + " (" + result.gainers().size() + ")", result.gainers(), SECTION_GAINERS_BG, SECTION_GAINERS_BORDER);
        cardsPanel.add(Box.createVerticalStrut(6));
        addGroup(result.secondaryTitle() + " (" + result.losers().size() + ")", result.losers(), SECTION_LOSERS_BG, SECTION_LOSERS_BORDER);
        statusLabel.setText("Review " + cards.size() + " stock(s), choose quantity/term for each, then start " + modeLabel().toLowerCase() + " monitoring.");
        placeButton.setEnabled(cards.stream().anyMatch(StockCard::placeable));
        revalidate();
        repaint();
    }

    private LoadResult loadVolatileTopMovers(ProgressCallback progressCallback) throws Exception {
        TrendingStockGroups groups = trendingStocksService.topGainersAndLosers(10);
        if (groups.empty()) {
            progressCallback.set(100);
            return new LoadResult(List.of(), List.of(), "Top 10 Gainers", "Top 10 Losers");
        }
        List<TrendingStock> stocks = new ArrayList<>();
        stocks.addAll(groups.gainers());
        stocks.addAll(groups.losers());
        log("Top movers selected. gainers=" + groups.gainers().stream().map(TrendingStock::symbol).toList()
                + " losers=" + groups.losers().stream().map(TrendingStock::symbol).toList());
        return analyzeGroupedStocks(groups.gainers(), groups.losers(), "Top 10 Gainers", "Top 10 Losers", progressCallback);
    }

    private LoadResult loadDiversifiedTop20(ProgressCallback progressCallback) throws Exception {
        List<TrendingStock> stocks = buildDiversifiedStocks();
        List<TrendingStock> firstTen = stocks.subList(0, Math.min(10, stocks.size()));
        List<TrendingStock> secondTen = stocks.subList(Math.min(10, stocks.size()), stocks.size());
        log("Diversified stocks selected. symbols=" + stocks.stream().map(TrendingStock::symbol).toList());
        return analyzeGroupedStocks(
                firstTen,
                secondTen,
                "Top 20 Diversified Stocks - Set A",
                "Top 20 Diversified Stocks - Set B",
                progressCallback
        );
    }

    private LoadResult analyzeGroupedStocks(
            List<TrendingStock> firstGroup,
            List<TrendingStock> secondGroup,
            String firstTitle,
            String secondTitle,
            ProgressCallback progressCallback
    ) throws Exception {
        int taskCount = firstGroup.size() + secondGroup.size();
        if (taskCount <= 0) {
            return new LoadResult(List.of(), List.of(), firstTitle, secondTitle);
        }
        record GroupedStock(int groupIndex, TrendingStock stock) {}
        List<GroupedStock> groupedStocks = new ArrayList<>(taskCount);
        firstGroup.forEach(stock -> groupedStocks.add(new GroupedStock(0, stock)));
        secondGroup.forEach(stock -> groupedStocks.add(new GroupedStock(1, stock)));
        List<LuckyStockAnalysis> firstAnalyses = new ArrayList<>();
        List<LuckyStockAnalysis> secondAnalyses = new ArrayList<>();
        List<LuckyStockAnalysis> analyses = LuckyParallelExecutor.mapPreservingOrder(
                groupedStocks,
                "neuralarc-lucky-analysis",
                groupedStock -> analyze(groupedStock.stock()),
                completed -> progressCallback.set(percent(completed, taskCount))
        );
        for (int i = 0; i < groupedStocks.size(); i++) {
            if (groupedStocks.get(i).groupIndex() == 0) {
                firstAnalyses.add(analyses.get(i));
            } else {
                secondAnalyses.add(analyses.get(i));
            }
        }
        progressCallback.set(100);
        return new LoadResult(firstAnalyses, secondAnalyses, firstTitle, secondTitle);
    }

    private List<TrendingStock> buildDiversifiedStocks() {
        return diversifiedTop20Stocks(this::latestPriceForSymbol);
    }

    static List<TrendingStock> diversifiedTop20Stocks(Function<String, BigDecimal> latestPriceLookup) {
        return LuckyParallelExecutor.mapPreservingOrder(TOP_20_DIVERSIFIED_STOCKS, "neuralarc-lucky-price", entry -> {
            BigDecimal latestPrice = latestPriceLookup == null ? BigDecimal.ZERO : latestPriceLookup.apply(entry.symbol());
            return new TrendingStock(
                    entry.symbol(),
                    entry.companyName(),
                    latestPrice,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    entry.reason(),
                    BigDecimal.ZERO
            );
        }, null);
    }

    private BigDecimal latestPriceForSymbol(String symbol) {
        try {
            List<com.neuralarc.model.MarketBar> bars = marketDataApi.getIntradayBars(
                    symbol,
                    LocalDate.now().minusDays(5),
                    LocalDate.now(),
                    15
            );
            if (bars == null || bars.isEmpty()) {
                return BigDecimal.ZERO;
            }
            return bars.get(bars.size() - 1).close();
        } catch (Exception ex) {
            log("Price fetch fallback used for " + symbol + ": " + message(ex));
            return BigDecimal.ZERO;
        }
    }

    static List<String> diversifiedTop20Symbols() {
        return TOP_20_DIVERSIFIED_STOCKS.stream().map(DiversifiedStock::symbol).toList();
    }

    private String sourceDisplayName() {
        return universe == StrategyUniverse.DIVERSIFIED_TOP_20
                ? "Top 20 diversified stocks"
                : "trending volatile stocks";
    }

    private String sourceLogLabel() {
        return universe == StrategyUniverse.DIVERSIFIED_TOP_20
                ? "Top 20 diversified stocks"
                : "trending volatile movers";
    }

    private String initialStatusText() {
        return universe == StrategyUniverse.DIVERSIFIED_TOP_20
                ? "Click Refresh to load the curated top 20 diversified stocks."
                : "Click Refresh to load today's trending stocks.";
    }

    private String dialogDescriptionHtml() {
        if (universe == StrategyUniverse.DIVERSIFIED_TOP_20) {
            return "<html>Review curated diversified large-cap stocks, compare high-risk short-term and other recommendations, "
                    + "then start selected choices as Alpaca " + modeLabel() + " strategies.</html>";
        }
        return "<html>Review today's top gainers and losers, remove unwanted picks, then start the remaining choices as Alpaca "
                + modeLabel() + " strategies.</html>";
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
        if (reviewHandler != null) {
            reviewHandler.accept(selection);
        } else {
            placementHandler.accept(List.of(selection));
            log("Added " + selection.stock().symbol() + " to " + modeLabel().toLowerCase() + " strategy placement with "
                    + selection.selectedRecommendationType().name() + " qty=" + selection.buyQuantity() + ".");
        }
    }

    private void addGroup(String title, List<LuckyStockAnalysis> analyses, Color sectionBackground, Color sectionBorder) {
        JPanel group = new JPanel(new BorderLayout(0, 4));
        group.setOpaque(true);
        group.setBackground(sectionBackground);
        group.setAlignmentX(Component.LEFT_ALIGNMENT);
        group.setMaximumSize(new Dimension(SECTION_MAX_WIDTH, Short.MAX_VALUE));
        group.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(sectionBorder, 1, true),
                new EmptyBorder(8, 8, 8, 8)
        ));

        JLabel heading = new JLabel(title);
        heading.setFont(FontLoader.ui(Font.BOLD, 13f));
        heading.setForeground(SECTION_HEADER_TEXT);
        heading.setOpaque(true);
        heading.setBackground(sectionHeaderBackground(sectionBorder));
        heading.setBorder(new EmptyBorder(3, 6, 3, 6));
        group.add(heading, BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        body.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (analyses == null || analyses.isEmpty()) {
            JLabel empty = new JLabel("No stocks returned.");
            empty.setForeground(TEXT_PRIMARY);
            body.add(empty);
        }
        for (LuckyStockAnalysis analysis : analyses == null ? List.<LuckyStockAnalysis>of() : analyses) {
            StockCard card = new StockCard(analysis);
            cards.add(card);
            body.add(card);
            body.add(Box.createVerticalStrut(4));
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
            super(new BorderLayout(0, 6));
            this.analysis = analysis;
            setOpaque(true);
            setBackground(INPUT_BG);
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));
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
            tabs.setSelectedIndex(0);
            add(tabs, BorderLayout.CENTER);
            add(actionsRow(), BorderLayout.SOUTH);
        }

        private void styleTabs(JTabbedPane tabPane) {
            tabPane.setOpaque(true);
            tabPane.setBackground(INPUT_BG);
            tabPane.setForeground(TAB_UNSELECTED_TEXT);
            tabPane.setFont(FontLoader.ui(Font.BOLD, 11f));
            tabPane.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(TAB_BORDER, 1, true),
                    new EmptyBorder(1, 1, 1, 1)
            ));
            for (int i = 0; i < tabPane.getTabCount(); i++) {
                JLabel label = new JLabel(tabPane.getTitleAt(i), JLabel.CENTER);
                label.setFont(FontLoader.ui(Font.BOLD, 11f));
                label.setForeground(i == tabPane.getSelectedIndex() ? TAB_SELECTED_TEXT : TAB_UNSELECTED_TEXT);
                label.setOpaque(true);
                label.setBackground(i == tabPane.getSelectedIndex() ? TAB_SELECTED_BG : TAB_UNSELECTED_BG);
                label.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, TAB_BORDER),
                        new EmptyBorder(5, 12, 5, 12)
                ));
                tabPane.setTabComponentAt(i, label);
            }
            tabPane.addChangeListener(ignored -> refreshTabLabels(tabPane));
        }

        private void refreshTabLabels(JTabbedPane tabPane) {
            for (int i = 0; i < tabPane.getTabCount(); i++) {
                Component component = tabPane.getTabComponentAt(i);
                if (component instanceof JLabel label) {
                    boolean selected = i == tabPane.getSelectedIndex();
                    label.setForeground(selected ? TAB_SELECTED_TEXT : TAB_UNSELECTED_TEXT);
                    label.setBackground(selected ? TAB_SELECTED_BG : TAB_UNSELECTED_BG);
                }
            }
        }

        private JPanel header() {
            TrendingStock stock = analysis.stock();
            JPanel panel = new JPanel(new BorderLayout(0, 6));
            panel.setOpaque(false);
            String price = stock.latestPrice() == null || stock.latestPrice().compareTo(BigDecimal.ZERO) <= 0
                    ? "-"
                    : "$" + stock.latestPrice().toPlainString();
            String change = stock.dailyChangePercent() == null || stock.dailyChangePercent().compareTo(BigDecimal.ZERO) == 0
                    ? "-"
                    : stock.dailyChangePercent().toPlainString() + "%";
            String volume = stock.volume() == null || stock.volume().compareTo(BigDecimal.ZERO) == 0
                    ? "-"
                    : stock.volume().toPlainString();
            JLabel metrics = new JLabel("Price: " + price
                    + "  Change: " + change
                    + "  Vol: " + volume, JLabel.CENTER);
            metrics.setFont(FontLoader.ui(Font.BOLD, 11f));
            metrics.setForeground(TEXT_PRIMARY);

            panel.add(metrics, BorderLayout.CENTER);
            return panel;
        }

        private JPanel actionsRow() {
            JButton removeButton = new JButton();
            DialogButtonStyles.apply(removeButton, "icons/delete.svg");
            removeButton.setToolTipText("Remove this stock");
            removeButton.addActionListener(ignored -> removeCard(this));
            JButton addToCurrentButton = new JButton("Review New Stock Strategy");
            DialogButtonStyles.apply(addToCurrentButton, "icons/add-stock-strategy.svg");
            addToCurrentButton.addActionListener(ignored -> addToCurrentStrategy(this));
            addToCurrentButton.setToolTipText("Review and add to current strategy");

            JPanel qtyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            qtyPanel.setOpaque(false);
            JLabel qtyLabel = new JLabel("Qty:");
            qtyLabel.setFont(FontLoader.ui(Font.PLAIN, 10f));
            quantitySpinner.setFont(FontLoader.ui(Font.PLAIN, 10f));
            quantitySpinner.setPreferredSize(new Dimension(72, 24));
            qtyPanel.add(qtyLabel);
            qtyPanel.add(quantitySpinner);

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            actions.setOpaque(false);
            actions.add(qtyPanel);
            actions.add(addToCurrentButton);
            actions.add(removeButton);
            actions.setBorder(new EmptyBorder(4, 0, 0, 0));
            return actions;
        }

        private JPanel recommendationPanel(StrategyRecommendation recommendation) {
            JPanel panel = new JPanel(new GridLayout(0, 2, 8, 6));
            panel.setOpaque(false);
            panel.setBorder(new EmptyBorder(6, 6, 6, 6));
            addPair(panel, "Recommendation:", recommendation.recommendationAction().name());
            addPair(panel, "Confidence:", recommendation.confidenceScore() + "%");
            addPair(panel, "Base limit buy:", baseLimitBuyDisplay(recommendation));
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
            StrategyRecommendation recommendation = selectedRecommendation();
            return recommendation != null
                    && recommendation.isApplicable()
                    && usablePrice(recommendation.baseBuyPrice());
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
                        new EmptyBorder(6, 6, 6, 6)
                ),
                title
        );
        border.setTitleFont(FontLoader.ui(Font.BOLD, 12f));
        border.setTitleColor(TEXT_PRIMARY);
        return border;
    }

    private static Color sectionHeaderBackground(Color sectionBorder) {
        int red = Math.max(0, sectionBorder.getRed() - 55);
        int green = Math.max(0, sectionBorder.getGreen() - 55);
        int blue = Math.max(0, sectionBorder.getBlue() - 55);
        return new Color(red, green, blue);
    }


    private static String money(BigDecimal value) {
        return value == null ? "-" : "$" + value.toPlainString();
    }

    static String baseLimitBuyDisplay(StrategyRecommendation recommendation) {
        if (recommendation == null || !usablePrice(recommendation.baseBuyPrice())) {
            return "Not available";
        }
        return money(recommendation.baseBuyPrice());
    }

    private static boolean usablePrice(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private record LoadResult(
            List<LuckyStockAnalysis> gainers,
            List<LuckyStockAnalysis> losers,
            String primaryTitle,
            String secondaryTitle
    ) {}

    private record DiversifiedStock(String symbol, String companyName, String reason) {}

    @FunctionalInterface
    private interface ProgressCallback {
        void set(int value);
    }

    public enum StrategyUniverse {
        VOLATILE,
        DIVERSIFIED_TOP_20
    }

    private record CachedDailyTrends(LoadResult result, Instant loadedAt) {}
}
