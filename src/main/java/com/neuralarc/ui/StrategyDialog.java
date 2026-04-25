package com.neuralarc.ui;

import com.neuralarc.model.StrategyConfig;
import com.neuralarc.util.FontLoader;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Properties;

public class StrategyDialog extends JDialog {
    private static final int OUTER_PADDING = 18;
    private static final int SECTION_GAP = 12;
    private static final int FORM_ROW_GAP = 12;
    private static final int FORM_COLUMN_GAP = 14;
    private static final int FIELD_HEIGHT = 34;
    private static final String GRID_ROW_KEY = "strategyDialog.gridRow";

    private static final Color DIALOG_BG = new Color(244, 246, 251);
    private static final Color CARD_BG = Color.WHITE;
    private static final Color CARD_BORDER = new Color(219, 224, 235);
    private static final Color TITLE_COLOR = new Color(36, 44, 66);
    private static final Color SUBTITLE_COLOR = new Color(109, 117, 138);
    private static final Color HELP_TEXT_COLOR = new Color(110, 110, 130);
    private static final Color INPUT_BG = new Color(251, 252, 255);
    private static final Color INPUT_BORDER = new Color(196, 201, 214);
    private static final Color PRIMARY_BUTTON_BG = new Color(76, 99, 210);
    private static final Color PRIMARY_BUTTON_BORDER = new Color(56, 77, 175);
    private static final Color SECONDARY_BUTTON_BG = new Color(236, 239, 246);
    private static final Color SECONDARY_BUTTON_BORDER = new Color(199, 205, 221);
    private static final String DEFAULT_CONFIG_RESOURCE = "mock-config.properties";
    private static final DialogDefaults DEFAULTS = loadDefaults();

    private final JTextField symbolField = new JTextField(12);
    private final JCheckBox paperMode = new JCheckBox("Paper trading mode", true);
    private final JTextField basePriceField = new JTextField(12);
    private final JTextField baseQtyField = new JTextField(12);
    private final JTextField stopLossField = new JTextField(12);
    private final JTextField sellTriggerField = new JTextField(12);
    private final JTextField loss1PriceField = new JTextField(12);
    private final JTextField loss1QtyField = new JTextField(12);
    private final JTextField loss2PriceField = new JTextField(12);
    private final JTextField loss2QtyField = new JTextField(12);
    private final JTextField pollingField = new JTextField(12);
    private final JCheckBox holdAtTenPercentProfit = new JCheckBox(
            "<html>Hold when price reaches +10% above Sell Trigger</html>", false);

    private StrategyConfig result;

