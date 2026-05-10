package com.neuralarc.ui;

import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.service.StrategyService;
import com.neuralarc.util.FontLoader;

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

final class LivePromotionDialog extends JDialog {
    private boolean promoted;
    private JTextField confirmationField;
    private JButton promoteButton;
    private JCheckBox closePaperPositionCheckBox;

    LivePromotionDialog(Frame owner, StrategyService.LivePromotionPreview preview, String realizedPnl, String unrealizedPnl) {
        super(owner, "Preview Live Promotion", true);
        buildUi(preview, realizedPnl, unrealizedPnl);
    }

    LivePromotionDialog(Dialog owner, StrategyService.LivePromotionPreview preview, String realizedPnl, String unrealizedPnl) {
        super(owner, "Preview Live Promotion", true);
        buildUi(preview, realizedPnl, unrealizedPnl);
    }

    boolean showDialog() {
        setVisible(true);
        return promoted;
    }

    boolean shouldClosePaperPositions() {
        return closePaperPositionCheckBox != null && closePaperPositionCheckBox.isSelected();
    }

    private void buildUi(StrategyService.LivePromotionPreview preview, String realizedPnl, String unrealizedPnl) {
        Strategy strategy = preview.strategy();
        setLayout(new BorderLayout(0, 12));
        getContentPane().setBackground(Color.WHITE);
        ((JComponent) getContentPane()).setBorder(new EmptyBorder(14, 14, 14, 14));

        JLabel title = new JLabel("Preview Live Promotion");
        title.setFont(FontLoader.ui(Font.BOLD, 14f));
        title.setForeground(new Color(41, 51, 66));

        JLabel subtitle = new JLabel("<html>Review the cloned LIVE strategy before submitting the initial live order.</html>");
        subtitle.setFont(FontLoader.ui(Font.PLAIN, 10f));
        subtitle.setForeground(new Color(88, 96, 107));

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

        addRow(content, gbc, "Symbol", strategy.symbol());
        addRow(content, gbc, "Source Mode", "Paper");
        addRow(content, gbc, "Target Mode", StrategyMode.LIVE.name());
        addRow(content, gbc, "Base Buy", money(strategy.baseBuyLimitPrice()) + " x " + strategy.baseBuyQuantity());
        addRow(content, gbc, "Buy Level 1", money(strategy.buyLimit1Price()) + " x " + strategy.buyLimit1Quantity());
        addRow(content, gbc, "Buy Level 2", money(strategy.buyLimit2Price()) + " x " + strategy.buyLimit2Quantity());
        addRow(content, gbc, "Stop Loss", strategy.automatedStopLossEnabled() ? money(strategy.stopLossPrice()) : "Disabled");
        addRow(content, gbc, "Target Sell", strategy.targetSellEnabled() ? money(strategy.targetSellPrice()) : "Disabled");
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
        closePaperPositionCheckBox.setFont(FontLoader.ui(Font.PLAIN, 10f));
        closePaperPositionCheckBox.setSelected(false);
        gbc.gridy++;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(8, 0, 0, 0);
        content.add(closePaperPositionCheckBox, gbc);

        JLabel note = new JLabel("<html><span style='color:#8A5A00;'>Paper orders and positions are not automatically closed by promotion. Review the paper account separately if needed.</span></html>");
        note.setFont(FontLoader.ui(Font.PLAIN, 10f));
        note.setBorder(new EmptyBorder(10, 0, 0, 0));
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(6, 0, 0, 0);
        content.add(note, gbc);

        if (!preview.validationErrors().isEmpty()) {
            JLabel validation = new JLabel("<html><span style='color:#B71C1C;'>Validation issues: "
                    + escapeHtml(String.join(" ", preview.validationErrors())) + "</span></html>");
            validation.setFont(FontLoader.ui(Font.PLAIN, 10f));
            validation.setBorder(new EmptyBorder(8, 0, 0, 0));
            gbc.gridy++;
            content.add(validation, gbc);
        }

        if (!preview.issues().isEmpty()) {
            JLabel eligibility = new JLabel("<html><span style='color:#B71C1C;'>"
                    + escapeHtml(String.join(" ", preview.issues())) + "</span></html>");
            eligibility.setFont(FontLoader.ui(Font.PLAIN, 10f));
            eligibility.setBorder(new EmptyBorder(8, 0, 0, 0));
            gbc.gridy++;
            content.add(eligibility, gbc);
        } else {
            JLabel ready = new JLabel("<html><span style='color:#2E7D32;'>Checklist passed. Type LIVE below to enable promotion.</span></html>");
            ready.setFont(FontLoader.ui(Font.PLAIN, 10f));
            ready.setBorder(new EmptyBorder(8, 0, 0, 0));
            gbc.gridy++;
            content.add(ready, gbc);
        }

        add(content, BorderLayout.CENTER);

        JButton cancelButton = new JButton("Cancel");
        DialogButtonStyles.apply(cancelButton, "icons/delete.svg");
        cancelButton.addActionListener(event -> dispose());

        confirmationField = new JTextField();
        confirmationField.setFont(FontLoader.ui(Font.PLAIN, 10f));
        confirmationField.setColumns(12);
        confirmationField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(208, 214, 222), 1, true),
                new EmptyBorder(6, 8, 6, 8)
        ));
        confirmationField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { updatePromoteButtonEnabled(preview); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { updatePromoteButtonEnabled(preview); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { updatePromoteButtonEnabled(preview); }
        });

        JLabel confirmationLabel = new JLabel("Type LIVE to confirm:");
        confirmationLabel.setFont(FontLoader.ui(Font.BOLD, 10f));
        confirmationLabel.setForeground(new Color(60, 68, 79));

        JPanel confirmationPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
        confirmationPanel.setOpaque(false);
        confirmationPanel.add(confirmationLabel);
        confirmationPanel.add(confirmationField);

        promoteButton = new JButton("Promote to Live");
        DialogButtonStyles.apply(promoteButton, "icons/add-stock-strategy.svg");
        promoteButton.setEnabled(false);
        promoteButton.addActionListener(event -> {
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
        DialogSizing.packAndFit(this, 520, 380);
        setLocationRelativeTo(getOwner());
        updatePromoteButtonEnabled(preview);
    }

    private void updatePromoteButtonEnabled(StrategyService.LivePromotionPreview preview) {
        boolean confirmed = "LIVE".equalsIgnoreCase(confirmationField.getText().trim());
        promoteButton.setEnabled(preview.eligible() && confirmed);
    }

    private void addRow(JPanel content, GridBagConstraints gbc, String labelText, String valueText) {
        JLabel label = new JLabel(labelText + ":");
        label.setFont(FontLoader.ui(Font.BOLD, 10f));
        label.setForeground(new Color(60, 68, 79));
        gbc.gridx = 0;
        gbc.weightx = 0.0;
        gbc.gridwidth = 1;
        content.add(label, gbc);

        JLabel value = new JLabel(valueText);
        value.setFont(FontLoader.ui(Font.PLAIN, 10f));
        value.setForeground(new Color(41, 51, 66));
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 0, 8, 0);
        content.add(value, gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 8, 18);
    }

    private Border sectionBorder() {
        return new CompoundBorder(
                BorderFactory.createLineBorder(new Color(208, 214, 222), 1, true),
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
