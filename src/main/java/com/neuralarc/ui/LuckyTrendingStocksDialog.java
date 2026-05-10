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
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
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
import java.time.ZoneId;
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
    private static final Color TEXT_MUTED = new Color(130, 130, 130);
    private static final Color INPUT_BORDER = new Color(190, 190, 200);
    private static final int SECTION_WIDTH = 860;
    private static final int CARD_WIDTH = 820;

    private final TrendingStocksService trendingStocksService;
    private final AlpacaMarketDataApi marketDataApi;
    private final Consumer<List<LuckySimulationSelection>> placementHandler;
    private final Consumer<String> logSink;
    private final JPanel cardsPanel = new JPanel();
    private final JLabel statusLabel = new JLabel("Loading trending stocks...");
    private final JProgressBar progressBar = new JProgressBar();
    private final JButton placeButton = new JButton("Start Monitoring in Paper Mode");
    private final List<StockCard> cards = new ArrayList<>();

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
        loadAsync();
    }

    private void buildUi() {
        setLayout(new BorderLayout(12, 12));
        ((JPanel) getContentPane()).setBorder(new EmptyBorder(14, 14, 14, 14));

        JPanel top = new JPanel(new BorderLayout(10, 6));
        JLabel description = new JLabel("<html>Review today's top gainers and losers, remove unwanted picks, then start the remaining choices as Alpaca Paper strategies.</html>");
        description.setForeground(TEXT_MUTED);
        description.setFont(FontLoader.ui(Font.PLAIN, 11f));
        progressBar.setIndeterminate(true);
        top.add(description, BorderLayout.NORTH);
        top.add(progressBar, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        cardsPanel.setLayout(new BoxLayout(cardsPanel, BoxLayout.Y_AXIS));
        cardsPanel.setOpaque(false);
        JScrollPane scrollPane = new JScrollPane(cardsPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setPreferredSize(new Dimension(920, 660));
        add(scrollPane, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(12, 0));
        statusLabel.setForeground(TEXT_MUTED);
        placeButton.setEnabled(false);
        DialogButtonStyles.apply(placeButton, "icons/apply.svg");
        placeButton.addActionListener(e -> placeReviewedStocks());
        JButton closeButton = new JButton("Close");
        DialogButtonStyles.apply(closeButton, "icons/close.svg");
        closeButton.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(placeButton);
        buttons.add(closeButton);
        bottom.add(statusLabel, BorderLayout.CENTER);
        bottom.add(buttons, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        pack();
        setMinimumSize(new Dimension(820, 560));
        setLocationRelativeTo(getOwner());
    }

    private void loadAsync() {
        log("I Am Feeling Lucky clicked. Loading trending stocks.");
        SwingWorker<LoadResult, Void> worker = new SwingWorker<>() {
            @Override
            protected LoadResult doInBackground() throws Exception {
                TrendingStockGroups groups = trendingStocksService.topGainersAndLosers(10);
                if (groups.empty()) {
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
                    for (var future : executor.invokeAll(gainerTasks)) {
                        gainers.add(future.get());
                    }
                    List<LuckyStockAnalysis> losers = new ArrayList<>();
                    for (var future : executor.invokeAll(loserTasks)) {
                        losers.add(future.get());
                    }
                    return new LoadResult(gainers, losers);
                } finally {
                    executor.shutdownNow();
                }
            }

            @Override
            protected void done() {
                progressBar.setIndeterminate(false);
                try {
                    render(get());
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
        addGroup("Top 10 Gainers", result.gainers());
        cardsPanel.add(Box.createVerticalStrut(12));
        addGroup("Top 10 Losers", result.losers());
        statusLabel.setText("Review " + cards.size() + " stock(s), remove unwanted picks, then start paper monitoring.");
        placeButton.setEnabled(cards.stream().anyMatch(StockCard::placeable));
        revalidate();
        repaint();
    }

    private void addGroup(String title, List<LuckyStockAnalysis> analyses) {
        JPanel group = new JPanel();
        group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));
        group.setOpaque(false);
        group.setAlignmentX(Component.LEFT_ALIGNMENT);
        group.setMaximumSize(new Dimension(SECTION_WIDTH, Short.MAX_VALUE));
        group.setBorder(createBorder(title));
        if (analyses == null || analyses.isEmpty()) {
            JLabel empty = new JLabel("No stocks returned.");
            empty.setForeground(TEXT_MUTED);
            group.add(empty);
        }
        for (LuckyStockAnalysis analysis : analyses == null ? List.<LuckyStockAnalysis>of() : analyses) {
            StockCard card = new StockCard(analysis);
            cards.add(card);
            group.add(card);
            group.add(Box.createVerticalStrut(10));
        }
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

    private void log(String message) {
        LOGGER.info(message);
        SwingUtilities.invokeLater(() -> logSink.accept("[I Am Feeling Lucky] " + message));
    }

    private final class StockCard extends JPanel {
        private final LuckyStockAnalysis analysis;
        private final JTabbedPane tabs = new JTabbedPane();

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
            tabs.setSelectedIndex(1);
            add(tabs, BorderLayout.CENTER);
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
            removeButton.addActionListener(e -> removeCard(this));
            JPanel left = new JPanel(new BorderLayout(0, 4));
            left.setOpaque(false);
            left.add(details, BorderLayout.CENTER);
            panel.add(left, BorderLayout.CENTER);
            panel.add(removeButton, BorderLayout.EAST);
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
            JLabel valueComponent = new JLabel("<html>" + escape(value) + "</html>");
            valueComponent.setFont(FontLoader.ui(Font.BOLD, 11f));
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
            return new LuckySimulationSelection(analysis.stock(), analysis.analysis(), selectedType());
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
        return border;
    }

    private static JTextArea textArea(String text) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(FontLoader.ui(Font.PLAIN, 11f));
        return area;
    }

    private static String money(BigDecimal value) {
        return value == null ? "-" : "$" + value.toPlainString();
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private record LoadResult(List<LuckyStockAnalysis> gainers, List<LuckyStockAnalysis> losers) {}
}
