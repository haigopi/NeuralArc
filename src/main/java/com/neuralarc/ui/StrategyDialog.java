package com.neuralarc.ui;

import com.neuralarc.model.StrategyConfig;
import com.neuralarc.util.FontLoader;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class StrategyDialog extends JDialog {
    private static final int OUTER_PADDING = 16;
    private static final int SECTION_GAP = 12;
    private static final int FIELD_GAP = 10;
    private static final int SECTION_INNER_PADDING = 10;

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

    private static final String DEFAULT_CONFIG_RESOURCE = "mock-config.properties";
    private static final DialogDefaults DEFAULTS = loadDefaults();

    private final JTextField symbolField = new JTextField(25);
    private final JCheckBox paperMode = new JCheckBox("Paper trading mode", true);
    private final JTextField basePriceField = new JTextField(25);
    private final JTextField baseQtyField = new JTextField(25);
    private final JTextField stopLossField = new JTextField(25);
    private final JTextField sellTriggerField = new JTextField(25);
    private final JTextField loss1PriceField = new JTextField(25);
    private final JTextField loss1QtyField = new JTextField(25);
    private final JTextField loss2PriceField = new JTextField(25);
    private final JTextField loss2QtyField = new JTextField(25);
    private final JTextField pollingField = new JTextField(25);
    private final JCheckBox holdAtTenPercentProfit = new JCheckBox("Enable +10% Profit Hold", false);

    private final JTextArea highSeriesArea = new JTextArea(8, 22);
    private final JTextArea lowSeriesArea = new JTextArea(8, 22);
    private final JLabel medianStatus = new JLabel("Paste 6-month daily highs and lows, then apply to current strategy.");
    private JTabbedPane tabs;

    private StrategyConfig result;

    public StrategyDialog(JFrame owner, StrategyConfig initialConfig) {
        super(owner, initialConfig == null ? "Add Stock Strategy" : "Edit Stock Strategy", true);
        setLayout(new BorderLayout(SECTION_GAP, SECTION_GAP));

        applyDialogDefaults();
        configureTooltips();

        tabs = new JTabbedPane();
        tabs.addTab("Current Strategy", buildCurrentStrategyTab());
        tabs.addTab("6-Month Median Strategy (Coming Soon)", buildMedianStrategyTab());
        tabs.setEnabledAt(1, false);
        tabs.setToolTipTextAt(1, "Temporarily disabled");
        add(tabs, BorderLayout.CENTER);

        JPanel actions = new JPanel(new BorderLayout());
        actions.setBorder(new EmptyBorder(0, OUTER_PADDING, OUTER_PADDING, OUTER_PADDING));
        JButton helpFaq = new JButton("Help & FAQ");
        helpFaq.addActionListener(e -> new HelpDialog(owner).setVisible(true));
        JButton save = new JButton("Save Strategy");
        save.addActionListener(e -> onSave());
        JButton cancel = new JButton("Cancel");
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

        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
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

        JPanel riskPanel = new JPanel(new GridLayout(0, 2, FIELD_GAP, FIELD_GAP));
        riskPanel.setBorder(createSectionBorder("Risk Controls"));
        addRow(riskPanel, "Stop Loss:", stopLossField);
        addRow(riskPanel, "Sell trigger price:", sellTriggerField);
        addRow(riskPanel, "Loss Buy Level 1 Price:", loss1PriceField);
        addRow(riskPanel, "Loss buy level 1 qty:", loss1QtyField);
        addRow(riskPanel, "Loss Buy Level 2 Price:", loss2PriceField);
        addRow(riskPanel, "Loss buy level 2 qty:", loss2QtyField);

        JPanel profitHoldPanel = new JPanel(new BorderLayout(0, 8));
        profitHoldPanel.setBorder(createSectionBorder("Profit Hold Option"));
        profitHoldPanel.add(holdAtTenPercentProfit, BorderLayout.NORTH);
        JLabel help = new JLabel("<html>When enabled, hold after sell trigger if price continues at least 10% higher.</html>");
        help.setForeground(TEXT_MUTED);
        help.setFont(FontLoader.ui(java.awt.Font.PLAIN, 10f));
        profitHoldPanel.add(help, BorderLayout.CENTER);

        JPanel executionPanel = new JPanel(new GridLayout(0, 2, FIELD_GAP, FIELD_GAP));
        executionPanel.setBorder(createSectionBorder("Execution"));
        addRow(executionPanel, "Polling interval seconds:", pollingField);

        content.add(strategyPanel);
        content.add(Box.createVerticalStrut(SECTION_GAP));
        content.add(riskPanel);
        content.add(Box.createVerticalStrut(SECTION_GAP));
        content.add(profitHoldPanel);
        content.add(Box.createVerticalStrut(SECTION_GAP));
        content.add(executionPanel);

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        return scrollPane;
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
        apply.addActionListener(e -> applyMedianStrategyValues());

        medianStatus.setForeground(TEXT_MUTED);
        medianStatus.setFont(FontLoader.ui(java.awt.Font.PLAIN, 10f));

        actionsPanel.add(ruleLabel, BorderLayout.NORTH);
        actionsPanel.add(apply, BorderLayout.CENTER);
        actionsPanel.add(medianStatus, BorderLayout.SOUTH);

        content.add(inputPanel);
        content.add(Box.createVerticalStrut(SECTION_GAP));
        content.add(actionsPanel);

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        return scrollPane;
    }

    private JPanel wrapTextArea(String title, JTextArea area) {
        area.setLineWrap(true);
        area.setWrapStyleWord(true);

        JPanel wrapper = new JPanel(new BorderLayout(0, 6));
        wrapper.add(new JLabel(title), BorderLayout.NORTH);
        wrapper.add(new JScrollPane(area), BorderLayout.CENTER);
        return wrapper;
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
        BigDecimal stopLoss = base.multiply(new BigDecimal("1.03")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal sellTrigger = base.multiply(new BigDecimal("1.10")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal loss1 = base.multiply(new BigDecimal("0.92")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal loss2 = base.multiply(new BigDecimal("0.85")).setScale(2, RoundingMode.HALF_UP);

        basePriceField.setText(base.toPlainString());
        stopLossField.setText(stopLoss.toPlainString());
        sellTriggerField.setText(sellTrigger.toPlainString());
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
        panel.add(new JLabel(label));
        panel.add(component);
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

    private void configureTooltips() {
        basePriceField.setToolTipText("Initial buy triggers when price is less than or equal to this value.");
        stopLossField.setToolTipText("Stop Loss activates once price reaches this level, then can trigger stop-loss on reversal.");
        sellTriggerField.setToolTipText("Sell trigger starts at this price.");
        loss1PriceField.setToolTipText("Loss Buy Level 1 triggers when price is less than or equal to this value.");
        loss2PriceField.setToolTipText("Loss Buy Level 2 triggers when price is less than or equal to this value.");
        pollingField.setToolTipText("How often the strategy evaluates prices, in seconds.");
    }

    private void styleInputs() {
        styleInput(symbolField);
        styleInput(basePriceField);
        styleInput(baseQtyField);
        styleInput(stopLossField);
        styleInput(sellTriggerField);
        styleInput(loss1PriceField);
        styleInput(loss1QtyField);
        styleInput(loss2PriceField);
        styleInput(loss2QtyField);
        styleInput(pollingField);
        styleInput(highSeriesArea);
        styleInput(lowSeriesArea);

        paperMode.setOpaque(false);
        holdAtTenPercentProfit.setOpaque(false);
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
        stopLossField.setText(config.stopLoss().toPlainString());
        sellTriggerField.setText(config.sellTriggerPrice().toPlainString());
        loss1PriceField.setText(config.lossBuyLevel1Price().toPlainString());
        loss1QtyField.setText(String.valueOf(config.lossBuyLevel1Qty()));
        loss2PriceField.setText(config.lossBuyLevel2Price().toPlainString());
        loss2QtyField.setText(String.valueOf(config.lossBuyLevel2Qty()));
        pollingField.setText(String.valueOf(config.pollingSeconds()));
        holdAtTenPercentProfit.setSelected(config.holdAtTenPercentProfit());
    }

    private void applyDialogDefaults() {
        symbolField.setText(DEFAULTS.symbol());
        paperMode.setSelected(DEFAULTS.paperTrading());
        basePriceField.setText(DEFAULTS.baseBuyPrice());
        baseQtyField.setText(DEFAULTS.baseBuyQty());
        stopLossField.setText(DEFAULTS.stopLoss());
        sellTriggerField.setText(DEFAULTS.sellTriggerPrice());
        loss1PriceField.setText(DEFAULTS.lossBuyLevel1Price());
        loss1QtyField.setText(DEFAULTS.lossBuyLevel1Qty());
        loss2PriceField.setText(DEFAULTS.lossBuyLevel2Price());
        loss2QtyField.setText(DEFAULTS.lossBuyLevel2Qty());
        pollingField.setText(DEFAULTS.pollingSeconds());
        holdAtTenPercentProfit.setSelected(DEFAULTS.holdAtTenPercentProfit());
    }

    private void onSave() {
        try {
            String symbol = symbolField.getText().trim().toUpperCase();
            if (symbol.isBlank()) {
                JOptionPane.showMessageDialog(this, "Symbol is required.", "Missing Symbol", JOptionPane.WARNING_MESSAGE);
                return;
            }

            result = new StrategyConfig(
                    symbol,
                    new BigDecimal(basePriceField.getText().trim()),
                    Integer.parseInt(baseQtyField.getText().trim()),
                    new BigDecimal(stopLossField.getText().trim()),
                    new BigDecimal(sellTriggerField.getText().trim()),
                    new BigDecimal(loss1PriceField.getText().trim()),
                    Integer.parseInt(loss1QtyField.getText().trim()),
                    new BigDecimal(loss2PriceField.getText().trim()),
                    Integer.parseInt(loss2QtyField.getText().trim()),
                    Integer.parseInt(pollingField.getText().trim()),
                    paperMode.isSelected(),
                    holdAtTenPercentProfit.isSelected()
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
                property(properties, "stopActivationPrice", "6.42"));
        return new DialogDefaults(
                symbol,
                parseBoolean(properties, "paperTrading", true),
                property(properties, "baseBuyPrice", "6.21"),
                property(properties, "baseBuyQty", "10"),
                stopLoss,
                property(properties, "sellTriggerPrice", "7.00"),
                property(properties, "lossBuyLevel1Price", "5.55"),
                property(properties, "lossBuyLevel1Qty", "5"),
                property(properties, "lossBuyLevel2Price", "4.45"),
                property(properties, "lossBuyLevel2Qty", "5"),
                property(properties, "pollingSeconds", "2"),
                parseBoolean(properties, "holdAtTenPercentProfit", false)
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

    private record DialogDefaults(
            String symbol,
            boolean paperTrading,
            String baseBuyPrice,
            String baseBuyQty,
            String stopLoss,
            String sellTriggerPrice,
            String lossBuyLevel1Price,
            String lossBuyLevel1Qty,
            String lossBuyLevel2Price,
            String lossBuyLevel2Qty,
            String pollingSeconds,
            boolean holdAtTenPercentProfit
    ) {}
}
