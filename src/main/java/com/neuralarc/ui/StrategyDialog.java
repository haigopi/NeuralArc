package com.neuralarc.ui;

import com.neuralarc.model.ProfitHoldType;
import com.neuralarc.model.StrategyConfig;
import com.neuralarc.util.AppMetadata;
import com.neuralarc.util.FontLoader;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
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
    private static final int RISK_CONTROLS_HORIZONTAL_PADDING = 12;

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

    private static final String DEFAULT_CONFIG_RESOURCE = "defaults-config.properties";
    private static final DialogDefaults DEFAULTS = loadDefaults();

    private final JTextField symbolField = new JTextField(25);
    private final JCheckBox paperMode = new JCheckBox("Paper trading mode", true);
    private final JTextField basePriceField = new JTextField(25);
    private final JTextField baseQtyField = new JTextField(25);
    private final JCheckBox stopLossEnabled = new JCheckBox("Enable Stop Loss", true);
    private final JTextField stopLossField = new JTextField(25);
    private final JTextField sellTriggerField = new JTextField(25);
    private final JTextField loss1PriceField = new JTextField(25);
    private final JTextField loss1QtyField = new JTextField(25);
    private final JTextField loss2PriceField = new JTextField(25);
    private final JTextField loss2QtyField = new JTextField(25);
    private final JTextField pollingField = new JTextField(25);
    private final JCheckBox repeatCycleAfterProfitExitEnabled = new JCheckBox("Repeat cycle after profitable exit", false);
    private final JCheckBox profitHoldEnabled = new JCheckBox("Enable Profit Hold", false);
    private final JComboBox<ProfitHoldType> profitHoldTypeBox = new JComboBox<>(ProfitHoldType.values());
    private final JTextField profitHoldPercentField = new JTextField(25);
    private final JTextField profitHoldAmountField = new JTextField(25);

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
        wireStopLossFields();
        wireProfitHoldFields();
        styleInputs();

        tabs = new JTabbedPane();
        tabs.addTab("Current Strategy", buildCurrentStrategyTab());
        tabs.addTab("6-Month Median Strategy (Coming Soon)", buildMedianStrategyTab());
        tabs.setEnabledAt(1, false);
        tabs.setToolTipTextAt(1, TooltipStyler.text("Temporarily disabled"));
        add(tabs, BorderLayout.CENTER);

        JPanel actions = new JPanel(new BorderLayout());
        actions.setBorder(new EmptyBorder(0, OUTER_PADDING, OUTER_PADDING, OUTER_PADDING));
        JButton helpFaq = new JButton("Help & FAQ");
        DialogButtonStyles.apply(helpFaq, "icons/faqs.svg");
        helpFaq.addActionListener(e -> new HelpDialog(owner).setVisible(true));
        JButton save = new JButton("Save Strategy");
        DialogButtonStyles.apply(save, "icons/save.svg");
        save.addActionListener(e -> onSave());
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
        addRow(lossBuySubPanel, "Loss Buy Level 1 Price:", loss1PriceField);
        addRow(lossBuySubPanel, "Loss buy level 1 qty:", loss1QtyField);
        addRow(lossBuySubPanel, "Loss Buy Level 2 Price:", loss2PriceField);
        addRow(lossBuySubPanel, "Loss buy level 2 qty:", loss2QtyField);

        riskContent.add(stopLossSubPanel);
        riskContent.add(Box.createVerticalStrut(SECTION_GAP));
        riskContent.add(lossBuySubPanel);
        riskPanel.add(riskContent);

        JPanel profitHoldPanel = new JPanel(new BorderLayout(0, 8));
        profitHoldPanel.setBorder(createSectionBorder("Profit Hold Option"));
        prepareSection(profitHoldPanel);
        JPanel profitHoldFields = new JPanel(new GridLayout(0, 2, FIELD_GAP, FIELD_GAP));
        profitHoldFields.setOpaque(false);
        addRow(profitHoldFields, "Sell trigger price:", sellTriggerField);
        addRow(profitHoldFields, "Enable:", profitHoldEnabled);
        addRow(profitHoldFields, "Profit hold type:", profitHoldTypeBox);
        addRow(profitHoldFields, "Trailing percent:", profitHoldPercentField);
        addRow(profitHoldFields, "Trailing amount:", profitHoldAmountField);
        profitHoldPanel.add(profitHoldFields, BorderLayout.NORTH);
        JLabel help = new JLabel("<html>Use profit hold after the target sell trigger. Percent trailing follows gains by a percentage. Fixed amount trailing exits after a fixed dollar pullback from the highest observed price.</html>");
        help.setForeground(TEXT_MUTED);
        help.setFont(FontLoader.ui(java.awt.Font.PLAIN, 10f));
        profitHoldPanel.add(help, BorderLayout.CENTER);

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
        content.add(profitHoldPanel);
        content.add(Box.createVerticalStrut(SECTION_GAP));
        content.add(riskPanel);
        content.add(Box.createVerticalStrut(SECTION_GAP));
        content.add(executionPanel);
        content.add(Box.createVerticalStrut(SECTION_GAP));
        content.add(cycleBehaviorPanel);

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
        BigDecimal stopLoss = base.multiply(new BigDecimal("0.97")).setScale(2, RoundingMode.HALF_UP);
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
        sellTriggerField.setToolTipText(TooltipStyler.text("Sell trigger starts at this price."));
        loss1PriceField.setToolTipText(TooltipStyler.text("Loss Buy Level 1 triggers when price is less than or equal to this value."));
        loss2PriceField.setToolTipText(TooltipStyler.text("Loss Buy Level 2 triggers when price is less than or equal to this value."));
        stopLossEnabled.setToolTipText(TooltipStyler.text("When disabled, stop-loss checks and stop-loss sell orders are skipped."));
        pollingField.setToolTipText(TooltipStyler.text("How often the strategy evaluates prices, in seconds."));
        profitHoldTypeBox.setToolTipText(TooltipStyler.text("Choose whether profit hold exits on a percent pullback or a fixed amount pullback."));
        profitHoldPercentField.setToolTipText(TooltipStyler.text("Trailing percent below the highest observed price after the target trigger."));
        profitHoldAmountField.setToolTipText(TooltipStyler.text("Trailing dollar amount below the highest observed price after the target trigger."));
        repeatCycleAfterProfitExitEnabled.setToolTipText(TooltipStyler.text("Optional. When enabled, a new cycle starts only after a profitable exit. Stop-loss and manual close exits do not restart the cycle."));
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
        styleInput(profitHoldPercentField);
        styleInput(profitHoldAmountField);
        styleInput(highSeriesArea);
        styleInput(lowSeriesArea);

        paperMode.setOpaque(false);
        stopLossEnabled.setOpaque(false);
        repeatCycleAfterProfitExitEnabled.setOpaque(false);
        profitHoldEnabled.setOpaque(false);
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
        sellTriggerField.setText(config.sellTriggerPrice().toPlainString());
        loss1PriceField.setText(config.lossBuyLevel1Price().toPlainString());
        loss1QtyField.setText(String.valueOf(config.lossBuyLevel1Qty()));
        loss2PriceField.setText(config.lossBuyLevel2Price().toPlainString());
        loss2QtyField.setText(String.valueOf(config.lossBuyLevel2Qty()));
        pollingField.setText(String.valueOf(config.pollingSeconds()));
        profitHoldEnabled.setSelected(config.profitHoldEnabled());
        profitHoldTypeBox.setSelectedItem(config.profitHoldType());
        profitHoldPercentField.setText(config.profitHoldPercent().compareTo(BigDecimal.ZERO) > 0
                ? config.profitHoldPercent().toPlainString()
                : "10");
        profitHoldAmountField.setText(config.profitHoldAmount().compareTo(BigDecimal.ZERO) > 0
                ? config.profitHoldAmount().toPlainString()
                : "0.50");
        repeatCycleAfterProfitExitEnabled.setSelected(config.repeatCycleAfterProfitExitEnabled());
        updateStopLossFieldState();
        updateProfitHoldFieldState();
    }

    private void applyDialogDefaults() {
        symbolField.setText(DEFAULTS.symbol());
        paperMode.setSelected(DEFAULTS.paperTrading());
        basePriceField.setText(DEFAULTS.baseBuyPrice());
        baseQtyField.setText(DEFAULTS.baseBuyQty());
        stopLossEnabled.setSelected(DEFAULTS.stopLossEnabled());
        stopLossField.setText(DEFAULTS.stopLoss());
        sellTriggerField.setText(DEFAULTS.sellTriggerPrice());
        loss1PriceField.setText(DEFAULTS.lossBuyLevel1Price());
        loss1QtyField.setText(DEFAULTS.lossBuyLevel1Qty());
        loss2PriceField.setText(DEFAULTS.lossBuyLevel2Price());
        loss2QtyField.setText(DEFAULTS.lossBuyLevel2Qty());
        pollingField.setText(DEFAULTS.pollingSeconds());
        repeatCycleAfterProfitExitEnabled.setSelected(DEFAULTS.repeatCycleAfterProfitExitEnabled());
        profitHoldEnabled.setSelected(DEFAULTS.profitHoldEnabled());
        profitHoldTypeBox.setSelectedItem(DEFAULTS.profitHoldType());
        profitHoldPercentField.setText(DEFAULTS.profitHoldPercent());
        profitHoldAmountField.setText(DEFAULTS.profitHoldAmount());
        updateStopLossFieldState();
        updateProfitHoldFieldState();
    }

    private void onSave() {
        try {
            String symbol = symbolField.getText().trim().toUpperCase();
            if (symbol.isBlank()) {
                JOptionPane.showMessageDialog(this, "Symbol is required.", "Missing Symbol", JOptionPane.WARNING_MESSAGE);
                return;
            }

            BigDecimal baseBuyPrice = new BigDecimal(basePriceField.getText().trim());
            BigDecimal sellTriggerPrice = new BigDecimal(sellTriggerField.getText().trim());
            if (sellTriggerPrice.compareTo(baseBuyPrice) < 0) {
                JOptionPane.showMessageDialog(this,
                        "Sell trigger price must be greater than or equal to base buy price.",
                        "Invalid Sell Trigger",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            BigDecimal lossBuyLevel1Price = new BigDecimal(loss1PriceField.getText().trim());
            BigDecimal lossBuyLevel2Price = new BigDecimal(loss2PriceField.getText().trim());

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

            if (lossBuyLevel1Price.compareTo(baseBuyPrice) >= 0) {
                JOptionPane.showMessageDialog(this,
                        "Loss Buy Level 1 price must be less than base buy price.",
                        "Invalid Loss Buy Level",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (lossBuyLevel2Price.compareTo(lossBuyLevel1Price) >= 0) {
                JOptionPane.showMessageDialog(this,
                        "Loss Buy Level 2 price must be less than Loss Buy Level 1 price.",
                        "Invalid Loss Buy Level",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            boolean profitHold = profitHoldEnabled.isSelected();
            ProfitHoldType profitHoldType = (ProfitHoldType) profitHoldTypeBox.getSelectedItem();
            BigDecimal profitHoldPercent = profitHoldPercentField.getText().trim().isBlank()
                    ? BigDecimal.ZERO
                    : new BigDecimal(profitHoldPercentField.getText().trim());
            BigDecimal profitHoldAmount = profitHoldAmountField.getText().trim().isBlank()
                    ? BigDecimal.ZERO
                    : new BigDecimal(profitHoldAmountField.getText().trim());

            if (profitHold) {
                if (profitHoldType == ProfitHoldType.PERCENT_TRAILING && profitHoldPercent.compareTo(BigDecimal.ZERO) <= 0) {
                    JOptionPane.showMessageDialog(this,
                            "Trailing percent must be greater than zero when Percent Trailing is selected.",
                            "Invalid Profit Hold",
                            JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (profitHoldType == ProfitHoldType.FIXED_AMOUNT_TRAILING && profitHoldAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    JOptionPane.showMessageDialog(this,
                            "Trailing amount must be greater than zero when Fixed Amount Trailing is selected.",
                            "Invalid Profit Hold",
                            JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }

            int pollingSeconds = Integer.parseInt(pollingField.getText().trim());
            if (pollingSeconds <= 0) {
                JOptionPane.showMessageDialog(this,
                        "Polling interval must be greater than zero seconds.",
                        "Invalid Polling Interval",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            result = new StrategyConfig(
                    symbol,
                    baseBuyPrice,
                    Integer.parseInt(baseQtyField.getText().trim()),
                    stopLossOn,
                    stopLossPrice,
                    sellTriggerPrice,
                    lossBuyLevel1Price,
                    Integer.parseInt(loss1QtyField.getText().trim()),
                    lossBuyLevel2Price,
                    Integer.parseInt(loss2QtyField.getText().trim()),
                    false,
                    BigDecimal.ZERO,
                    pollingSeconds,
                    paperMode.isSelected(),
                    profitHold,
                    profitHoldType,
                    profitHoldPercent,
                    profitHoldAmount,
                    repeatCycleAfterProfitExitEnabled.isSelected()
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
                property(properties, "sellTriggerPrice", "7.00"),
                property(properties, "lossBuyLevel1Price", "5.55"),
                property(properties, "lossBuyLevel1Qty", "5"),
                property(properties, "lossBuyLevel2Price", "4.45"),
                property(properties, "lossBuyLevel2Qty", "5"),
                String.valueOf(AppMetadata.defaultStrategyPollingSeconds()),
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

    private void wireProfitHoldFields() {
        profitHoldEnabled.addActionListener(e -> updateProfitHoldFieldState());
        profitHoldTypeBox.addActionListener(e -> updateProfitHoldFieldState());
    }

    private void wireStopLossFields() {
        stopLossEnabled.addActionListener(e -> updateStopLossFieldState());
    }

    private void updateStopLossFieldState() {
        stopLossField.setEnabled(stopLossEnabled.isSelected());
    }

    private void updateProfitHoldFieldState() {
        boolean enabled = profitHoldEnabled.isSelected();
        ProfitHoldType selectedType = (ProfitHoldType) profitHoldTypeBox.getSelectedItem();
        profitHoldTypeBox.setEnabled(enabled);
        profitHoldPercentField.setEnabled(enabled && selectedType == ProfitHoldType.PERCENT_TRAILING);
        profitHoldAmountField.setEnabled(enabled && selectedType == ProfitHoldType.FIXED_AMOUNT_TRAILING);
    }

    private record DialogDefaults(
            String symbol,
            boolean paperTrading,
            String baseBuyPrice,
            String baseBuyQty,
            boolean stopLossEnabled,
            String stopLoss,
            String sellTriggerPrice,
            String lossBuyLevel1Price,
            String lossBuyLevel1Qty,
            String lossBuyLevel2Price,
            String lossBuyLevel2Qty,
            String pollingSeconds,
            boolean profitHoldEnabled,
            ProfitHoldType profitHoldType,
            String profitHoldPercent,
            String profitHoldAmount,
            boolean repeatCycleAfterProfitExitEnabled
    ) {}
}
