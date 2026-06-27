package com.neuralarc.ui;

import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.service.StrategyService;
import com.neuralarc.util.FontLoader;
import com.neuralarc.util.Monetary;
import com.neuralarc.util.ThemeColors;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.math.BigDecimal;

final class LivePromotionDialog extends JDialog {
    private static final Color DIALOG_BG = UIManager.getColor("Panel.background") != null
            ? UIManager.getColor("Panel.background")
            : Color.WHITE;
    private static final Color INPUT_BG = UIManager.getColor("TextField.background") != null
            ? UIManager.getColor("TextField.background")
            : Color.WHITE;
    private static final Color INPUT_BORDER = ThemeColors.color("NeuralArc.Input.border", new Color(208, 214, 222));
    private static final Color SECTION_BORDER = ThemeColors.color("NeuralArc.Section.border", new Color(208, 214, 222));
    private static final Color TEXT_PRIMARY = UIManager.getColor("Label.foreground") != null
            ? UIManager.getColor("Label.foreground")
            : new Color(41, 51, 66);
    private static final Color TEXT_MUTED = UIManager.getColor("Label.disabledForeground") != null
            ? UIManager.getColor("Label.disabledForeground")
            : new Color(107, 119, 133);
    private static final Color STATUS_OK = ThemeColors.color("NeuralArc.statusOk", new Color(46, 125, 50));
    private static final Color STATUS_WARN = ThemeColors.color("NeuralArc.statusWarn", new Color(180, 115, 20));
    private static final Color STATUS_ERROR = ThemeColors.color("NeuralArc.statusError", new Color(183, 28, 28));
    private static final Color INPUT_SELECTION = new Color(114, 130, 176);

    private boolean promoted;
    private JTextField confirmationField;
    private JButton promoteButton;
    private JCheckBox closePaperPositionCheckBox;

    // Optional, selectable loss buy levels.
    private JCheckBox lossBuyLevelsCheckBox;

    // Editable price/qty fields
    private JTextField baseBuyPriceField;
    private JTextField baseBuyQtyField;
    private JTextField buyLevel1PriceField;
    private JTextField buyLevel1QtyField;
    private JTextField buyLevel2PriceField;
    private JTextField buyLevel2QtyField;
    private JTextField targetSellPriceField;
    private JLabel pricingValidationLabel;

    // Strategy state needed for conditional rendering and validation
    private boolean targetSellEnabled;

    // Captured output values (initialized from paper strategy, overwritten on confirm)
    private BigDecimal selectedBaseBuyPrice = BigDecimal.ZERO;
    private int selectedBaseBuyQty = 0;
    private BigDecimal selectedBuyLevel1Price = BigDecimal.ZERO;
    private int selectedBuyLevel1Qty = 0;
    private BigDecimal selectedBuyLevel2Price = BigDecimal.ZERO;
    private int selectedBuyLevel2Qty = 0;
    private BigDecimal selectedTargetSellPrice = BigDecimal.ZERO;

    LivePromotionDialog(Frame owner, StrategyService.LivePromotionPreview preview, String realizedPnl, String unrealizedPnl) {
        super(owner, "Preview Live Promotion", true);
        DialogCloseActions.bindEscapeToClose(this);
        buildUi(preview, realizedPnl, unrealizedPnl);
    }

    LivePromotionDialog(Dialog owner, StrategyService.LivePromotionPreview preview, String realizedPnl, String unrealizedPnl) {
        super(owner, "Preview Live Promotion", true);
        DialogCloseActions.bindEscapeToClose(this);
        buildUi(preview, realizedPnl, unrealizedPnl);
    }

    boolean showDialog() {
        setVisible(true);
        return promoted;
    }

    boolean shouldClosePaperPositions() {
        return closePaperPositionCheckBox != null && closePaperPositionCheckBox.isSelected();
    }

    boolean lossBuyLevelsEnabled() {
        return lossBuyLevelsCheckBox != null && lossBuyLevelsCheckBox.isSelected();
    }

    BigDecimal baseBuyPrice() { return selectedBaseBuyPrice; }
    int baseBuyQty() { return selectedBaseBuyQty; }
    BigDecimal buyLevel1Price() { return selectedBuyLevel1Price; }
    int buyLevel1Qty() { return selectedBuyLevel1Qty; }
    BigDecimal buyLevel2Price() { return selectedBuyLevel2Price; }
    int buyLevel2Qty() { return selectedBuyLevel2Qty; }
    BigDecimal targetSellPrice() { return selectedTargetSellPrice; }