    public StrategyDialog(JFrame owner, StrategyConfig initialConfig) {
        super(owner, initialConfig == null ? "Add Stock Strategy" : "Edit Stock Strategy", true);
        setLayout(new BorderLayout());

        configureTooltips();
        styleInputs();
        applyDialogDefaults();

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(DIALOG_BG);
        content.setBorder(new EmptyBorder(OUTER_PADDING, OUTER_PADDING, OUTER_PADDING, OUTER_PADDING));

        content.add(Box.createVerticalStrut(SECTION_GAP));
        content.add(buildStrategyCard());
        content.add(Box.createVerticalStrut(SECTION_GAP));
        content.add(buildRiskCard());
        content.add(Box.createVerticalStrut(SECTION_GAP));
        content.add(buildProfitHoldCard());
        content.add(Box.createVerticalStrut(SECTION_GAP));
        content.add(buildExecutionCard());

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getViewport().setBackground(DIALOG_BG);
        scrollPane.setPreferredSize(new Dimension(660, 700));
        add(scrollPane, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setBackground(DIALOG_BG);
        actions.setBorder(new EmptyBorder(8, OUTER_PADDING, OUTER_PADDING, OUTER_PADDING));
        JButton save = new JButton("Save Strategy");
        JButton cancel = new JButton("Cancel");
        stylePrimaryButton(save);
        styleSecondaryButton(cancel);
        save.addActionListener(e -> onSave());
        cancel.addActionListener(e -> {
            result = null;
            setVisible(false);
        });
        actions.add(save);
        actions.add(cancel);
        add(actions, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(save);

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

    private JPanel buildStrategyCard() {
        JPanel card = createCard("Strategy Setup", "Choose the symbol, trading mode, and first-entry rule.");
        JPanel form = createFormPanel();
        addRow(form, "Symbol", "Ticker symbol used by this strategy.", symbolField);
        addRow(form, "Trading mode", "Paper trading keeps strategy execution safe during validation.", paperMode);
        addRow(form, "Base buy price", "Triggers when price is less than or equal to this value.", basePriceField);
        addRow(form, "Base buy quantity", "Number of shares to purchase on the first entry.", baseQtyField);
        card.add(form, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildRiskCard() {
        JPanel card = createCard("Risk Controls", "Define exits and pullback-based additional buys.");
        JPanel form = createFormPanel();
        addRow(form, "Stop Loss", "Activates once price reaches this level and can exit on reversal.", stopLossField);
        addRow(form, "Sell trigger price", "Profit-taking starts when price reaches this threshold.", sellTriggerField);
        addRow(form, "Loss Buy Level 1 Price", "Triggers when price is less than or equal to this value.", loss1PriceField);
        addRow(form, "Loss buy level 1 qty", "Additional shares to buy at loss level one.", loss1QtyField);
        addRow(form, "Loss Buy Level 2 Price", "Triggers when price is less than or equal to this value.", loss2PriceField);
        addRow(form, "Loss buy level 2 qty", "Additional shares to buy at loss level two.", loss2QtyField);
        card.add(form, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildProfitHoldCard() {
        JPanel card = createCard("Profit Hold Option", "Optional momentum filter for stronger upside moves.");
        JPanel form = createFormPanel();
        addRow(form, "Enable +10% Profit Hold", "", holdAtTenPercentProfit);
        card.add(form, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildExecutionCard() {
        JPanel card = createCard("Execution", "Price changes defines the frequency of strategy evaluation");
        JPanel form = createFormPanel();
        addRow(form, "Polling interval seconds", "Defines teh frequency of price changes.", pollingField);
        card.add(form, BorderLayout.CENTER);
        return card;
    }

    private JPanel createCard(String title, String subtitle) {
        JPanel card = new JPanel(new BorderLayout(0, 14));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CARD_BORDER, 1, true),
                new EmptyBorder(16, 18, 16, 18)
        ));

        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.setOpaque(false);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(FontLoader.ui(java.awt.Font.BOLD, 14f));
        titleLabel.setForeground(TITLE_COLOR);

        JLabel subtitleLabel = new JLabel(wrapText(subtitle, 70, 10, SUBTITLE_COLOR));
        subtitleLabel.setFont(FontLoader.ui(java.awt.Font.PLAIN, 10f));
        subtitleLabel.setForeground(SUBTITLE_COLOR);

        header.add(titleLabel, BorderLayout.NORTH);
        header.add(subtitleLabel, BorderLayout.CENTER);
        card.add(header, BorderLayout.NORTH);
        return card;
    }

    private JPanel createFormPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        panel.putClientProperty(GRID_ROW_KEY, 0);
        return panel;
    }

    private void addRow(JPanel panel, String label, String description, JComponent component) {
        int row = nextRow(panel);

        JPanel labelPanel = new JPanel();
        labelPanel.setOpaque(false);
        labelPanel.setLayout(new BoxLayout(labelPanel, BoxLayout.Y_AXIS));

        JLabel rowLabel = new JLabel(wrapText(label, 28, 11, TITLE_COLOR));
        rowLabel.setFont(FontLoader.ui(java.awt.Font.BOLD, 11f));
        rowLabel.setForeground(TITLE_COLOR);
        labelPanel.add(rowLabel);

        if (description != null && !description.isBlank()) {
            JLabel descriptionLabel = new JLabel(wrapText(description, 38, 9, SUBTITLE_COLOR));
            descriptionLabel.setFont(FontLoader.ui(java.awt.Font.PLAIN, 9f));
            descriptionLabel.setForeground(SUBTITLE_COLOR);
            descriptionLabel.setBorder(new EmptyBorder(3, 0, 0, 0));
            labelPanel.add(descriptionLabel);
        }

        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.weightx = 0.43;
        labelConstraints.fill = GridBagConstraints.HORIZONTAL;
        labelConstraints.anchor = GridBagConstraints.NORTHWEST;
        labelConstraints.insets = new Insets(0, 0, FORM_ROW_GAP, FORM_COLUMN_GAP);
        panel.add(labelPanel, labelConstraints);

        GridBagConstraints componentConstraints = new GridBagConstraints();
        componentConstraints.gridx = 1;
        componentConstraints.gridy = row;
        componentConstraints.weightx = 0.57;
        componentConstraints.fill = GridBagConstraints.HORIZONTAL;
        componentConstraints.anchor = GridBagConstraints.NORTHWEST;
        componentConstraints.insets = new Insets(0, 0, FORM_ROW_GAP, 0);
        panel.add(component, componentConstraints);
    }

    private void addInlineHelp(JPanel panel, String text) {
        int row = nextRow(panel);
        JLabel helpLabel = new JLabel(wrapText(text, 72, 9, HELP_TEXT_COLOR));
        helpLabel.setFont(FontLoader.ui(java.awt.Font.PLAIN, 9f));
        helpLabel.setForeground(HELP_TEXT_COLOR);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.gridwidth = 2;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.insets = new Insets(-4, 0, 0, 0);
        panel.add(helpLabel, constraints);
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
        styleTextField(symbolField);
        styleTextField(basePriceField);
        styleTextField(baseQtyField);
        styleTextField(stopLossField);
        styleTextField(sellTriggerField);
        styleTextField(loss1PriceField);
        styleTextField(loss1QtyField);
        styleTextField(loss2PriceField);
        styleTextField(loss2QtyField);
        styleTextField(pollingField);

        paperMode.setOpaque(false);
        paperMode.setForeground(TITLE_COLOR);
        paperMode.setFont(FontLoader.ui(java.awt.Font.BOLD, 11f));

        holdAtTenPercentProfit.setOpaque(false);
        holdAtTenPercentProfit.setForeground(TITLE_COLOR);
        holdAtTenPercentProfit.setFont(FontLoader.ui(java.awt.Font.BOLD, 11f));
    }

    private void styleTextField(JTextField field) {
        field.setFont(FontLoader.ui(java.awt.Font.PLAIN, 11f));
        field.setBackground(INPUT_BG);
        field.setForeground(TITLE_COLOR);
        field.setCaretColor(TITLE_COLOR);
        field.setPreferredSize(new Dimension(field.getPreferredSize().width, FIELD_HEIGHT));
        field.setMinimumSize(new Dimension(140, FIELD_HEIGHT));
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, FIELD_HEIGHT));
        Border border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(INPUT_BORDER, 1, true),
                new EmptyBorder(6, 10, 6, 10)
        );
        field.setBorder(border);
    }

    private void stylePrimaryButton(JButton button) {
        button.setFont(FontLoader.ui(java.awt.Font.BOLD, 12f));
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setForeground(Color.WHITE);
        button.setBackground(PRIMARY_BUTTON_BG);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(PRIMARY_BUTTON_BORDER, 1, true),
                new EmptyBorder(6, 14, 6, 14)
        ));
    }

