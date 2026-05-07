package com.neuralarc.ui;

import com.neuralarc.api.AlpacaMarketDataApi;
import com.neuralarc.model.AutoAnalyzeBundle;
import com.neuralarc.model.AutoAnalyzeResult;
import com.neuralarc.model.MarketMode;
import com.neuralarc.model.ProfitControlMode;
import com.neuralarc.model.ProfitHoldType;
import com.neuralarc.model.RecommendationAction;
import com.neuralarc.model.RecommendationType;
import com.neuralarc.model.ShortTermMarketMode;
import com.neuralarc.model.StrategyConfig;
import com.neuralarc.model.StrategyRecommendation;
import com.neuralarc.model.ThresholdType;
import com.neuralarc.model.TrailingType;
import com.neuralarc.service.AutoAnalyzeResultStore;
import com.neuralarc.service.AutoAnalyzeService;
import com.neuralarc.service.StrategyApplyService;
import com.neuralarc.util.AppMetadata;
import com.neuralarc.util.FontLoader;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.io.InputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class StrategyDialog extends JDialog {
    private static final int TAB_CURRENT_STRATEGY = 0;
    private static final int TAB_AUTO_ANALYZE = 1;
    private static final int OUTER_PADDING = 16;
    private static final int SECTION_GAP = 12;
    private static final int FIELD_GAP = 10;
    private static final int SECTION_INNER_PADDING = 10;
    private static final int RISK_CONTROLS_HORIZONTAL_PADDING = 12;
    private static final int MAX_MONTHS_BACK = 12;
    private static final int DIALOG_TARGET_WIDTH = 860;
    private static final int DIALOG_TARGET_HEIGHT = 720;
    private static final double DIALOG_MAX_SCREEN_HEIGHT_RATIO = 0.88d;
    private static final int DIALOG_SCREEN_MARGIN = 48;
    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm").withZone(ZoneId.systemDefault());

    private static final Color DIALOG_BG = UIManager.getColor("Panel.background") != null
            ? UIManager.getColor("Panel.background")
            : Color.WHITE;
    private static final Color INPUT_BG = UIManager.getColor("TextField.background") != null
            ? UIManager.getColor("TextField.background")
            : Color.WHITE;
    private static final Color INPUT_BORDER = new Color(190, 190, 200);
    private static final Color TEXT_PRIMARY = UIManager.getColor("Label.foreground") != null
            ? UIManager.getColor("Label.foreground")
            : new Color(45, 45, 50);
    private static final Color TEXT_MUTED = UIManager.getColor("Label.disabledForeground") != null
            ? UIManager.getColor("Label.disabledForeground")
            : new Color(130, 130, 130);
    private static final Color TAB_BORDER = new Color(204, 214, 225);
    private static final Color TAB_SELECTED_BG = new Color(235, 244, 252);
    private static final Color TAB_UNSELECTED_BG = new Color(246, 248, 250);
    private static final Color TAB_SELECTED_TEXT = new Color(11, 84, 132);
    private static final Color TAB_UNSELECTED_TEXT = new Color(75, 85, 99);
    private static final Color BUY_BADGE_BG = new Color(219, 241, 229);
    private static final Color BUY_BADGE_TEXT = new Color(22, 110, 62);
    private static final Color WATCH_BADGE_BG = new Color(255, 242, 214);
    private static final Color WATCH_BADGE_TEXT = new Color(154, 92, 13);
    private static final Color AVOID_BADGE_BG = new Color(250, 223, 223);
    private static final Color AVOID_BADGE_TEXT = new Color(166, 45, 45);
    private static final Color WARNING_TEXT = new Color(166, 45, 45);
    private static final Color INFO_TEXT = new Color(180, 115, 20);
    private static final Color BREAKOUT_INFO_TEXT = new Color(35, 95, 180);

    private static final String DEFAULT_CONFIG_RESOURCE = "defaults-config.properties";
    private static final DialogDefaults DEFAULTS = loadDefaults();

    private final JTextField symbolField = new JTextField(25);
    private final JCheckBox paperMode = new JCheckBox("Paper trading mode", true);
    private final JTextField basePriceField = new JTextField(25);
    private final JTextField baseQtyField = new JTextField(25);
    private final JCheckBox stopLossEnabled = new JCheckBox("Enable Stop Loss", true);
    private final JTextField stopLossField = new JTextField(25);
    private final ProfitControlsPanel profitControlsPanel = new ProfitControlsPanel();
    private final JTextField loss1PriceField = new JTextField(25);
    private final JTextField loss1QtyField = new JTextField(25);
    private final JTextField loss2PriceField = new JTextField(25);
    private final JTextField loss2QtyField = new JTextField(25);
    private final JCheckBox lossBuyLevelsEnabled = new JCheckBox("Enable Loss Buy Levels", true);
    private final JTextField pollingField = new JTextField(25);
    private final JCheckBox repeatCycleAfterProfitExitEnabled = new JCheckBox("Repeat cycle after profitable exit", false);

    private final JTextArea highSeriesArea = new JTextArea(8, 22);
    private final JTextArea lowSeriesArea = new JTextArea(8, 22);
    private final JLabel medianStatus = new JLabel("Paste 6-month daily highs and lows, then apply to current strategy.");
    private JTabbedPane tabs;

    private StrategyConfig result;

    // ---- Auto Analyze fields ----
    private final JTextField aaSymbolField = new JTextField(20);
    private final JSpinner aaMonthsSpinner = new JSpinner(new SpinnerNumberModel(MAX_MONTHS_BACK, 1, MAX_MONTHS_BACK, 1));
    private final JSpinner aaIntervalSpinner = new JSpinner(new SpinnerNumberModel(15, 1, 60, 5));
    private final JButton aaRunButton = new JButton("Run Auto Analyze");
    private final JLabel aaStatusLabel = new JLabel(" ");
    private final JLabel aaAvgOpenLabel = new JLabel("—");
    private final JLabel aaAvgCloseLabel = new JLabel("—");
    private final JLabel aaAvgLowLabel = new JLabel("—");
    private final JLabel aaAvgHighLabel = new JLabel("—");
    private final JLabel aaOneWeekLowLabel = new JLabel("—");
    private final JLabel aaOneWeekHighLabel = new JLabel("—");
    private final JLabel aaTwoWeekLowLabel = new JLabel("—");
    private final JLabel aaTwoWeekHighLabel = new JLabel("—");
    private final JLabel aaOneMonthLowLabel = new JLabel("—");
    private final JLabel aaOneMonthHighLabel = new JLabel("—");
    private final JLabel aaTwoMonthLowLabel = new JLabel("—");
    private final JLabel aaTwoMonthHighLabel = new JLabel("—");
    private final JLabel aaFourMonthLowLabel = new JLabel("—");
    private final JLabel aaFourMonthHighLabel = new JLabel("—");
    private final JLabel aaSixMonthLowLabel = new JLabel("—");
    private final JLabel aaSixMonthHighLabel = new JLabel("—");
    private final JLabel aaEightMonthLowLabel = new JLabel("—");
    private final JLabel aaEightMonthHighLabel = new JLabel("—");
    private final JLabel aaOneYearLowLabel = new JLabel("—");
    private final JLabel aaOneYearHighLabel = new JLabel("—");
    private final JLabel aaTodayStockPriceLabel = new JLabel("—");
    private final JLabel aaTodayOpenLabel = new JLabel("—");
    private final JLabel aaTodayHighSoFarLabel = new JLabel("—");
    private final JLabel aaTodayLowSoFarLabel = new JLabel("—");
    private final JLabel aaTodayCloseLabel = new JLabel("—");
    private final JLabel aaThresholdLabel = new JLabel("—");
    private final JLabel aaDailyBarsLabel = new JLabel("—");
    private final JLabel aaIntradayBarsLabel = new JLabel("—");
    private final JLabel aaAnalyzedAtLabel = new JLabel("—");
    private final JLabel aaResultsLoadingLabel = new JLabel("Loading analysis data...");
    private final JProgressBar aaResultsProgressBar = new JProgressBar();
    private final RecommendationView shortTermRecommendationView = new RecommendationView("Short-Term Recommendation");
    private final RecommendationView longTermRecommendationView = new RecommendationView("Long-Term Recommendation");

    private AutoAnalyzeResult lastAutoAnalyzeResult;
    private StrategyRecommendation lastShortTermRecommendation;
    private StrategyRecommendation lastLongTermRecommendation;
    private final AlpacaMarketDataApi marketDataApi;
    private final AutoAnalyzeResultStore resultStore;
    private final StrategyApplyService strategyApplyService = new StrategyApplyService();

    /** Backward-compatible constructor – Auto Analyze tab is visible but requires connection. */
    public StrategyDialog(JFrame owner, StrategyConfig initialConfig) {
        this(owner, initialConfig, null, null);
    }

    public StrategyDialog(JFrame owner, StrategyConfig initialConfig,
                          AlpacaMarketDataApi marketDataApi, AutoAnalyzeResultStore resultStore) {
        super(owner, initialConfig == null ? "Add Stock Strategy" : "Edit Stock Strategy", true);
        this.marketDataApi = marketDataApi;
        this.resultStore = resultStore;
        setLayout(new BorderLayout(SECTION_GAP, SECTION_GAP));

        applyDialogDefaults();
        configureTooltips();
        wireStopLossFields();
        wireLossBuyLevelFields();
        styleInputs();

        // Pre-fill Auto Analyze symbol from the strategy config if available
        if (initialConfig != null && initialConfig.symbol() != null && !initialConfig.symbol().isBlank()) {
            aaSymbolField.setText(initialConfig.symbol().toUpperCase());
        }

        tabs = new JTabbedPane();
        styleTabs();
        tabs.addTab("Current Strategy", buildCurrentStrategyTab());
        tabs.addTab("Auto Analyze", buildAutoAnalyzeTab());
        add(tabs, BorderLayout.CENTER);

        JPanel actions = new JPanel(new BorderLayout());
        actions.setBorder(new EmptyBorder(0, OUTER_PADDING, OUTER_PADDING, OUTER_PADDING));
        JButton helpFaq = new JButton("Help & FAQ");
        DialogButtonStyles.apply(helpFaq, "icons/faqs.svg");
        helpFaq.addActionListener(e -> new HelpDialog(owner).setVisible(true));
        JButton save = new JButton("Save Strategy");
        DialogButtonStyles.apply(save, "icons/save.svg");
        save.addActionListener(e -> onSaveForSelectedTab());
        JButton cancel = new JButton("Cancel");
        DialogButtonStyles.apply(cancel, "icons/close.svg");
        cancel.addActionListener(e -> {
            result = null;
            setVisible(false);
        });
        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightActions.setOpaque(false);
        rightActions.add(save);
        rightActions.add(cancel);
        actions.add(helpFaq, BorderLayout.WEST);
        actions.add(rightActions, BorderLayout.EAST);
        add(actions, BorderLayout.SOUTH);

        applyDialogTheme();

        if (initialConfig != null) {
            applyConfig(initialConfig);
        }

        // Load cached Auto Analyze result for this symbol (if any)
        loadCachedAutoAnalyzeResult(initialConfig != null ? initialConfig.symbol() : null);

        pack();
        fitDialogToScreen(owner);
    }

    public StrategyConfig showDialog() {
        result = null;
        setVisible(true);
        return result;
    }

    private JComponent buildCurrentStrategyTab() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(OUTER_PADDING, OUTER_PADDING, OUTER_PADDING, OUTER_PADDING));

        JPanel strategyPanel = new JPanel(new GridLayout(0, 2, FIELD_GAP, FIELD_GAP));
        strategyPanel.setBorder(createSectionBorder("Strategy Parameters"));
        addRow(strategyPanel, "Symbol:", symbolField);
        addRow(strategyPanel, "Trading mode:", paperMode);
        addRow(strategyPanel, "Base buy price:", basePriceField);
        addRow(strategyPanel, "Base buy quantity:", baseQtyField);
        prepareSection(strategyPanel);

        // Risk Controls — outer wrapper with three sub-sections
        JPanel riskPanel = new JPanel();
        riskPanel.setLayout(new BoxLayout(riskPanel, BoxLayout.Y_AXIS));
        riskPanel.setBorder(createSectionBorder("Risk Controls"));
        prepareSection(riskPanel);

        JPanel riskContent = new JPanel();
        riskContent.setLayout(new BoxLayout(riskContent, BoxLayout.Y_AXIS));
        riskContent.setBorder(new EmptyBorder(0, RISK_CONTROLS_HORIZONTAL_PADDING, 0, RISK_CONTROLS_HORIZONTAL_PADDING));
        riskContent.setOpaque(false);

        JPanel stopLossSubPanel = new JPanel(new GridLayout(0, 2, FIELD_GAP, FIELD_GAP));
        stopLossSubPanel.setBorder(createSubSectionBorder("Stop Loss"));
        prepareSubSection(stopLossSubPanel);
        addRow(stopLossSubPanel, "Set Stop Loss:", stopLossEnabled);
        addRow(stopLossSubPanel, "Stop loss price:", stopLossField);

        JPanel lossBuySubPanel = new JPanel(new GridLayout(0, 2, FIELD_GAP, FIELD_GAP));
        lossBuySubPanel.setBorder(createSubSectionBorder("Loss Buy Levels"));
        prepareSubSection(lossBuySubPanel);
        addRow(lossBuySubPanel, "Enable:", lossBuyLevelsEnabled);
        addRow(lossBuySubPanel, "Loss Buy Level 1 Price:", loss1PriceField);
        addRow(lossBuySubPanel, "Loss buy level 1 qty:", loss1QtyField);
        addRow(lossBuySubPanel, "Loss Buy Level 2 Price:", loss2PriceField);
        addRow(lossBuySubPanel, "Loss buy level 2 qty:", loss2QtyField);

        riskContent.add(stopLossSubPanel);
        riskContent.add(Box.createVerticalStrut(SECTION_GAP));
        riskContent.add(lossBuySubPanel);
        riskPanel.add(riskContent);

        JPanel executionPanel = new JPanel(new GridLayout(0, 2, FIELD_GAP, FIELD_GAP));
        executionPanel.setBorder(createSectionBorder("Execution"));
        addRow(executionPanel, "Polling interval seconds:", pollingField);
        prepareSection(executionPanel);

        JPanel cycleBehaviorPanel = new JPanel(new GridLayout(0, 2, FIELD_GAP, FIELD_GAP));
        cycleBehaviorPanel.setBorder(createSectionBorder("Cycle Behavior"));
        addRow(cycleBehaviorPanel, "Repeat cycle after profitable exit (optional):", repeatCycleAfterProfitExitEnabled);
        prepareSection(cycleBehaviorPanel);

        content.add(strategyPanel);
        content.add(Box.createVerticalStrut(SECTION_GAP));
        content.add(profitControlsPanel);
        content.add(Box.createVerticalStrut(SECTION_GAP));
        content.add(riskPanel);
        content.add(Box.createVerticalStrut(SECTION_GAP));
        content.add(executionPanel);
        content.add(Box.createVerticalStrut(SECTION_GAP));
        content.add(cycleBehaviorPanel);

        return createContentScrollPane(content);
    }

    private JComponent buildMedianStrategyTab() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(OUTER_PADDING, OUTER_PADDING, OUTER_PADDING, OUTER_PADDING));

        JPanel inputPanel = new JPanel(new GridLayout(1, 2, FIELD_GAP, FIELD_GAP));
        inputPanel.setBorder(createSectionBorder("6-Month Daily Data Input"));

        inputPanel.add(wrapTextArea("Daily High Prices", highSeriesArea));
        inputPanel.add(wrapTextArea("Daily Low Prices", lowSeriesArea));

        JPanel actionsPanel = new JPanel(new BorderLayout(0, 8));
        actionsPanel.setBorder(createSectionBorder("Apply Median Strategy to Current Strategy"));

        JLabel ruleLabel = new JLabel("Average of each day midpoint ((high + low)/2) is used as the base price anchor.");
        ruleLabel.setForeground(TEXT_MUTED);
        ruleLabel.setFont(FontLoader.ui(java.awt.Font.PLAIN, 10f));

        JButton apply = new JButton("Apply 6-Month Median to Current Strategy");
        DialogButtonStyles.apply(apply, "icons/apply.svg");
        apply.addActionListener(e -> applyMedianStrategyValues());

        medianStatus.setForeground(TEXT_MUTED);
        medianStatus.setFont(FontLoader.ui(java.awt.Font.PLAIN, 10f));

        actionsPanel.add(ruleLabel, BorderLayout.NORTH);
        actionsPanel.add(apply, BorderLayout.CENTER);
        actionsPanel.add(medianStatus, BorderLayout.SOUTH);

        content.add(inputPanel);
        content.add(Box.createVerticalStrut(SECTION_GAP));
        content.add(actionsPanel);

        return createContentScrollPane(content);
    }

    private JComponent buildAutoAnalyzeTab() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(OUTER_PADDING, OUTER_PADDING, OUTER_PADDING, OUTER_PADDING));

        styleInput(aaSymbolField);
        styleSpinner(aaMonthsSpinner);
        styleSpinner(aaIntervalSpinner);

        // -- Analysis Parameters: fields + Run button docked to the right at the bottom --
        JPanel inputPanel = new JPanel(new BorderLayout(0, FIELD_GAP));
        inputPanel.setBorder(createSectionBorder("Analysis Parameters"));
        inputPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        inputPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));

        JPanel inputFields = new JPanel(new GridLayout(0, 2, FIELD_GAP, FIELD_GAP));
        inputFields.setOpaque(false);
        addRow(inputFields, "Symbol:", aaSymbolField);
        addRow(inputFields, "Months back (max 12):", aaMonthsSpinner);
        addRow(inputFields, "Intraday interval (minutes):", aaIntervalSpinner);

        DialogButtonStyles.apply(aaRunButton, "icons/actions.svg");
        if (marketDataApi == null) {
            aaRunButton.setEnabled(false);
            aaRunButton.setToolTipText(TooltipStyler.text("Please connect to Alpaca (Settings -> Verify Connection) first."));
        } else {
            aaRunButton.addActionListener(e -> runAutoAnalyze());
        }
        // Allow pressing Enter in the symbol field to trigger analysis (same guard as the button)
        aaSymbolField.addActionListener(e -> {
            if (aaRunButton.isEnabled()) {
                runAutoAnalyze();
            }
        });
        JPanel runRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        runRow.setOpaque(false);
        runRow.add(aaRunButton);

        inputPanel.add(inputFields, BorderLayout.CENTER);
        inputPanel.add(runRow, BorderLayout.SOUTH);

        // -- Results: price averages in a 4-column sub-grid, analysis details below --
        JPanel resultsPanel = new JPanel();
        resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
        resultsPanel.setBorder(createSectionBorder("Results"));
        resultsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        resultsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));

        JPanel resultsLoadingPanel = new JPanel(new BorderLayout(8, 0));
        resultsLoadingPanel.setOpaque(false);
        resultsLoadingPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        aaResultsLoadingLabel.setForeground(TEXT_MUTED);
        aaResultsLoadingLabel.setFont(FontLoader.ui(java.awt.Font.PLAIN, 10f));
        aaResultsProgressBar.setIndeterminate(true);
        aaResultsProgressBar.setVisible(false);
        aaResultsLoadingLabel.setVisible(false);
        resultsLoadingPanel.add(aaResultsLoadingLabel, BorderLayout.WEST);
        resultsLoadingPanel.add(aaResultsProgressBar, BorderLayout.CENTER);

        resultsPanel.add(resultsLoadingPanel);
        resultsPanel.add(Box.createVerticalStrut(SECTION_GAP));

        // Price averages: 4 columns, label–value pairs side by side
        JPanel avgSubPanel = new JPanel(new GridLayout(0, 4, FIELD_GAP, FIELD_GAP));
        avgSubPanel.setOpaque(false);
        avgSubPanel.setBorder(createSubSectionBorder("Price Averages"));
        avgSubPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        avgSubPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));
        avgSubPanel.add(new JLabel("Avg Open:"));
        avgSubPanel.add(aaAvgOpenLabel);
        avgSubPanel.add(new JLabel("Avg Close:"));
        avgSubPanel.add(aaAvgCloseLabel);
        avgSubPanel.add(new JLabel("Avg Low:"));
        avgSubPanel.add(aaAvgLowLabel);
        avgSubPanel.add(new JLabel("Avg High:"));
        avgSubPanel.add(aaAvgHighLabel);

        // Analysis details: 2-column compact grid
        JPanel detailsSubPanel = new JPanel(new GridLayout(0, 2, FIELD_GAP, FIELD_GAP));
        detailsSubPanel.setOpaque(false);
        detailsSubPanel.setBorder(createSubSectionBorder("Analysis Details"));
        detailsSubPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailsSubPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));
        detailsSubPanel.add(new JLabel("Threshold Number:"));
        detailsSubPanel.add(aaThresholdLabel);
        detailsSubPanel.add(new JLabel("Daily bars processed:"));
        detailsSubPanel.add(aaDailyBarsLabel);
        detailsSubPanel.add(new JLabel("Intraday bars processed:"));
        detailsSubPanel.add(aaIntradayBarsLabel);
        detailsSubPanel.add(new JLabel("Analysed at:"));
        detailsSubPanel.add(aaAnalyzedAtLabel);

        resultsPanel.add(avgSubPanel);
        resultsPanel.add(Box.createVerticalStrut(SECTION_GAP));

        // Price Ranges: low/high across requested lookback windows
        JPanel rangesSubPanel = new JPanel(new GridLayout(0, 4, FIELD_GAP, FIELD_GAP));
        rangesSubPanel.setOpaque(false);
        rangesSubPanel.setBorder(createSubSectionBorder("Price Ranges"));
        rangesSubPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        rangesSubPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));
        rangesSubPanel.add(new JLabel("1-Week Low:"));
        rangesSubPanel.add(aaOneWeekLowLabel);
        rangesSubPanel.add(new JLabel("1-Week High:"));
        rangesSubPanel.add(aaOneWeekHighLabel);
        rangesSubPanel.add(new JLabel("2-Week Low:"));
        rangesSubPanel.add(aaTwoWeekLowLabel);
        rangesSubPanel.add(new JLabel("2-Week High:"));
        rangesSubPanel.add(aaTwoWeekHighLabel);
        rangesSubPanel.add(new JLabel("1-Month Low:"));
        rangesSubPanel.add(aaOneMonthLowLabel);
        rangesSubPanel.add(new JLabel("1-Month High:"));
        rangesSubPanel.add(aaOneMonthHighLabel);
        rangesSubPanel.add(new JLabel("2-Month Low:"));
        rangesSubPanel.add(aaTwoMonthLowLabel);
        rangesSubPanel.add(new JLabel("2-Month High:"));
        rangesSubPanel.add(aaTwoMonthHighLabel);
        rangesSubPanel.add(new JLabel("4-Month Low:"));
        rangesSubPanel.add(aaFourMonthLowLabel);
        rangesSubPanel.add(new JLabel("4-Month High:"));
        rangesSubPanel.add(aaFourMonthHighLabel);
        rangesSubPanel.add(new JLabel("6-Month Low:"));
        rangesSubPanel.add(aaSixMonthLowLabel);
        rangesSubPanel.add(new JLabel("6-Month High:"));
        rangesSubPanel.add(aaSixMonthHighLabel);
        rangesSubPanel.add(new JLabel("8-Month Low:"));
        rangesSubPanel.add(aaEightMonthLowLabel);
        rangesSubPanel.add(new JLabel("8-Month High:"));
        rangesSubPanel.add(aaEightMonthHighLabel);
        rangesSubPanel.add(new JLabel("1-Year Low:"));
        rangesSubPanel.add(aaOneYearLowLabel);
        rangesSubPanel.add(new JLabel("1-Year High:"));
        rangesSubPanel.add(aaOneYearHighLabel);

        resultsPanel.add(rangesSubPanel);
        resultsPanel.add(Box.createVerticalStrut(SECTION_GAP));

        JPanel todaySubPanel = new JPanel(new GridLayout(0, 4, FIELD_GAP, FIELD_GAP));
        todaySubPanel.setOpaque(false);
        todaySubPanel.setBorder(createSubSectionBorder("Today's Snapshot"));
        todaySubPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        todaySubPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));
        todaySubPanel.add(new JLabel("Stock Price:"));
        todaySubPanel.add(aaTodayStockPriceLabel);
        todaySubPanel.add(new JLabel("Open:"));
        todaySubPanel.add(aaTodayOpenLabel);
        todaySubPanel.add(new JLabel("High So Far:"));
        todaySubPanel.add(aaTodayHighSoFarLabel);
        todaySubPanel.add(new JLabel("Low So Far:"));
        todaySubPanel.add(aaTodayLowSoFarLabel);
        todaySubPanel.add(new JLabel("Close (if available):"));
        todaySubPanel.add(aaTodayCloseLabel);

        resultsPanel.add(todaySubPanel);
        resultsPanel.add(Box.createVerticalStrut(SECTION_GAP));
        resultsPanel.add(buildRecommendationSection(shortTermRecommendationView, RecommendationTypeLabel.SHORT_TERM));
        resultsPanel.add(Box.createVerticalStrut(SECTION_GAP));
        resultsPanel.add(buildRecommendationSection(longTermRecommendationView, RecommendationTypeLabel.LONG_TERM));
        resultsPanel.add(Box.createVerticalStrut(SECTION_GAP));
        resultsPanel.add(detailsSubPanel);

        JPanel disclaimerPanel = new JPanel(new BorderLayout());
        disclaimerPanel.setBorder(createSectionBorder("Disclaimer"));
        disclaimerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        disclaimerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));
        JLabel disclaimerLabel = new JLabel("<html>Auto Analyze is a decision-support tool. It does not guarantee profit. Review all values before saving or trading.</html>");
        disclaimerLabel.setForeground(TEXT_MUTED);
        disclaimerLabel.setFont(FontLoader.ui(java.awt.Font.PLAIN, 10f));
        disclaimerPanel.add(disclaimerLabel, BorderLayout.CENTER);

        aaStatusLabel.setFont(FontLoader.ui(java.awt.Font.PLAIN, 10f));
        aaStatusLabel.setForeground(TEXT_MUTED);

        content.add(inputPanel);
        content.add(Box.createVerticalStrut(SECTION_GAP));
        content.add(resultsPanel);
        content.add(Box.createVerticalStrut(SECTION_GAP));
        content.add(disclaimerPanel);
        content.add(Box.createVerticalStrut(SECTION_GAP));
        content.add(aaStatusLabel);

        return createContentScrollPane(content);
    }

    private JPanel wrapTextArea(String title, JTextArea area) {
        area.setLineWrap(true);
        area.setWrapStyleWord(true);

        JPanel wrapper = new JPanel(new BorderLayout(0, 6));
        wrapper.add(new JLabel(title), BorderLayout.NORTH);
        wrapper.add(new JScrollPane(area), BorderLayout.CENTER);
        return wrapper;
    }

    private JScrollPane createContentScrollPane(JPanel content) {
        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setPreferredSize(new Dimension(DIALOG_TARGET_WIDTH - (OUTER_PADDING * 2), DIALOG_TARGET_HEIGHT - 180));
        return scrollPane;
    }

    private void fitDialogToScreen(JFrame owner) {
        Rectangle screenBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int maxWidth = Math.max(720, screenBounds.width - DIALOG_SCREEN_MARGIN);
        int maxHeight = Math.max(560, Math.min(
                screenBounds.height - DIALOG_SCREEN_MARGIN,
                (int) Math.round(screenBounds.height * DIALOG_MAX_SCREEN_HEIGHT_RATIO)
        ));

        Dimension preferred = getPreferredSize();
        int width = Math.min(Math.max(preferred.width, DIALOG_TARGET_WIDTH), maxWidth);
        int height = Math.min(Math.max(preferred.height, DIALOG_TARGET_HEIGHT), maxHeight);

        setPreferredSize(new Dimension(width, height));
        setSize(width, height);
        setMinimumSize(new Dimension(Math.min(width, 760), Math.min(height, 620)));
        setResizable(true);
        setLocationRelativeTo(owner);
    }

    private void applyMedianStrategyValues() {
        List<BigDecimal> highs;
        List<BigDecimal> lows;
        try {
            highs = parseSeries(highSeriesArea.getText());
            lows = parseSeries(lowSeriesArea.getText());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                    "Please provide only numeric values for highs and lows.",
                    "Invalid Median Input",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (highs.isEmpty() || lows.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please enter at least one high and one low value.",
                    "Missing Median Input",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (highs.size() != lows.size()) {
            JOptionPane.showMessageDialog(this,
                    "High and low series must have the same number of entries (one pair per day).",
                    "Mismatched Series",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        BigDecimal midpointSum = BigDecimal.ZERO;
        for (int i = 0; i < highs.size(); i++) {
            BigDecimal midpoint = highs.get(i).add(lows.get(i))
                    .divide(new BigDecimal("2"), 8, RoundingMode.HALF_UP);
            midpointSum = midpointSum.add(midpoint);
        }

        BigDecimal averageMidpoint = midpointSum.divide(new BigDecimal(highs.size()), 8, RoundingMode.HALF_UP);
        BigDecimal base = averageMidpoint.setScale(2, RoundingMode.HALF_UP);
        BigDecimal stopLoss = base.multiply(new BigDecimal("0.97")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal sellTrigger = base.multiply(new BigDecimal("1.10")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal loss1 = base.multiply(new BigDecimal("0.92")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal loss2 = base.multiply(new BigDecimal("0.85")).setScale(2, RoundingMode.HALF_UP);

        basePriceField.setText(base.toPlainString());
        stopLossField.setText(stopLoss.toPlainString());
        profitControlsPanel.setSellTriggerPrice(sellTrigger);
        loss1PriceField.setText(loss1.toPlainString());
        loss2PriceField.setText(loss2.toPlainString());

        medianStatus.setText("Applied median strategy from " + highs.size() + " daily pairs to Current Strategy tab.");
        tabs.setSelectedIndex(0);
    }

    private List<BigDecimal> parseSeries(String raw) {
        List<BigDecimal> values = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return values;
        }
        String[] tokens = raw.trim().split("[,\\s]+");
        for (String token : tokens) {
            if (!token.isBlank()) {
                values.add(new BigDecimal(token));
            }
        }
        return values;
    }

    private void addRow(JPanel panel, String label, JComponent component) {
        JLabel rowLabel = new JLabel(label);
        rowLabel.setLabelFor(component);
        rowLabel.setToolTipText(component.getToolTipText());
        panel.add(rowLabel);
        panel.add(component);
    }

    private void prepareSection(JPanel panel) {
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));
    }

    private void prepareSubSection(JPanel panel) {
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));
    }

    private void lockPreferredHeight(JComponent component) {
        Dimension preferred = component.getPreferredSize();
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));
    }

    private Border createSectionBorder(String title) {
        TitledBorder border = new TitledBorder(title);
        border.setTitleColor(TEXT_PRIMARY);
        border.setTitleFont(FontLoader.ui(java.awt.Font.BOLD, 12f));
        return BorderFactory.createCompoundBorder(
                border,
                new EmptyBorder(SECTION_INNER_PADDING, SECTION_INNER_PADDING, SECTION_INNER_PADDING, SECTION_INNER_PADDING)
        );
    }

    private Border createSubSectionBorder(String title) {
        TitledBorder border = new TitledBorder(title);
        border.setTitleColor(TEXT_MUTED);
        border.setTitleFont(FontLoader.ui(java.awt.Font.PLAIN, 11f));
        return BorderFactory.createCompoundBorder(
                border,
                new EmptyBorder(SECTION_INNER_PADDING, SECTION_INNER_PADDING, SECTION_INNER_PADDING, SECTION_INNER_PADDING)
        );
    }

    private void configureTooltips() {
        basePriceField.setToolTipText(TooltipStyler.text("Initial buy triggers when price is less than or equal to this value."));
        stopLossField.setToolTipText(TooltipStyler.text("Stop Loss activates once price reaches this level, then can trigger stop-loss on reversal."));
        lossBuyLevelsEnabled.setToolTipText(TooltipStyler.text("When disabled, Loss Buy Level 1 and Loss Buy Level 2 will not trigger."));
        loss1PriceField.setToolTipText(TooltipStyler.text("Loss Buy Level 1 triggers when price is less than or equal to this value."));
        loss2PriceField.setToolTipText(TooltipStyler.text("Loss Buy Level 2 triggers when price is less than or equal to this value."));
        stopLossEnabled.setToolTipText(TooltipStyler.text("When disabled, stop-loss checks and stop-loss sell orders are skipped."));
        pollingField.setToolTipText(TooltipStyler.text("How often the strategy evaluates prices, in seconds."));
        repeatCycleAfterProfitExitEnabled.setToolTipText(TooltipStyler.text("Optional. When enabled, a new cycle starts only after a profitable exit. Stop-loss and manual close exits do not restart the cycle."));
    }

    private void styleInputs() {
        styleInput(symbolField);
        styleInput(basePriceField);
        styleInput(baseQtyField);
        styleInput(stopLossField);
        styleInput(loss1PriceField);
        styleInput(loss1QtyField);
        styleInput(loss2PriceField);
        styleInput(loss2QtyField);
        styleInput(pollingField);
        styleInput(highSeriesArea);
        styleInput(lowSeriesArea);

        paperMode.setOpaque(false);
        stopLossEnabled.setOpaque(false);
        lossBuyLevelsEnabled.setOpaque(false);
        repeatCycleAfterProfitExitEnabled.setOpaque(false);
    }

    private void wireLossBuyLevelFields() {
        lossBuyLevelsEnabled.addActionListener(e -> updateLossBuyFieldState());
        updateLossBuyFieldState();
    }

    private void updateLossBuyFieldState() {
        boolean enabled = lossBuyLevelsEnabled.isSelected();
        loss1PriceField.setEnabled(enabled);
        loss1QtyField.setEnabled(enabled);
        loss2PriceField.setEnabled(enabled);
        loss2QtyField.setEnabled(enabled);
    }

    private void styleInput(JTextField input) {
        input.setBackground(INPUT_BG);
        input.setForeground(TEXT_PRIMARY);
        input.setCaretColor(TEXT_PRIMARY);
        input.setSelectionColor(new Color(114, 130, 176));
        input.setSelectedTextColor(TEXT_PRIMARY);
        input.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(INPUT_BORDER, 1, true),
                new EmptyBorder(4, 8, 4, 8)
        ));
    }

    private void styleInput(JTextArea input) {
        input.setBackground(INPUT_BG);
        input.setForeground(TEXT_PRIMARY);
        input.setCaretColor(TEXT_PRIMARY);
        input.setSelectionColor(new Color(114, 130, 176));
        input.setSelectedTextColor(TEXT_PRIMARY);
        input.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(INPUT_BORDER, 1, true),
                new EmptyBorder(6, 8, 6, 8)
        ));
    }

    private void styleSpinner(JSpinner spinner) {
        spinner.setFont(FontLoader.ui(java.awt.Font.PLAIN, 12f));
        if (spinner.getEditor() instanceof JSpinner.DefaultEditor editor) {
            JTextField field = editor.getTextField();
            styleInput(field);
            field.setHorizontalAlignment(JTextField.RIGHT);
        }
    }

    private void applyDialogTheme() {
        applyThemeRecursively(getContentPane());
        getContentPane().setBackground(DIALOG_BG);
    }

    private void applyThemeRecursively(Component component) {
        component.setFont(FontLoader.ui(java.awt.Font.PLAIN, 12f));
        if (component instanceof JPanel panel) {
            panel.setBackground(DIALOG_BG);
        }
        if (component instanceof JLabel label) {
            label.setForeground(TEXT_PRIMARY);
        }
        if (component instanceof JCheckBox checkBox) {
            checkBox.setBackground(DIALOG_BG);
            checkBox.setForeground(TEXT_PRIMARY);
        }
        if (component instanceof JComboBox<?> comboBox) {
            comboBox.setBackground(INPUT_BG);
            comboBox.setForeground(TEXT_PRIMARY);
        }
        if (component instanceof JButton button) {
            button.setFont(FontLoader.ui(java.awt.Font.BOLD, 12f));
        }

        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                applyThemeRecursively(child);
            }
        }
    }

    private void applyConfig(StrategyConfig config) {
        symbolField.setText(config.symbol());
        paperMode.setSelected(config.paperTrading());
        basePriceField.setText(config.baseBuyPrice().toPlainString());
        baseQtyField.setText(String.valueOf(config.baseBuyQty()));
        stopLossEnabled.setSelected(config.stopLossEnabled());
        stopLossField.setText(config.stopLoss().toPlainString());
        lossBuyLevelsEnabled.setSelected(config.lossBuyLevelsEnabled());
        loss1PriceField.setText(config.lossBuyLevel1Price().toPlainString());
        loss1QtyField.setText(String.valueOf(config.lossBuyLevel1Qty()));
        loss2PriceField.setText(config.lossBuyLevel2Price().toPlainString());
        loss2QtyField.setText(String.valueOf(config.lossBuyLevel2Qty()));
        pollingField.setText(String.valueOf(config.pollingSeconds()));
        repeatCycleAfterProfitExitEnabled.setSelected(config.repeatCycleAfterProfitExitEnabled());
        profitControlsPanel.applyConfig(config);
        updateStopLossFieldState();
        updateLossBuyFieldState();
    }

    private void applyDialogDefaults() {
        symbolField.setText(DEFAULTS.symbol());
        paperMode.setSelected(DEFAULTS.paperTrading());
        basePriceField.setText(DEFAULTS.baseBuyPrice());
        baseQtyField.setText(DEFAULTS.baseBuyQty());
        stopLossEnabled.setSelected(DEFAULTS.stopLossEnabled());
        stopLossField.setText(DEFAULTS.stopLoss());
        lossBuyLevelsEnabled.setSelected(true);
        loss1PriceField.setText(DEFAULTS.lossBuyLevel1Price());
        loss1QtyField.setText(DEFAULTS.lossBuyLevel1Qty());
        loss2PriceField.setText(DEFAULTS.lossBuyLevel2Price());
        loss2QtyField.setText(DEFAULTS.lossBuyLevel2Qty());
        pollingField.setText(DEFAULTS.pollingSeconds());
        repeatCycleAfterProfitExitEnabled.setSelected(DEFAULTS.repeatCycleAfterProfitExitEnabled());
        profitControlsPanel.applyConfig(new StrategyConfig(  // Apply defaults to profit controls panel
                DEFAULTS.symbol(),
                new BigDecimal(DEFAULTS.baseBuyPrice()),
                Integer.parseInt(DEFAULTS.baseBuyQty()),
                DEFAULTS.stopLossEnabled(),
                new BigDecimal(DEFAULTS.stopLoss()),
                DEFAULTS.sellTriggerEnabled(),
                new BigDecimal(DEFAULTS.sellTriggerPrice()),
                new BigDecimal(DEFAULTS.lossBuyLevel1Price()),
                Integer.parseInt(DEFAULTS.lossBuyLevel1Qty()),
                new BigDecimal(DEFAULTS.lossBuyLevel2Price()),
                Integer.parseInt(DEFAULTS.lossBuyLevel2Qty()),
                true,
                false,
                BigDecimal.ZERO,
                Integer.parseInt(DEFAULTS.pollingSeconds()),
                DEFAULTS.paperTrading(),
                false,
                DEFAULTS.profitHoldEnabled(),
                DEFAULTS.profitHoldType(),
                new BigDecimal(DEFAULTS.profitHoldPercent()),
                new BigDecimal(DEFAULTS.profitHoldAmount()),
                DEFAULTS.repeatCycleAfterProfitExitEnabled(),
                ProfitControlMode.NONE,
                ThresholdType.PERCENTAGE,
                BigDecimal.ZERO,
                TrailingType.PERCENTAGE,
                BigDecimal.ZERO
        ));
        updateStopLossFieldState();
        updateLossBuyFieldState();
    }

    private void onSaveForSelectedTab() {
        onSave();
    }

    private void styleTabs() {
        tabs.setOpaque(false);
        tabs.setBackground(DIALOG_BG);
        tabs.setForeground(TAB_UNSELECTED_TEXT);
        tabs.setFont(FontLoader.ui(java.awt.Font.BOLD, 12f));
        tabs.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(TAB_BORDER, 1, true),
                new EmptyBorder(2, 2, 2, 2)
        ));
        tabs.setUI(new BasicTabbedPaneUI() {
            @Override
            protected void installDefaults() {
                super.installDefaults();
                selectedTabPadInsets = new Insets(0, 0, 0, 0);
                tabInsets = new Insets(8, 14, 8, 14);
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
                g.drawLine(0, calculateTabAreaHeight(tabPlacement, runCount, maxTabHeight), tabs.getWidth(), calculateTabAreaHeight(tabPlacement, runCount, maxTabHeight));
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
                // Intentionally omitted for cleaner enterprise-style tabs.
            }
        });
    }

    private void onSave() {
        try {
            String symbol = symbolField.getText().trim().toUpperCase();
            if (symbol.isBlank()) {
                JOptionPane.showMessageDialog(this, "Symbol is required.", "Missing Symbol", JOptionPane.WARNING_MESSAGE);
                return;
            }

            BigDecimal baseBuyPrice = new BigDecimal(basePriceField.getText().trim());
            boolean sellTriggerOn = profitControlsPanel.getSellTriggerEnabled();
            BigDecimal sellTriggerPrice = profitControlsPanel.getSellTriggerPrice();
            if (sellTriggerOn && sellTriggerPrice.compareTo(baseBuyPrice) < 0) {
                JOptionPane.showMessageDialog(this,
                        "Sell trigger price must be greater than or equal to base buy price.",
                        "Invalid Sell Trigger",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            boolean lossBuysOn = lossBuyLevelsEnabled.isSelected();
            BigDecimal lossBuyLevel1Price = lossBuysOn ? new BigDecimal(loss1PriceField.getText().trim()) : BigDecimal.ZERO;
            BigDecimal lossBuyLevel2Price = lossBuysOn ? new BigDecimal(loss2PriceField.getText().trim()) : BigDecimal.ZERO;

            boolean stopLossOn = stopLossEnabled.isSelected();
            BigDecimal stopLossPrice = stopLossOn
                    ? new BigDecimal(stopLossField.getText().trim())
                    : BigDecimal.ZERO;
            if (stopLossOn && stopLossPrice.compareTo(BigDecimal.ZERO) <= 0) {
                JOptionPane.showMessageDialog(this,
                        "Stop loss price must be greater than zero when Stop Loss is enabled.",
                        "Invalid Stop Loss",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (stopLossOn && stopLossPrice.compareTo(baseBuyPrice) >= 0) {
                JOptionPane.showMessageDialog(this,
                        "Stop loss price must be less than base buy price.",
                        "Invalid Stop Loss",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (lossBuysOn && lossBuyLevel1Price.compareTo(baseBuyPrice) >= 0) {
                JOptionPane.showMessageDialog(this,
                        "Loss Buy Level 1 price must be less than base buy price.",
                        "Invalid Loss Buy Level",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (lossBuysOn && lossBuyLevel2Price.compareTo(lossBuyLevel1Price) >= 0) {
                JOptionPane.showMessageDialog(this,
                        "Loss Buy Level 2 price must be less than Loss Buy Level 1 price.",
                        "Invalid Loss Buy Level",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            int pollingSeconds = Integer.parseInt(pollingField.getText().trim());
            if (pollingSeconds <= 0) {
                JOptionPane.showMessageDialog(this,
                        "Polling interval must be greater than zero seconds.",
                        "Invalid Polling Interval",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            int lossBuyLevel1Qty = lossBuysOn ? Integer.parseInt(loss1QtyField.getText().trim()) : 0;
            int lossBuyLevel2Qty = lossBuysOn ? Integer.parseInt(loss2QtyField.getText().trim()) : 0;

            // Get profit control data from the panel
            ProfitControlMode profitControlModeFromUI = profitControlsPanel.getSelectedMode();
            if ((profitControlModeFromUI == ProfitControlMode.AUTOMATIC_STOP_SELL
                    || profitControlModeFromUI == ProfitControlMode.PROFIT_HOLD)
                    && profitControlsPanel.getThresholdValue().compareTo(BigDecimal.ZERO) <= 0) {
                JOptionPane.showMessageDialog(this,
                        "Profit activation threshold must be greater than zero.",
                        "Invalid Profit Controls",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (profitControlModeFromUI == ProfitControlMode.AUTOMATIC_STOP_SELL
                    && profitControlsPanel.getTrailingValue().compareTo(BigDecimal.ZERO) <= 0) {
                JOptionPane.showMessageDialog(this,
                        "Automatic Stop Sell trailing value must be greater than zero.",
                        "Invalid Profit Controls",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (profitControlModeFromUI == ProfitControlMode.PROFIT_HOLD) {
                ProfitHoldType profitHoldType = profitControlsPanel.getProfitHoldType();
                if (profitHoldType == ProfitHoldType.PERCENT_TRAILING
                        && profitControlsPanel.getProfitHoldPercent().compareTo(BigDecimal.ZERO) <= 0) {
                    JOptionPane.showMessageDialog(this,
                            "Profit Hold trailing percent must be greater than zero.",
                            "Invalid Profit Controls",
                            JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (profitHoldType == ProfitHoldType.FIXED_AMOUNT_TRAILING
                        && profitControlsPanel.getProfitHoldAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    JOptionPane.showMessageDialog(this,
                            "Profit Hold trailing amount must be greater than zero.",
                            "Invalid Profit Controls",
                            JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }

            result = new StrategyConfig(
                    symbol,
                    baseBuyPrice,
                    Integer.parseInt(baseQtyField.getText().trim()),
                    stopLossOn,
                    stopLossPrice,
                    profitControlsPanel.getSellTriggerEnabled(),
                    profitControlsPanel.getSellTriggerPrice(),
                    lossBuyLevel1Price,
                    lossBuyLevel1Qty,
                    lossBuyLevel2Price,
                    lossBuyLevel2Qty,
                    lossBuysOn,
                    false,
                    BigDecimal.ZERO,
                    pollingSeconds,
                    paperMode.isSelected(),
                    profitControlsPanel.getAutomaticStopSellEnabled(),
                    profitControlsPanel.getProfitHoldEnabled(),
                    profitControlsPanel.getProfitHoldType(),
                    profitControlsPanel.getProfitHoldPercent(),
                    profitControlsPanel.getProfitHoldAmount(),
                    repeatCycleAfterProfitExitEnabled.isSelected(),
                    profitControlModeFromUI,
                    profitControlsPanel.getThresholdType(),
                    profitControlsPanel.getThresholdValue(),
                    profitControlsPanel.getTrailingType(),
                    profitControlsPanel.getTrailingValue()
            );
            setVisible(false);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                    "Please enter valid numeric values for all strategy fields.",
                    "Invalid Input",
                    JOptionPane.WARNING_MESSAGE);
        }
    }


    private static DialogDefaults loadDefaults() {
        Properties properties = new Properties();
        try (InputStream input = StrategyDialog.class.getClassLoader().getResourceAsStream(DEFAULT_CONFIG_RESOURCE)) {
            if (input != null) {
                properties.load(input);
            }
        } catch (Exception ignored) {
            // Fallback defaults are applied below.
        }

        String symbol = property(properties, "symbol", "NIO").toUpperCase();
        String stopLoss = property(properties, "stopLoss",
                property(properties, "stopActivationPrice", "5.95"));
        return new DialogDefaults(
                symbol,
                parseBoolean(properties, "paperTrading", true),
                property(properties, "baseBuyPrice", "6.21"),
                property(properties, "baseBuyQty", "10"),
                parseBoolean(properties, "stopLossEnabled", true),
                stopLoss,
                parseBoolean(properties, "sellTriggerEnabled", true),
                property(properties, "sellTriggerPrice", "7.00"),
                property(properties, "lossBuyLevel1Price", "5.55"),
                property(properties, "lossBuyLevel1Qty", "5"),
                property(properties, "lossBuyLevel2Price", "4.45"),
                property(properties, "lossBuyLevel2Qty", "5"),
                String.valueOf(AppMetadata.defaultStrategyPollingSeconds()),
                parseBoolean(properties, "alpacaTrailingStopEnabled", false),
                parseBoolean(properties, "holdAtTenPercentProfit", false),
                parseProfitHoldType(properties, "profitHoldType", ProfitHoldType.PERCENT_TRAILING),
                property(properties, "profitHoldPercent", "10"),
                property(properties, "profitHoldAmount", "0.50"),
                parseBoolean(properties, "repeatCycleAfterProfitExitEnabled", false)
        );
    }

    private static String property(Properties properties, String key, String fallback) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static boolean parseBoolean(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private static ProfitHoldType parseProfitHoldType(Properties properties, String key, ProfitHoldType fallback) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return ProfitHoldType.valueOf(value.trim());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private void wireStopLossFields() {
        stopLossEnabled.addActionListener(e -> updateStopLossFieldState());
    }

    private void updateStopLossFieldState() {
        stopLossField.setEnabled(stopLossEnabled.isSelected());
    }


    private void runAutoAnalyze() {
        String symbol = aaSymbolField.getText().trim().toUpperCase();
        if (symbol.isBlank()) {
            aaStatusLabel.setForeground(new Color(180, 30, 30));
            aaStatusLabel.setText("Please enter a symbol.");
            return;
        }
        int monthsBack = (Integer) aaMonthsSpinner.getValue();
        int intervalMinutes = (Integer) aaIntervalSpinner.getValue();

        aaRunButton.setEnabled(false);
        setRecommendationState(shortTermRecommendationView, null);
        setRecommendationState(longTermRecommendationView, null);
        updateResultsLoadingState(true, "Loading historical bars and computing metrics...");
        aaStatusLabel.setForeground(TEXT_MUTED);
        aaStatusLabel.setText("Running analysis for " + symbol + "…");

        SwingWorker<AutoAnalyzeBundle, Void> worker = new SwingWorker<>() {
            @Override
            protected AutoAnalyzeBundle doInBackground() throws Exception {
                AutoAnalyzeService service = new AutoAnalyzeService(marketDataApi);
                return service.analyzeBundle(symbol, monthsBack, intervalMinutes);
            }

            @Override
            protected void done() {
                try {
                    AutoAnalyzeBundle bundle = get();
                    AutoAnalyzeResult r = bundle.result();
                    displayAutoAnalyzeResult(r);
                    setRecommendationState(shortTermRecommendationView, bundle.shortTermRecommendation());
                    setRecommendationState(longTermRecommendationView, bundle.longTermRecommendation());
                    if (resultStore != null) {
                        resultStore.save(r);
                    }
                    aaStatusLabel.setForeground(new Color(34, 139, 34));
                    aaStatusLabel.setText("Analysis complete — " + r.dailyBarsProcessed() + " daily bars processed.");
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    aaStatusLabel.setForeground(new Color(180, 30, 30));
                    aaStatusLabel.setText("<html>" + escapeHtml(cause.getMessage()) + "</html>");
                } finally {
                    updateResultsLoadingState(false, null);
                    aaRunButton.setEnabled(marketDataApi != null);
                }
            }
        };
        worker.execute();
    }

    private void displayAutoAnalyzeResult(AutoAnalyzeResult r) {
        lastAutoAnalyzeResult = r;
        aaAvgOpenLabel.setText("$" + r.averageDailyOpen().toPlainString());
        aaAvgCloseLabel.setText("$" + r.averageDailyClose().toPlainString());
        aaAvgLowLabel.setText("$" + r.averageDailyLow().toPlainString());
        aaAvgHighLabel.setText("$" + r.averageDailyHigh().toPlainString());
        aaOneWeekLowLabel.setText("$" + r.oneWeekLow().toPlainString());
        aaOneWeekHighLabel.setText("$" + r.oneWeekHigh().toPlainString());
        aaTwoWeekLowLabel.setText("$" + r.twoWeekLow().toPlainString());
        aaTwoWeekHighLabel.setText("$" + r.twoWeekHigh().toPlainString());
        aaOneMonthLowLabel.setText("$" + r.oneMonthLow().toPlainString());
        aaOneMonthHighLabel.setText("$" + r.oneMonthHigh().toPlainString());
        aaTwoMonthLowLabel.setText("$" + r.twoMonthLow().toPlainString());
        aaTwoMonthHighLabel.setText("$" + r.twoMonthHigh().toPlainString());
        aaFourMonthLowLabel.setText("$" + r.fourMonthLow().toPlainString());
        aaFourMonthHighLabel.setText("$" + r.fourMonthHigh().toPlainString());
        aaSixMonthLowLabel.setText("$" + r.sixMonthLow().toPlainString());
        aaSixMonthHighLabel.setText("$" + r.sixMonthHigh().toPlainString());
        aaEightMonthLowLabel.setText("$" + r.eightMonthLow().toPlainString());
        aaEightMonthHighLabel.setText("$" + r.eightMonthHigh().toPlainString());
        aaOneYearLowLabel.setText("$" + r.oneYearLow().toPlainString());
        aaOneYearHighLabel.setText("$" + r.oneYearHigh().toPlainString());
        BigDecimal snapshotPrice = firstPositive(r.todayStockPrice(), r.todayClose(), r.averageDailyClose());
        BigDecimal snapshotOpen = firstPositive(r.todayOpen(), snapshotPrice, r.averageDailyOpen());
        BigDecimal snapshotHigh = firstPositive(r.todayHighSoFar(), r.todayClose(), snapshotPrice, snapshotOpen);
        BigDecimal snapshotLow = firstPositive(r.todayLowSoFar(), snapshotOpen, snapshotPrice, r.averageDailyLow());
        BigDecimal snapshotClose = firstPositive(r.todayClose(), snapshotPrice);

        aaTodayStockPriceLabel.setText(formatPriceOrDash(snapshotPrice));
        aaTodayOpenLabel.setText(formatPriceOrDash(snapshotOpen));
        aaTodayHighSoFarLabel.setText(formatPriceOrDash(snapshotHigh));
        aaTodayLowSoFarLabel.setText(formatPriceOrDash(snapshotLow));
        aaTodayCloseLabel.setText(r.todayCloseAvailable() || snapshotClose.compareTo(BigDecimal.ZERO) > 0
                ? formatPriceOrDash(snapshotClose)
                : "—");
        aaThresholdLabel.setText("$" + r.thresholdNumber().toPlainString());
        aaDailyBarsLabel.setText(String.valueOf(r.dailyBarsProcessed()));
        aaIntradayBarsLabel.setText(String.valueOf(r.intradayBarsProcessed()));
        aaAnalyzedAtLabel.setText(DISPLAY_FMT.format(r.analyzedAt()));
    }

    private BigDecimal firstPositive(BigDecimal... values) {
        if (values == null) {
            return BigDecimal.ZERO;
        }
        for (BigDecimal value : values) {
            if (value != null && value.compareTo(BigDecimal.ZERO) > 0) {
                return value;
            }
        }
        return BigDecimal.ZERO;
    }

    private JPanel buildRecommendationSection(RecommendationView view, RecommendationTypeLabel typeLabel) {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);
        panel.setBorder(createSubSectionBorder(view.title));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));

        JPanel topRow = new JPanel(new BorderLayout(10, 0));
        topRow.setOpaque(false);

        JPanel confidencePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        confidencePanel.setOpaque(false);
        JLabel confidenceTitle = new JLabel("Confidence:");
        confidenceTitle.setFont(FontLoader.ui(Font.BOLD, 11f));
        view.confidenceValue.setFont(FontLoader.ui(Font.BOLD, 14f));
        view.badge.setOpaque(true);
        view.badge.setBorder(new EmptyBorder(4, 10, 4, 10));
        view.badge.setFont(FontLoader.ui(Font.BOLD, 11f));
        confidencePanel.add(confidenceTitle);
        confidencePanel.add(view.confidenceValue);
        confidencePanel.add(view.badge);
        topRow.add(confidencePanel, BorderLayout.WEST);

        DialogButtonStyles.apply(view.applyButton, "icons/apply.svg");
        view.applyButton.setText("Apply to Current Strategy");
        view.applyButton.setEnabled(false);
        view.applyButton.addActionListener(e -> applyRecommendationToCurrentStrategy(typeLabel));
        topRow.add(view.applyButton, BorderLayout.EAST);

        JPanel grid = new JPanel(new GridLayout(0, 2, FIELD_GAP, FIELD_GAP));
        grid.setOpaque(false);
        addRecommendationRow(grid, "Base Buy Price:", view.baseBuyValue);
        addRecommendationRow(grid, "Buy Level 1:", view.buy1Value);
        addRecommendationRow(grid, "Buy Level 2:", view.buy2Value);
        addRecommendationRow(grid, "Stop Loss:", view.stopLossValue);
        addRecommendationRow(grid, "Recommended Sell Price:", view.sellPriceValue);
        addRecommendationRow(grid, "Target 1:", view.target1Value);
        addRecommendationRow(grid, "Target 2:", view.target2Value);
        addRecommendationRow(grid, "Trend Status:", view.trendStatusValue);
        addRecommendationRow(grid, "Volume Status:", view.volumeStatusValue);
        addRecommendationRow(grid, "Risk/Reward Ratio:", view.riskRewardValue);
        if (typeLabel == RecommendationTypeLabel.SHORT_TERM) {
            addRecommendationRow(grid, "Effective Market Price:", view.effectiveMarketPriceValue);
            addRecommendationRow(grid, "Last Close Price:", view.lastClosePriceValue);
            addRecommendationRow(grid, "Current Price:", view.currentPriceValue);
            addRecommendationRow(grid, "Two-Week Low:", view.twoWeekLowValue);
            addRecommendationRow(grid, "Two-Week High:", view.twoWeekHighValue);
            addRecommendationRow(grid, "Expected Dip %:", view.expectedDipPctValue);
            addRecommendationRow(grid, "Behavior Adjusted Base Price:", view.behaviorAdjustedBaseValue);
            addRecommendationRow(grid, "Final Base Buy Price:", view.adjustedBaseValue);
            addRecommendationRow(grid, "Short-Term Market Mode:", view.marketModeValue);
            addRecommendationRow(grid, "Base Adjustment Reason:", view.baseAdjustmentReasonValue);
        }
        if (typeLabel == RecommendationTypeLabel.LONG_TERM) {
            addRecommendationRow(grid, "Original Calculated Base Price:", view.originalBaseValue);
            addRecommendationRow(grid, "Effective Market Price:", view.effectiveMarketPriceValue);
            addRecommendationRow(grid, "Last Close Price:", view.lastClosePriceValue);
            addRecommendationRow(grid, "Current Price:", view.currentPriceValue);
            addRecommendationRow(grid, "Expected Dip %:", view.expectedDipPctValue);
            addRecommendationRow(grid, "Behavior Adjusted Base Price:", view.behaviorAdjustedBaseValue);
            addRecommendationRow(grid, "Final Adjusted Base Buy Price:", view.adjustedBaseValue);
            addRecommendationRow(grid, "Market Mode:", view.marketModeValue);
            addRecommendationRow(grid, "Base Adjustment Reason:", view.baseAdjustmentReasonValue);
        }

        view.infoLabel.setFont(FontLoader.ui(Font.PLAIN, 10f));
        view.infoLabel.setForeground(INFO_TEXT);
        view.warningLabel.setFont(FontLoader.ui(Font.PLAIN, 10f));
        view.warningLabel.setForeground(WARNING_TEXT);
        view.warningLabel.setVerticalAlignment(SwingConstants.TOP);

        panel.add(topRow, BorderLayout.NORTH);
        panel.add(grid, BorderLayout.CENTER);
        JPanel messagesPanel = new JPanel();
        messagesPanel.setOpaque(false);
        messagesPanel.setLayout(new BoxLayout(messagesPanel, BoxLayout.Y_AXIS));
        messagesPanel.add(view.infoLabel);
        messagesPanel.add(Box.createVerticalStrut(4));
        messagesPanel.add(view.warningLabel);
        panel.add(messagesPanel, BorderLayout.SOUTH);

        setRecommendationState(view, null);
        return panel;
    }

    private void addRecommendationRow(JPanel panel, String label, JLabel valueLabel) {
        JLabel rowLabel = new JLabel(label);
        rowLabel.setFont(FontLoader.ui(Font.PLAIN, 11f));
        valueLabel.setFont(FontLoader.ui(Font.BOLD, 11f));
        panel.add(rowLabel);
        panel.add(valueLabel);
    }

    private void setRecommendationState(RecommendationView view, StrategyRecommendation recommendation) {
        if (view == shortTermRecommendationView) {
            lastShortTermRecommendation = recommendation;
        } else if (view == longTermRecommendationView) {
            lastLongTermRecommendation = recommendation;
        }

        if (recommendation == null) {
            view.baseBuyValue.setText("—");
            view.buy1Value.setText("—");
            view.buy2Value.setText("—");
            view.stopLossValue.setText("—");
            view.sellPriceValue.setText("—");
            view.target1Value.setText("—");
            view.target2Value.setText("—");
            view.trendStatusValue.setText("Run Auto Analyze");
            view.volumeStatusValue.setText("Run Auto Analyze");
            view.riskRewardValue.setText("—");
            view.originalBaseValue.setText("—");
            view.expectedDipPctValue.setText("—");
            view.behaviorAdjustedBaseValue.setText("—");
            view.adjustedBaseValue.setText("—");
            view.effectiveMarketPriceValue.setText("—");
            view.lastClosePriceValue.setText("—");
            view.currentPriceValue.setText("—");
            view.twoWeekLowValue.setText("—");
            view.twoWeekHighValue.setText("—");
            view.marketModeValue.setText("—");
            view.baseAdjustmentReasonValue.setText("—");
            view.confidenceValue.setText("—");
            view.infoLabel.setText(" ");
            view.warningLabel.setText("Run Auto Analyze to populate this recommendation.");
            styleBadge(view.badge, null);
            view.badge.setText("PENDING");
            view.applyButton.setEnabled(false);
            return;
        }

        view.baseBuyValue.setText(formatPriceOrDash(recommendation.baseBuyPrice()));
        view.buy1Value.setText(formatPriceOrDash(recommendation.buy1Price()));
        view.buy2Value.setText(formatPriceOrDash(recommendation.buy2Price()));
        view.stopLossValue.setText(formatPriceOrDash(recommendation.stopLossPrice()));
        view.sellPriceValue.setText(formatPriceOrDash(recommendation.sellPrice()));
        view.target1Value.setText(formatPriceOrDash(recommendation.target1Price()));
        view.target2Value.setText(formatPriceOrDash(recommendation.target2Price()));
        view.trendStatusValue.setText(recommendation.trendStatus());
        view.volumeStatusValue.setText(recommendation.volumeStatus());
        view.riskRewardValue.setText(recommendation.riskRewardRatio().compareTo(BigDecimal.ZERO) > 0
                ? recommendation.riskRewardRatio().toPlainString() + "x"
                : "—");
        view.originalBaseValue.setText(formatPriceOrDash(recommendation.originalCalculatedBasePrice()));
        view.expectedDipPctValue.setText(formatPercentOrDash(recommendation.expectedDipPct()));
        view.behaviorAdjustedBaseValue.setText(formatPriceOrDash(recommendation.behaviorAdjustedBasePrice()));
        view.adjustedBaseValue.setText(formatPriceOrDash(recommendation.adjustedBaseBuyPrice()));
        view.effectiveMarketPriceValue.setText(formatPriceOrDash(recommendation.effectiveMarketPrice()));
        view.lastClosePriceValue.setText(formatPriceOrDash(recommendation.lastClosePrice()));
        view.currentPriceValue.setText(formatPriceOrDash(recommendation.currentPrice()));
        view.twoWeekLowValue.setText(formatPriceOrDash(recommendation.twoWeekLow()));
        view.twoWeekHighValue.setText(formatPriceOrDash(recommendation.twoWeekHigh()));
        view.marketModeValue.setText(recommendation.recommendationType() == RecommendationType.SHORT_TERM
                ? recommendation.shortTermMarketMode().name()
                : recommendation.marketMode().name());
        view.baseAdjustmentReasonValue.setText(formatWrappedTextOrDash(recommendation.baseAdjustmentReason(), 360));
        view.confidenceValue.setText(recommendation.confidenceScore() + "/100");
        if (recommendation.recommendationType() == RecommendationType.SHORT_TERM
                && recommendation.baseBuyPrice().compareTo(recommendation.effectiveMarketPrice()) < 0) {
            view.infoLabel.setForeground(INFO_TEXT);
            view.infoLabel.setText("Short-term base buy price reduced using two-week close-to-open and intraday dip behavior.");
        } else if (recommendation.recommendationType() == RecommendationType.SHORT_TERM
                && recommendation.shortTermMarketMode() == ShortTermMarketMode.SHORT_TERM_BREAKOUT) {
            view.infoLabel.setForeground(BREAKOUT_INFO_TEXT);
            view.infoLabel.setText("Short-term breakout detected. Entry uses current market price because the stock broke above two-week resistance with volume confirmation.");
        } else if (recommendation.recommendationType() == RecommendationType.LONG_TERM
                && recommendation.adjustedBaseBuyPrice().compareTo(recommendation.effectiveMarketPrice()) < 0) {
            view.infoLabel.setForeground(INFO_TEXT);
            view.infoLabel.setText("Base buy price reduced using two-week close-to-open and intraday dip behavior.");
        } else if (recommendation.recommendationType() == RecommendationType.LONG_TERM
                && recommendation.marketMode() == MarketMode.BREAKOUT) {
            view.infoLabel.setForeground(BREAKOUT_INFO_TEXT);
            view.infoLabel.setText("Breakout mode detected. Entry uses latest market price because the stock is trading above six-month resistance with trend and volume confirmation.");
        } else {
            view.infoLabel.setText(" ");
        }
        view.warningLabel.setText(recommendation.warningMessage().isBlank()
                ? "Recommendation ready. Review values before applying."
                : "<html>" + escapeHtml(recommendation.warningMessage()) + "</html>");
        styleBadge(view.badge, recommendation.recommendationAction());
        view.badge.setText(recommendation.recommendationAction().name());
        view.applyButton.setEnabled(recommendation.isApplicable());
    }

    private void styleBadge(JLabel badge, RecommendationAction action) {
        if (action == null) {
            badge.setBackground(TAB_UNSELECTED_BG);
            badge.setForeground(TEXT_MUTED);
            return;
        }
        switch (action) {
            case BUY -> {
                badge.setBackground(BUY_BADGE_BG);
                badge.setForeground(BUY_BADGE_TEXT);
            }
            case WATCH -> {
                badge.setBackground(WATCH_BADGE_BG);
                badge.setForeground(WATCH_BADGE_TEXT);
            }
            case AVOID -> {
                badge.setBackground(AVOID_BADGE_BG);
                badge.setForeground(AVOID_BADGE_TEXT);
            }
        }
    }

    private void applyRecommendationToCurrentStrategy(RecommendationTypeLabel typeLabel) {
        StrategyRecommendation recommendation = typeLabel == RecommendationTypeLabel.SHORT_TERM
                ? lastShortTermRecommendation
                : lastLongTermRecommendation;
        if (recommendation == null || !recommendation.isApplicable()) {
            JOptionPane.showMessageDialog(this,
                    "This recommendation cannot be applied yet. Run Auto Analyze and make sure enough historical data is available.",
                    "Recommendation Not Ready",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        StrategyApplyService.AppliedStrategyValues values = strategyApplyService.applyRecommendationToCurrentStrategy(recommendation);
        basePriceField.setText(values.buyRulePrice().toPlainString());
        lossBuyLevelsEnabled.setSelected(values.enableLossBuyLevels());
        updateLossBuyFieldState();
        loss1PriceField.setText(values.lossBuy1Price().toPlainString());
        loss2PriceField.setText(values.lossBuy2Price().toPlainString());
        stopLossEnabled.setSelected(true);
        updateStopLossFieldState();
        stopLossField.setText(values.stopLossPrice().toPlainString());
        profitControlsPanel.setSellTriggerPrice(values.sellRulePrice());
        symbolField.setText(recommendation.symbol());
        tabs.setSelectedIndex(TAB_CURRENT_STRATEGY);
        JOptionPane.showMessageDialog(this,
                typeLabel == RecommendationTypeLabel.SHORT_TERM
                        ? "Short-term recommendation applied using behavior-adjusted base price. Please review and save strategy."
                        : "Long-term recommendation applied using behavior-adjusted discounted base price. Please review and save strategy.",
                "Recommendation Applied",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void loadCachedAutoAnalyzeResult(String symbol) {
        if (resultStore == null || symbol == null || symbol.isBlank()) {
            return;
        }
        resultStore.load(symbol).ifPresent(r -> {
            displayAutoAnalyzeResult(r);
            aaStatusLabel.setForeground(TEXT_MUTED);
            aaStatusLabel.setText("Showing last saved result from " + DISPLAY_FMT.format(r.analyzedAt()) + ". Run to refresh recommendations.");
            setRecommendationState(shortTermRecommendationView, null);
            setRecommendationState(longTermRecommendationView, null);
        });
    }

    private static String escapeHtml(String s) {
        if (s == null) return "Unknown error";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String formatPriceOrDash(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0
                ? "$" + value.toPlainString()
                : "—";
    }

    private String formatPercentOrDash(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0
                ? value.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%"
                : "—";
    }

    private String formatWrappedTextOrDash(String value, int widthPx) {
        if (value == null || value.isBlank()) {
            return "—";
        }
        return "<html><div style='width:" + widthPx + "px;'>" + escapeHtml(value) + "</div></html>";
    }

    private void updateResultsLoadingState(boolean loading, String message) {
        if (message != null && !message.isBlank()) {
            aaResultsLoadingLabel.setText(message);
        }
        aaResultsLoadingLabel.setVisible(loading);
        aaResultsProgressBar.setVisible(loading);
    }

    private static final class RecommendationView {
        private final String title;
        private final JLabel baseBuyValue = new JLabel("—");
        private final JLabel buy1Value = new JLabel("—");
        private final JLabel buy2Value = new JLabel("—");
        private final JLabel stopLossValue = new JLabel("—");
        private final JLabel sellPriceValue = new JLabel("—");
        private final JLabel target1Value = new JLabel("—");
        private final JLabel target2Value = new JLabel("—");
        private final JLabel trendStatusValue = new JLabel("—");
        private final JLabel volumeStatusValue = new JLabel("—");
        private final JLabel riskRewardValue = new JLabel("—");
        private final JLabel originalBaseValue = new JLabel("—");
        private final JLabel expectedDipPctValue = new JLabel("—");
        private final JLabel behaviorAdjustedBaseValue = new JLabel("—");
        private final JLabel adjustedBaseValue = new JLabel("—");
        private final JLabel effectiveMarketPriceValue = new JLabel("—");
        private final JLabel lastClosePriceValue = new JLabel("—");
        private final JLabel currentPriceValue = new JLabel("—");
        private final JLabel twoWeekLowValue = new JLabel("—");
        private final JLabel twoWeekHighValue = new JLabel("—");
        private final JLabel marketModeValue = new JLabel("—");
        private final JLabel baseAdjustmentReasonValue = new JLabel("—");
        private final JLabel confidenceValue = new JLabel("—");
        private final JLabel badge = new JLabel("PENDING");
        private final JLabel infoLabel = new JLabel(" ");
        private final JLabel warningLabel = new JLabel("Run Auto Analyze to populate this recommendation.");
        private final JButton applyButton = new JButton();

        private RecommendationView(String title) {
            this.title = title;
        }
    }

    private enum RecommendationTypeLabel {
        SHORT_TERM,
        LONG_TERM
    }

    private record DialogDefaults(
            String symbol,
            boolean paperTrading,
            String baseBuyPrice,
            String baseBuyQty,
            boolean stopLossEnabled,
            String stopLoss,
            boolean sellTriggerEnabled,
            String sellTriggerPrice,
            String lossBuyLevel1Price,
            String lossBuyLevel1Qty,
            String lossBuyLevel2Price,
            String lossBuyLevel2Qty,
            String pollingSeconds,
            boolean alpacaTrailingStopEnabled,
            boolean profitHoldEnabled,
            ProfitHoldType profitHoldType,
            String profitHoldPercent,
            String profitHoldAmount,
            boolean repeatCycleAfterProfitExitEnabled
    ) {}
}