    private void buildUi(StrategyService.LivePromotionPreview preview, String realizedPnl, String unrealizedPnl) {
        Strategy strategy = preview.strategy();
        targetSellEnabled = strategy.targetSellEnabled();

        // Pre-initialize output to paper values so callers get sensible defaults even if cancelled.
        selectedBaseBuyPrice = strategy.baseBuyLimitPrice();
        selectedBaseBuyQty = strategy.baseBuyQuantity();
        selectedBuyLevel1Price = strategy.buyLimit1Price();
        selectedBuyLevel1Qty = strategy.buyLimit1Quantity();
        selectedBuyLevel2Price = strategy.buyLimit2Price();
        selectedBuyLevel2Qty = strategy.buyLimit2Quantity();
        selectedTargetSellPrice = strategy.targetSellPrice();

        setLayout(new BorderLayout(0, 12));
        getContentPane().setBackground(DIALOG_BG);
        ((JComponent) getContentPane()).setBorder(new EmptyBorder(14, 14, 14, 14));

        JLabel title = new JLabel("Preview Live Promotion");
        title.setFont(FontLoader.ui(Font.BOLD, 14f));
        title.setForeground(TEXT_PRIMARY);

        JLabel subtitle = new JLabel("<html>Review and adjust the cloned LIVE strategy before submitting the initial live order.</html>");
        subtitle.setFont(FontLoader.ui(Font.PLAIN, 10f));
        subtitle.setForeground(TEXT_MUTED);

        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.setOpaque(false);
        header.add(title, BorderLayout.NORTH);
        header.add(subtitle, BorderLayout.CENTER);
        add(header, BorderLayout.NORTH);

        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);
        content.setBorder(sectionBorder());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(0, 0, 8, 18);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // ── Read-only summary ────────────────────────────────────────────────
        addRow(content, gbc, "Symbol", strategy.symbol());
        addRow(content, gbc, "Source Mode", "Paper");
        addRow(content, gbc, "Target Mode", StrategyMode.LIVE.name());

        // ── Editable buy parameters ──────────────────────────────────────────
        baseBuyPriceField = moneyInput(strategy.baseBuyLimitPrice());
        addInputRow(content, gbc, "Base Buy Price", baseBuyPriceField);
        baseBuyQtyField = intInput(strategy.baseBuyQuantity());
        addInputRow(content, gbc, "Base Buy Qty", baseBuyQtyField);

        // Use the same enable-toggle wording as the Strategy Creation dialog ("Enable Loss Buy
        // Levels" / "Enable Stop Loss") so promotion stays consistent with strategy creation.
        lossBuyLevelsCheckBox = new JCheckBox("Enable Loss Buy Levels");
        lossBuyLevelsCheckBox.setOpaque(false);
        lossBuyLevelsCheckBox.setForeground(TEXT_PRIMARY);
        lossBuyLevelsCheckBox.setFont(FontLoader.ui(Font.BOLD, 10f));
        lossBuyLevelsCheckBox.setSelected(strategy.lossBuyLevelsEnabled());
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(2, 0, 8, 0);
        content.add(lossBuyLevelsCheckBox, gbc);
        gbc.gridy++;
        gbc.gridwidth = 1;
        gbc.insets = new Insets(0, 0, 8, 18);

        buyLevel1PriceField = moneyInput(strategy.buyLimit1Price());
        addInputRow(content, gbc, "Level 1 Price", buyLevel1PriceField);
        buyLevel1QtyField = intInput(strategy.buyLimit1Quantity());
        addInputRow(content, gbc, "Level 1 Qty", buyLevel1QtyField);
        buyLevel2PriceField = moneyInput(strategy.buyLimit2Price());
        addInputRow(content, gbc, "Level 2 Price", buyLevel2PriceField);
        buyLevel2QtyField = intInput(strategy.buyLimit2Quantity());
        addInputRow(content, gbc, "Level 2 Qty", buyLevel2QtyField);

        addRow(content, gbc, "Stop Loss", strategy.automatedStopLossEnabled() ? money(strategy.stopLossPrice()) : "Disabled");

        targetSellPriceField = moneyInput(strategy.targetSellPrice());
        addInputRow(content, gbc, "Target Sell Price", targetSellPriceField);
        addRow(content, gbc, "Target Sell", strategy.targetSellEnabled() ? "Enabled" : "Disabled");

