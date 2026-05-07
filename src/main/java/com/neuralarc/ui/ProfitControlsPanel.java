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

    // Sell Trigger fields
    private final JCheckBox sellTriggerEnabled = new JCheckBox("Enable Sell Trigger", false);
    private final JTextField sellTriggerPriceField = new JTextField(25);

    // Shared profit activation fields for Automatic Stop Sell and Profit Hold.
    private final JComboBox<ThresholdType> thresholdTypeBox = new JComboBox<>(ThresholdType.values());
    private final JTextField thresholdValueField = new JTextField(25);

    // Automatic Stop Sell fields
    private final JCheckBox automaticStopSellEnabled = new JCheckBox("Enable Automatic Stop Sell", false);
    private final JComboBox<TrailingType> trailingTypeBox = new JComboBox<>(TrailingType.values());
    private final JTextField trailingValueField = new JTextField(25);

    // Profit Hold fields
    private final JCheckBox profitHoldEnabled = new JCheckBox("Enable Profit Hold", false);
    private final JComboBox<ProfitHoldType> profitHoldTypeBox = new JComboBox<>(ProfitHoldType.values());
    private final JTextField profitHoldPercentField = new JTextField(25);
    private final JTextField profitHoldAmountField = new JTextField(25);

    // Status label
    private final JLabel statusLabel = new JLabel("Manual sell is always available");
    private final JLabel helpLabel = new JLabel("<html>Sell Trigger: Local application-side trigger. Places order when price reaches trigger.<br>"
            + "Profit Activation: Arming threshold for Automatic Stop Sell and Profit Hold.<br>"
            + "Automatic Stop Sell: Broker-side protection. Places Alpaca trailing stop after profit activation.<br>"
            + "Profit Hold: Application-side trailing logic after profit activation.</html>");

    public ProfitControlsPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(createSectionBorder("Profit Controls"));
        setOpaque(false);

        initializeUI();
        styleInputs();
        wireListeners();
    }

    private void initializeUI() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(0, HORIZONTAL_CONTENT_PADDING, 0, HORIZONTAL_CONTENT_PADDING));
        content.setOpaque(false);

        // Sell Trigger section
        JPanel sellTriggerPanel = createSubPanel("Sell Trigger");
        addRow(sellTriggerPanel, "Enable:", sellTriggerEnabled);
        addRow(sellTriggerPanel, "Trigger Price:", sellTriggerPriceField);
        content.add(sellTriggerPanel);
        content.add(Box.createVerticalStrut(FIELD_GAP));

        JPanel activationPanel = createSubPanel("Profit Activation");
        addRow(activationPanel, "Threshold Type:", thresholdTypeBox);
        addRow(activationPanel, "Threshold Value:", thresholdValueField);
        content.add(activationPanel);
        content.add(Box.createVerticalStrut(FIELD_GAP));

        JPanel autoStopPanel = createSubPanel("Automatic Stop Sell");
        addRow(autoStopPanel, "Enable:", automaticStopSellEnabled);
        addRow(autoStopPanel, "Broker Trailing Type:", trailingTypeBox);
        addRow(autoStopPanel, "Broker Trailing Value:", trailingValueField);
        content.add(autoStopPanel);
        content.add(Box.createVerticalStrut(FIELD_GAP));

        // Profit Hold section
        JPanel profitHoldPanel = createSubPanel("Profit Hold");
        addRow(profitHoldPanel, "Enable:", profitHoldEnabled);
        addRow(profitHoldPanel, "Trailing Type:", profitHoldTypeBox);
        addRow(profitHoldPanel, "Trailing Percent:", profitHoldPercentField);
        addRow(profitHoldPanel, "Trailing Amount:", profitHoldAmountField);
        content.add(profitHoldPanel);
        content.add(Box.createVerticalStrut(FIELD_GAP));

        helpLabel.setForeground(TEXT_MUTED);
        helpLabel.setFont(FontLoader.ui(Font.PLAIN, 10f));
        helpLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(helpLabel);
        content.add(Box.createVerticalStrut(FIELD_GAP));

        // Status label
        statusLabel.setForeground(TEXT_MUTED);
        statusLabel.setFont(FontLoader.ui(Font.PLAIN, 10f));
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(statusLabel);

        add(content);
    }

    private JPanel createSubPanel(String title) {
        JPanel panel = new JPanel(new GridLayout(0, 2, FIELD_GAP, FIELD_GAP));
        panel.setBorder(createSubSectionBorder(title));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));
        return panel;
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

        sellTriggerEnabled.setOpaque(false);
        automaticStopSellEnabled.setOpaque(false);
        profitHoldEnabled.setOpaque(false);
    }

    private void wireListeners() {
        sellTriggerEnabled.addActionListener(e -> updateFieldStates());
        automaticStopSellEnabled.addActionListener(e -> updateFieldStates());
        profitHoldEnabled.addActionListener(e -> updateFieldStates());
        profitHoldTypeBox.addActionListener(e -> updateFieldStates());
    }

    private void updateFieldStates() {
        boolean sellTriggerOn = sellTriggerEnabled.isSelected();
        boolean autoStopOn = automaticStopSellEnabled.isSelected();
        boolean profitHoldOn = profitHoldEnabled.isSelected();

        // Mutual exclusivity: only one can be active
        if (sellTriggerOn) {
            automaticStopSellEnabled.setSelected(false);
            profitHoldEnabled.setSelected(false);
            autoStopOn = false;
            profitHoldOn = false;
        } else if (autoStopOn) {
            sellTriggerEnabled.setSelected(false);
            profitHoldEnabled.setSelected(false);
            profitHoldOn = false;
        } else if (profitHoldOn) {
            sellTriggerEnabled.setSelected(false);
            automaticStopSellEnabled.setSelected(false);
            autoStopOn = false;
        }

        // Enable/disable fields based on active strategy
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
        if (sellTriggerEnabled.isSelected()) {
            statusLabel.setText("Sell Trigger strategy is active");
        } else if (automaticStopSellEnabled.isSelected()) {
            statusLabel.setText("Automatic Stop Sell strategy is active");
        } else if (profitHoldEnabled.isSelected()) {
            statusLabel.setText("Profit Hold strategy is active (execution: application-side)");
        } else {
            statusLabel.setText("Manual sell is always available");
        }
    }

    public void applyConfig(StrategyConfig config) {
        ProfitControlMode mode = config.profitControlMode();
        boolean legacyMode = mode == null || mode == ProfitControlMode.NONE;
        sellTriggerEnabled.setSelected(mode == ProfitControlMode.SELL_TRIGGER
                || (legacyMode && config.sellTriggerEnabled() && !config.profitHoldEnabled() && !config.alpacaTrailingStopEnabled()));
        sellTriggerPriceField.setText(config.sellTriggerPrice().toPlainString());

        automaticStopSellEnabled.setSelected(mode == ProfitControlMode.AUTOMATIC_STOP_SELL);
        thresholdTypeBox.setSelectedItem(config.automaticStopSellThresholdType());
        thresholdValueField.setText(config.automaticStopSellThreshold().toPlainString());
        trailingTypeBox.setSelectedItem(config.automaticStopSellTrailingType());
        trailingValueField.setText(config.automaticStopSellTrailingValue().toPlainString());

        profitHoldEnabled.setSelected(mode == ProfitControlMode.PROFIT_HOLD
                || (legacyMode && config.profitHoldEnabled()));
        profitHoldTypeBox.setSelectedItem(config.profitHoldType());
        profitHoldPercentField.setText(config.profitHoldPercent().compareTo(BigDecimal.ZERO) > 0
                ? config.profitHoldPercent().toPlainString()
                : "10");
        profitHoldAmountField.setText(config.profitHoldAmount().compareTo(BigDecimal.ZERO) > 0
                ? config.profitHoldAmount().toPlainString()
                : "0.50");

        updateFieldStates();
    }

    public StrategyConfig extractConfig(StrategyConfig baseConfig) {
        // This is a temporary placeholder. Eventually, we'll construct the full config here.
        // For now, return the base config with updated profit control fields.
        // The parent dialog will still be responsible for the full config construction.
        return baseConfig;
    }

    public boolean getSellTriggerEnabled() {
        return sellTriggerEnabled.isSelected();
    }

    public BigDecimal getSellTriggerPrice() {
        try {
            return new BigDecimal(sellTriggerPriceField.getText().trim());
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    public boolean getAutomaticStopSellEnabled() {
        return automaticStopSellEnabled.isSelected();
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
        return profitHoldEnabled.isSelected();
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
        if (sellTriggerEnabled.isSelected()) {
            return ProfitControlMode.SELL_TRIGGER;
        } else if (automaticStopSellEnabled.isSelected()) {
            return ProfitControlMode.AUTOMATIC_STOP_SELL;
        } else if (profitHoldEnabled.isSelected()) {
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
