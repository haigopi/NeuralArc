package com.neuralarc.ui;

import com.neuralarc.model.ProfitControlMode;
import com.neuralarc.model.ProfitHoldType;
import com.neuralarc.model.StrategyConfig;
import com.neuralarc.model.ThresholdType;
import com.neuralarc.model.TrailingType;
import com.neuralarc.util.FontLoader;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.math.BigDecimal;

/**
 * Dedicated panel for Profit Controls UI configuration.
 * Manages fields for Sell Trigger, Automatic Stop Sell, and Profit Hold strategies.
 * Ensures only one automated strategy is active at a time.
 * Extracted from StrategyDialog to keep it under 1000 lines.
 */
public class ProfitControlsPanel extends JPanel {
    private static final int FIELD_GAP = 10;
    private static final int SECTION_INNER_PADDING = 10;
    private static final int HORIZONTAL_CONTENT_PADDING = 12;
    private static final Color TEXT_PRIMARY = UIManager.getColor("Label.foreground") != null
            ? UIManager.getColor("Label.foreground")
            : new Color(45, 45, 50);
    private static final Color TEXT_MUTED = UIManager.getColor("Label.disabledForeground") != null
            ? UIManager.getColor("Label.disabledForeground")
            : new Color(130, 130, 130);
    private static final Color INPUT_BG = UIManager.getColor("TextField.background") != null
            ? UIManager.getColor("TextField.background")
            : Color.WHITE;
    private static final Color INPUT_BORDER = new Color(190, 190, 200);

    private final JRadioButton noAutomationMode = new JRadioButton("Manual only", true);
    private final JRadioButton sellTriggerMode = new JRadioButton("Sell Trigger", false);
    private final JRadioButton automaticStopSellMode = new JRadioButton("Automatic Stop Sell", false);
    private final JRadioButton profitHoldMode = new JRadioButton("Profit Hold", false);
    private final ButtonGroup modeGroup = new ButtonGroup();

    // Sell Trigger fields
    private final JTextField sellTriggerPriceField = new JTextField(25);

    // Shared profit activation fields for Automatic Stop Sell and Profit Hold.
    private final JComboBox<ThresholdType> thresholdTypeBox = new JComboBox<>(ThresholdType.values());
    private final JTextField thresholdValueField = new JTextField(25);

    // Automatic Stop Sell fields
    private final JComboBox<TrailingType> trailingTypeBox = new JComboBox<>(TrailingType.values());
    private final JTextField trailingValueField = new JTextField(25);

    // Profit Hold fields
    private final JComboBox<ProfitHoldType> profitHoldTypeBox = new JComboBox<>(ProfitHoldType.values());
    private final JTextField profitHoldPercentField = new JTextField(25);
    private final JTextField profitHoldAmountField = new JTextField(25);

    private final JLabel statusLabel = new JLabel("Manual sell is always available");

    public ProfitControlsPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(createSectionBorder("Profit Controls"));
        setOpaque(false);