        JLabel editHint = new JLabel("<html>Editable: base price &amp; qty, optional loss buy level prices &amp; qtys,"
                + " target sell price. All other settings are cloned from the paper strategy.</html>");
        editHint.setFont(FontLoader.ui(Font.PLAIN, 10f));
        editHint.setForeground(TEXT_MUTED);
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 22, 8, 0);
        content.add(editHint, gbc);
        gbc.gridy++;
        gbc.gridwidth = 1;
        gbc.insets = new Insets(0, 0, 8, 18);

        // ── Read-only P&L / session info ──────────────────────────────────────
        addRow(content, gbc, "Polling Interval", strategy.pollingIntervalSeconds() + " seconds");
        addRow(content, gbc, "Realized P&L", realizedPnl);
        addRow(content, gbc, "Unrealized P&L", unrealizedPnl);
        addRow(content, gbc, "Archive Source", "Paper strategy will be archived locally after successful LIVE promotion");
        addRow(content, gbc, "Pending Paper Orders", String.valueOf(preview.pendingPaperOrders()));
        addRow(content, gbc, "Live Open Orders", String.valueOf(preview.liveOpenOrders()));
        addRow(content, gbc, "Live Position Qty", preview.livePositionQuantity().toPlainString());
        addRow(content, gbc, "Market Session", preview.marketSessionOpen() ? "Open" : "Closed");

        closePaperPositionCheckBox = new JCheckBox("Close paper open orders and position after successful LIVE promotion");
        closePaperPositionCheckBox.setOpaque(false);
        closePaperPositionCheckBox.setForeground(TEXT_PRIMARY);
        closePaperPositionCheckBox.setFont(FontLoader.ui(Font.PLAIN, 10f));
        closePaperPositionCheckBox.setSelected(false);
        gbc.gridy++;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(8, 0, 4, 0);
        content.add(closePaperPositionCheckBox, gbc);

        JLabel note = new JLabel("<html>Paper orders and positions are not automatically closed by promotion."
                + " Review the paper account separately if needed.</html>");
        note.setFont(FontLoader.ui(Font.PLAIN, 10f));
        note.setForeground(STATUS_WARN);
        gbc.gridy++;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 22, 6, 0);
        content.add(note, gbc);

        pricingValidationLabel = new JLabel(" ");
        pricingValidationLabel.setFont(FontLoader.ui(Font.PLAIN, 10f));
        pricingValidationLabel.setForeground(STATUS_ERROR);
        gbc.gridy++;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 22, 6, 0);
        content.add(pricingValidationLabel, gbc);

        if (!preview.validationErrors().isEmpty()) {
            JLabel validation = new JLabel("<html>Validation issues: "
                    + escapeHtml(String.join(" ", preview.validationErrors())) + "</html>");
            validation.setFont(FontLoader.ui(Font.PLAIN, 10f));
            validation.setForeground(STATUS_ERROR);
            gbc.gridy++;
            gbc.insets = new Insets(4, 0, 0, 0);
            content.add(validation, gbc);
        }

        if (!preview.issues().isEmpty()) {
            JLabel eligibility = new JLabel("<html>" + escapeHtml(String.join(" ", preview.issues())) + "</html>");
            eligibility.setFont(FontLoader.ui(Font.PLAIN, 10f));
            eligibility.setForeground(STATUS_ERROR);
            gbc.gridy++;
            gbc.insets = new Insets(4, 0, 0, 0);
            content.add(eligibility, gbc);
        } else {
            JLabel ready = new JLabel("<html>Checklist passed. Type LIVE below to enable promotion.</html>");
            ready.setFont(FontLoader.ui(Font.PLAIN, 10f));
            ready.setForeground(STATUS_OK);
            gbc.gridy++;
            gbc.insets = new Insets(4, 0, 0, 0);
            content.add(ready, gbc);
        }

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        add(scrollPane, BorderLayout.CENTER);

        JButton cancelButton = new JButton("Cancel");
        DialogButtonStyles.apply(cancelButton, "icons/delete.svg");
        cancelButton.addActionListener(event -> dispose());

        confirmationField = new JTextField();
        confirmationField.setColumns(12);
        styleInput(confirmationField);

        javax.swing.event.DocumentListener changeListener = new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { updatePromoteButtonEnabled(preview); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { updatePromoteButtonEnabled(preview); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { updatePromoteButtonEnabled(preview); }
        };
        confirmationField.getDocument().addDocumentListener(changeListener);
        baseBuyPriceField.getDocument().addDocumentListener(changeListener);
        baseBuyQtyField.getDocument().addDocumentListener(changeListener);
        targetSellPriceField.getDocument().addDocumentListener(changeListener);
        buyLevel1PriceField.getDocument().addDocumentListener(changeListener);
        buyLevel1QtyField.getDocument().addDocumentListener(changeListener);
        buyLevel2PriceField.getDocument().addDocumentListener(changeListener);
        buyLevel2QtyField.getDocument().addDocumentListener(changeListener);
        lossBuyLevelsCheckBox.addActionListener(event -> {
            applyLossBuyLevelFieldState();
            updatePromoteButtonEnabled(preview);
        });
        applyLossBuyLevelFieldState();

        JLabel confirmationLabel = new JLabel("Type LIVE to confirm:");
        confirmationLabel.setFont(FontLoader.ui(Font.BOLD, 10f));
        confirmationLabel.setForeground(TEXT_PRIMARY);

        JPanel confirmationPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
        confirmationPanel.setOpaque(false);
        confirmationPanel.add(confirmationLabel);
        confirmationPanel.add(confirmationField);

        promoteButton = new JButton("Promote to Live");
        DialogButtonStyles.apply(promoteButton, "icons/add-stock-strategy.svg");
        promoteButton.setEnabled(false);
        promoteButton.addActionListener(event -> {
            String error = validateEditableFields();
            if (error != null) {
                JOptionPane.showMessageDialog(this, error, "Invalid Promotion Values", JOptionPane.WARNING_MESSAGE);
                return;
            }
            selectedBaseBuyPrice = Monetary.round(new BigDecimal(baseBuyPriceField.getText().trim()));
            selectedBaseBuyQty = Integer.parseInt(baseBuyQtyField.getText().trim());
            selectedTargetSellPrice = Monetary.round(new BigDecimal(targetSellPriceField.getText().trim()));
            if (lossBuyLevelsEnabled()) {
                selectedBuyLevel1Price = Monetary.round(new BigDecimal(buyLevel1PriceField.getText().trim()));
                selectedBuyLevel1Qty = Integer.parseInt(buyLevel1QtyField.getText().trim());
                selectedBuyLevel2Price = Monetary.round(new BigDecimal(buyLevel2PriceField.getText().trim()));
                selectedBuyLevel2Qty = Integer.parseInt(buyLevel2QtyField.getText().trim());
            }
            promoted = true;
            dispose();
        });

        JPanel actions = new JPanel(new BorderLayout(8, 0));
        actions.setOpaque(false);
        actions.add(confirmationPanel, BorderLayout.WEST);
        JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(cancelButton);
        buttons.add(promoteButton);
        actions.add(buttons, BorderLayout.EAST);
        add(actions, BorderLayout.SOUTH);

        setResizable(true);
        DialogSizing.packAndFit(this, 580, 620);
        setLocationRelativeTo(getOwner());
        updatePromoteButtonEnabled(preview);
    }

    private void applyLossBuyLevelFieldState() {
        boolean enabled = lossBuyLevelsEnabled();
        buyLevel1PriceField.setEnabled(enabled);
        buyLevel1QtyField.setEnabled(enabled);
        buyLevel2PriceField.setEnabled(enabled);
        buyLevel2QtyField.setEnabled(enabled);
        Color fg = enabled ? TEXT_PRIMARY : TEXT_MUTED;
        buyLevel1PriceField.setForeground(fg);
        buyLevel1QtyField.setForeground(fg);
        buyLevel2PriceField.setForeground(fg);
        buyLevel2QtyField.setForeground(fg);
    }

    private void updatePromoteButtonEnabled(StrategyService.LivePromotionPreview preview) {
        boolean confirmed = "LIVE".equalsIgnoreCase(confirmationField.getText().trim());
        String error = validateEditableFields();
        boolean fieldsValid = error == null;
        pricingValidationLabel.setText(fieldsValid ? " " : "Check: " + error);
        promoteButton.setEnabled(preview.eligible() && confirmed && fieldsValid);
    }

    private String validateEditableFields() {
        try {
            BigDecimal baseBuyPrice = new BigDecimal(baseBuyPriceField.getText().trim());
            int baseBuyQty = Integer.parseInt(baseBuyQtyField.getText().trim());
            BigDecimal targetSellPrice = new BigDecimal(targetSellPriceField.getText().trim());

            if (baseBuyPrice.compareTo(BigDecimal.ZERO) <= 0) {
                return "Base buy price must be greater than zero.";
            }
            if (baseBuyQty <= 0) {
                return "Base buy quantity must be greater than zero.";
            }

            if (lossBuyLevelsEnabled()) {
                BigDecimal level1Price = new BigDecimal(buyLevel1PriceField.getText().trim());
                int level1Qty = Integer.parseInt(buyLevel1QtyField.getText().trim());
                BigDecimal level2Price = new BigDecimal(buyLevel2PriceField.getText().trim());
                int level2Qty = Integer.parseInt(buyLevel2QtyField.getText().trim());

                if (level1Price.compareTo(BigDecimal.ZERO) <= 0) {
                    return "Level 1 price must be greater than zero.";
                }
                if (level1Price.compareTo(baseBuyPrice) >= 0) {
                    return "Level 1 price must be less than base buy price.";
                }
                if (level1Qty <= 0) {
                    return "Level 1 quantity must be greater than zero.";
                }
                if (level2Price.compareTo(BigDecimal.ZERO) <= 0) {
                    return "Level 2 price must be greater than zero.";
                }
                if (level2Price.compareTo(level1Price) >= 0) {
                    return "Level 2 price must be less than Level 1 price.";
                }
                if (level2Qty <= 0) {
                    return "Level 2 quantity must be greater than zero.";
                }
            }

            if (targetSellEnabled) {
                if (targetSellPrice.compareTo(BigDecimal.ZERO) <= 0) {
                    return "Target sell price must be greater than zero when target sell is enabled.";
                }
                if (targetSellPrice.compareTo(baseBuyPrice) < 0) {
                    return "Target sell price must be greater than or equal to base buy price.";
                }
            } else if (targetSellPrice.compareTo(BigDecimal.ZERO) < 0) {
                return "Target sell price cannot be negative.";
            }
            return null;
        } catch (RuntimeException ex) {
            return "Enter valid numeric values for all editable fields.";
        }
    }

    private void addRow(JPanel content, GridBagConstraints gbc, String labelText, String valueText) {
        JLabel label = new JLabel(labelText + ":");
        label.setFont(FontLoader.ui(Font.BOLD, 10f));
        label.setForeground(TEXT_MUTED);
        gbc.gridx = 0;
        gbc.weightx = 0.0;
        gbc.gridwidth = 1;
        content.add(label, gbc);

        JLabel value = new JLabel(valueText);
        value.setFont(FontLoader.ui(Font.PLAIN, 10f));
        value.setForeground(TEXT_PRIMARY);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 0, 8, 0);
        content.add(value, gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 8, 18);
    }

    private void addInputRow(JPanel content, GridBagConstraints gbc, String labelText, JTextField inputField) {
        JLabel label = new JLabel(labelText + ":");
        label.setFont(FontLoader.ui(Font.BOLD, 10f));
        label.setForeground(TEXT_MUTED);
        gbc.gridx = 0;
        gbc.weightx = 0.0;
        gbc.gridwidth = 1;
        content.add(label, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 0, 8, 0);
        content.add(inputField, gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 8, 18);
    }

    private JTextField moneyInput(BigDecimal value) {
        JTextField field = new JTextField(12);
        field.setText(value == null ? "0.00" : Monetary.round(value).toPlainString());
        styleInput(field);
        return field;
    }

    private JTextField intInput(int value) {
        JTextField field = new JTextField(12);
        field.setText(String.valueOf(value));
        styleInput(field);
        return field;
    }

    private void styleInput(JTextField field) {
        field.setFont(FontLoader.ui(Font.PLAIN, 10f));
        field.setBackground(INPUT_BG);
        field.setForeground(TEXT_PRIMARY);
        field.setCaretColor(TEXT_PRIMARY);
        field.setSelectionColor(INPUT_SELECTION);
        field.setSelectedTextColor(TEXT_PRIMARY);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(INPUT_BORDER, 1, true),
                new EmptyBorder(6, 8, 6, 8)
        ));
    }

    private Border sectionBorder() {
        return new CompoundBorder(
                BorderFactory.createLineBorder(SECTION_BORDER, 1, true),
                new EmptyBorder(12, 12, 12, 12)
        );
    }

    private String money(java.math.BigDecimal value) {
        return "$" + (value == null ? "0.00" : value.toPlainString());
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