    private void styleSecondaryButton(JButton button) {
        button.setFont(FontLoader.ui(java.awt.Font.BOLD, 12f));
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setForeground(TITLE_COLOR);
        button.setBackground(SECONDARY_BUTTON_BG);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(SECONDARY_BUTTON_BORDER, 1, true),
                new EmptyBorder(6, 14, 6, 14)
        ));
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

    private int nextRow(JPanel panel) {
        Object rowValue = panel.getClientProperty(GRID_ROW_KEY);
        int row = rowValue instanceof Integer ? (Integer) rowValue : 0;
        panel.putClientProperty(GRID_ROW_KEY, row + 1);
        return row;
    }

    private String wrapText(String text, int maxCharactersPerLine, int fontSize, Color color) {
        String normalized = text.replace("<br>", "\n");
        StringBuilder html = new StringBuilder("<html><span style='font-size:")
                .append(fontSize)
                .append("px;color:")
                .append(toHex(color))
                .append(";'>");

        boolean firstParagraph = true;
        for (String paragraph : normalized.split("\\n")) {
            if (!firstParagraph) {
                html.append("<br>");
            }
            firstParagraph = false;

            int lineLength = 0;
            for (String word : paragraph.split(" ")) {
                if (word.isBlank()) {
                    continue;
                }
                if (lineLength > 0 && lineLength + word.length() + 1 > maxCharactersPerLine) {
                    html.append("<br>");
                    lineLength = 0;
                } else if (lineLength > 0) {
                    html.append(' ');
                    lineLength++;
                }
                html.append(word);
                lineLength += word.length();
            }
        }

        html.append("</span></html>");
        return html.toString();
    }

    private String toHex(Color color) {
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
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