        initializeUI();
        styleInputs();
        wireListeners();
        thresholdTypeBox.setSelectedItem(ThresholdType.FIXED_AMOUNT);
    }

    private void initializeUI() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(0, HORIZONTAL_CONTENT_PADDING, 0, HORIZONTAL_CONTENT_PADDING));
        content.setOpaque(false);

        JPanel modePanel = createSubPanel(
                "Automated Profit Strategy",
                "Choose one automated profit-control strategy. Manual sell actions remain available in every mode."
        );
        JPanel modeFields = createModeFieldsPanel();
        addModeOption(modeFields, noAutomationMode, "No automated profit sell strategy is evaluated. You can still sell manually.");
        addModeOption(modeFields, sellTriggerMode, "Waits locally until price reaches the trigger, then submits a sell order.");
        addModeOption(modeFields, automaticStopSellMode, "After profit activation, submits broker-side Alpaca trailing stop protection.");
        addModeOption(modeFields, profitHoldMode, "After profit activation, trails locally and sells on a pullback.");
        modePanel.add(modeFields, BorderLayout.CENTER);
        content.add(modePanel);
        content.add(Box.createVerticalStrut(FIELD_GAP));

        JPanel sellTriggerPanel = createSubPanel(
                "Sell Trigger",
                "Local application-side trigger. No Alpaca sell order is created until polling sees the current price at or above this value."
        );
        addRow((JPanel) sellTriggerPanel.getClientProperty("fields"), "Trigger Price:", sellTriggerPriceField);
        content.add(sellTriggerPanel);
        content.add(Box.createVerticalStrut(FIELD_GAP));

        JPanel activationPanel = createSubPanel(
                "Profit Activation",
                "Arming threshold for Automatic Stop Sell and Profit Hold. Percentage and fixed amount are calculated from the current average entry price."
        );
        addRow((JPanel) activationPanel.getClientProperty("fields"), "Threshold Type:", thresholdTypeBox);
        addRow((JPanel) activationPanel.getClientProperty("fields"), "Threshold Value:", thresholdValueField);
        content.add(activationPanel);
        content.add(Box.createVerticalStrut(FIELD_GAP));

        JPanel autoStopPanel = createSubPanel(
                "Automatic Stop Sell",
                "Broker-side protection. Once profit activation is crossed, the app places one Alpaca trailing stop sell order and prevents duplicates."
        );
        addRow((JPanel) autoStopPanel.getClientProperty("fields"), "Broker Trailing Type:", trailingTypeBox);
        addRow((JPanel) autoStopPanel.getClientProperty("fields"), "Broker Trailing Value:", trailingValueField);
        content.add(autoStopPanel);
        content.add(Box.createVerticalStrut(FIELD_GAP));

        JPanel profitHoldPanel = createSubPanel(
                "Profit Hold",
                "Application-side trailing strategy. After profit activation, the app tracks the highest observed price and sells after the configured pullback."
        );
        addRow((JPanel) profitHoldPanel.getClientProperty("fields"), "Trailing Type:", profitHoldTypeBox);
        addRow((JPanel) profitHoldPanel.getClientProperty("fields"), "Trailing Percent:", profitHoldPercentField);
        addRow((JPanel) profitHoldPanel.getClientProperty("fields"), "Trailing Amount:", profitHoldAmountField);
        content.add(profitHoldPanel);
        content.add(Box.createVerticalStrut(FIELD_GAP));

        statusLabel.setForeground(TEXT_MUTED);
        statusLabel.setFont(FontLoader.ui(Font.PLAIN, 10f));
        statusLabel.putClientProperty("neuralarc.mutedDescription", Boolean.TRUE);
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(statusLabel);

        add(content);
    }

    private JPanel createSubPanel(String title, String description) {
        JPanel panel = new JPanel(new BorderLayout(0, FIELD_GAP));
        panel.setBorder(createSubSectionBorder(title));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));
        JLabel descriptionLabel = description(description);
        JPanel fields = createFieldsPanel();
        panel.add(fields, BorderLayout.CENTER);
        panel.add(descriptionLabel, BorderLayout.SOUTH);
        panel.putClientProperty("fields", fields);
        return panel;
    }

    private JPanel createFieldsPanel() {
        JPanel panel = new JPanel(new GridLayout(0, 2, FIELD_GAP, FIELD_GAP));
        panel.setOpaque(false);
        return panel;
    }

    private JPanel createModeFieldsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        return panel;
    }

    private void addModeOption(JPanel panel, JRadioButton radioButton, String description) {
        JPanel option = new JPanel();
        option.setLayout(new BoxLayout(option, BoxLayout.Y_AXIS));
        option.setOpaque(false);
        option.setAlignmentX(Component.LEFT_ALIGNMENT);
        radioButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel descriptionLabel = description(description);
        descriptionLabel.setBorder(new EmptyBorder(0, 22, FIELD_GAP, 0));
        descriptionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        option.add(radioButton);
        option.add(descriptionLabel);
        panel.add(option);
    }

    private JLabel description(String text) {
        JLabel label = new JLabel("<html>" + text + "</html>");
        label.putClientProperty("neuralarc.mutedDescription", Boolean.TRUE);
        label.setForeground(TEXT_MUTED);
        label.setFont(FontLoader.ui(Font.PLAIN, 11f));
        return label;
    }

    private void addRow(JPanel panel, String label, JComponent component) {
        JLabel labelComp = new JLabel(label);
        labelComp.setForeground(TEXT_PRIMARY);
        labelComp.setLabelFor(component);
        panel.add(labelComp);
        panel.add(component);
    }

    private void styleInputs() {
        JTextField[] fields = {
            sellTriggerPriceField, thresholdValueField, trailingValueField,
            profitHoldPercentField, profitHoldAmountField
        };
        for (JTextField field : fields) {
            field.setBackground(INPUT_BG);
            field.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(INPUT_BORDER, 1, true),
                    new EmptyBorder(4, 8, 4, 8)
            ));
        }

        JComboBox<?>[] boxes = { thresholdTypeBox, trailingTypeBox, profitHoldTypeBox };
        for (JComboBox<?> box : boxes) {
            box.setBackground(INPUT_BG);
            box.setForeground(TEXT_PRIMARY);
        }

        JRadioButton[] radioButtons = { noAutomationMode, sellTriggerMode, automaticStopSellMode, profitHoldMode };
        for (JRadioButton radioButton : radioButtons) {
            radioButton.setOpaque(false);
            radioButton.setForeground(TEXT_PRIMARY);
        }
    }

    private void wireListeners() {
        modeGroup.add(noAutomationMode);
        modeGroup.add(sellTriggerMode);
        modeGroup.add(automaticStopSellMode);
        modeGroup.add(profitHoldMode);
        noAutomationMode.addActionListener(e -> updateFieldStates());
        sellTriggerMode.addActionListener(e -> updateFieldStates());
        automaticStopSellMode.addActionListener(e -> updateFieldStates());
        profitHoldMode.addActionListener(e -> updateFieldStates());
        profitHoldTypeBox.addActionListener(e -> updateFieldStates());
    }

    private void updateFieldStates() {
        boolean sellTriggerOn = sellTriggerMode.isSelected();
        boolean autoStopOn = automaticStopSellMode.isSelected();
        boolean profitHoldOn = profitHoldMode.isSelected();

        sellTriggerPriceField.setEnabled(sellTriggerOn);
        boolean usesProfitActivation = autoStopOn || profitHoldOn;
        thresholdTypeBox.setEnabled(usesProfitActivation);
        thresholdValueField.setEnabled(usesProfitActivation);
        trailingTypeBox.setEnabled(autoStopOn);
        trailingValueField.setEnabled(autoStopOn);

        ProfitHoldType selectedType = (ProfitHoldType) profitHoldTypeBox.getSelectedItem();
        profitHoldTypeBox.setEnabled(profitHoldOn);
        profitHoldPercentField.setEnabled(profitHoldOn && selectedType == ProfitHoldType.PERCENT_TRAILING);
        profitHoldAmountField.setEnabled(profitHoldOn && selectedType == ProfitHoldType.FIXED_AMOUNT_TRAILING);

        // Update status label
        updateStatusLabel();
    }

    private void updateStatusLabel() {
        if (sellTriggerMode.isSelected()) {
            statusLabel.setText("Sell Trigger strategy is active");
        } else if (automaticStopSellMode.isSelected()) {
            statusLabel.setText("Automatic Stop Sell strategy is active");
        } else if (profitHoldMode.isSelected()) {
            statusLabel.setText("Profit Hold strategy is active (execution: application-side)");
        } else {
            statusLabel.setText("Manual sell is always available");
        }
    }

    public void applyConfig(StrategyConfig config) {
        ProfitControlMode mode = config.profitControlMode();
        boolean legacyMode = mode == null || mode == ProfitControlMode.NONE;
        ProfitControlMode selectedMode = ProfitControlMode.NONE;
        if (mode == ProfitControlMode.SELL_TRIGGER
                || (legacyMode && config.sellTriggerEnabled() && !config.profitHoldEnabled() && !config.alpacaTrailingStopEnabled())) {
            selectedMode = ProfitControlMode.SELL_TRIGGER;
        } else if (mode == ProfitControlMode.AUTOMATIC_STOP_SELL) {
            selectedMode = ProfitControlMode.AUTOMATIC_STOP_SELL;
        } else if (mode == ProfitControlMode.PROFIT_HOLD || (legacyMode && config.profitHoldEnabled())) {
            selectedMode = ProfitControlMode.PROFIT_HOLD;
        }
        selectMode(selectedMode);
        sellTriggerPriceField.setText(config.sellTriggerPrice().toPlainString());

        thresholdTypeBox.setSelectedItem(config.automaticStopSellThresholdType());
        thresholdValueField.setText(config.automaticStopSellThreshold().toPlainString());
        trailingTypeBox.setSelectedItem(config.automaticStopSellTrailingType());
        trailingValueField.setText(config.automaticStopSellTrailingValue().toPlainString());

        profitHoldTypeBox.setSelectedItem(config.profitHoldType());
        profitHoldPercentField.setText(config.profitHoldPercent().compareTo(BigDecimal.ZERO) > 0
                ? config.profitHoldPercent().toPlainString()
                : "10");
        profitHoldAmountField.setText(config.profitHoldAmount().compareTo(BigDecimal.ZERO) > 0
                ? config.profitHoldAmount().toPlainString()
                : "0.50");

        updateFieldStates();
    }

    private void selectMode(ProfitControlMode mode) {
        switch (mode == null ? ProfitControlMode.NONE : mode) {
            case SELL_TRIGGER -> sellTriggerMode.setSelected(true);
            case AUTOMATIC_STOP_SELL -> automaticStopSellMode.setSelected(true);
            case PROFIT_HOLD -> profitHoldMode.setSelected(true);
            default -> noAutomationMode.setSelected(true);
        }
    }

    public boolean getSellTriggerEnabled() {
        return sellTriggerMode.isSelected();
    }

    public BigDecimal getSellTriggerPrice() {
        try {
            return new BigDecimal(sellTriggerPriceField.getText().trim());
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    public boolean getAutomaticStopSellEnabled() {
        return automaticStopSellMode.isSelected();
    }

    public ThresholdType getThresholdType() {
        return (ThresholdType) thresholdTypeBox.getSelectedItem();
    }

    public BigDecimal getThresholdValue() {
        try {
            return new BigDecimal(thresholdValueField.getText().trim());
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    public TrailingType getTrailingType() {
        return (TrailingType) trailingTypeBox.getSelectedItem();
    }

    public BigDecimal getTrailingValue() {
        try {
            return new BigDecimal(trailingValueField.getText().trim());
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    public boolean getProfitHoldEnabled() {
        return profitHoldMode.isSelected();
    }

    public ProfitHoldType getProfitHoldType() {
        return (ProfitHoldType) profitHoldTypeBox.getSelectedItem();
    }

    public BigDecimal getProfitHoldPercent() {
        try {
            return new BigDecimal(profitHoldPercentField.getText().trim());
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    public BigDecimal getProfitHoldAmount() {
        try {
            return new BigDecimal(profitHoldAmountField.getText().trim());
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    public ProfitControlMode getSelectedMode() {
        if (sellTriggerMode.isSelected()) {
            return ProfitControlMode.SELL_TRIGGER;
        } else if (automaticStopSellMode.isSelected()) {
            return ProfitControlMode.AUTOMATIC_STOP_SELL;
        } else if (profitHoldMode.isSelected()) {
            return ProfitControlMode.PROFIT_HOLD;
        }
        return ProfitControlMode.NONE;
    }

    public void setSellTriggerPrice(BigDecimal price) {
        if (price != null) {
            sellTriggerPriceField.setText(price.toPlainString());
        }
    }

    private static Border createSectionBorder(String title) {
        TitledBorder border = BorderFactory.createTitledBorder(title);
        border.setTitleColor(TEXT_PRIMARY);
        border.setTitleFont(FontLoader.ui(Font.BOLD, 12f));
        return BorderFactory.createCompoundBorder(
                border,
                new EmptyBorder(SECTION_INNER_PADDING, SECTION_INNER_PADDING, SECTION_INNER_PADDING, SECTION_INNER_PADDING)
        );
    }

    private static Border createSubSectionBorder(String title) {
        TitledBorder border = BorderFactory.createTitledBorder(title);
        border.setTitleColor(TEXT_MUTED);
        border.setTitleFont(FontLoader.ui(Font.PLAIN, 11f));
        return BorderFactory.createCompoundBorder(
                border,
                new EmptyBorder(SECTION_INNER_PADDING, SECTION_INNER_PADDING, SECTION_INNER_PADDING, SECTION_INNER_PADDING)
        );
    }
}
