package com.neuralarc.ui;

import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyMode;
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

    LivePromotionDialog(Frame owner, Strategy strategy, boolean promotionAllowed, String eligibilityMessage) {
        super(owner, "Preview Live Promotion", true);
        buildUi(strategy, promotionAllowed, eligibilityMessage);
    }

    LivePromotionDialog(Dialog owner, Strategy strategy, boolean promotionAllowed, String eligibilityMessage) {
        super(owner, "Preview Live Promotion", true);
        buildUi(strategy, promotionAllowed, eligibilityMessage);
    }

    boolean showDialog() {
        setVisible(true);
        return promoted;
    }

    private void buildUi(Strategy strategy, boolean promotionAllowed, String eligibilityMessage) {
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
        addRow(content, gbc, "Archive Source", "Paper strategy will be archived locally after successful LIVE promotion");

        JLabel note = new JLabel("<html><span style='color:#8A5A00;'>Paper orders and positions are not automatically closed by promotion. Review the paper account separately if needed.</span></html>");
        note.setFont(FontLoader.ui(Font.PLAIN, 10f));
        note.setBorder(new EmptyBorder(10, 0, 0, 0));
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(6, 0, 0, 0);
        content.add(note, gbc);

        if (eligibilityMessage != null && !eligibilityMessage.isBlank()) {
            JLabel eligibility = new JLabel("<html>" + escapeHtml(eligibilityMessage) + "</html>");
            eligibility.setFont(FontLoader.ui(Font.PLAIN, 10f));
            eligibility.setForeground(promotionAllowed ? new Color(46, 125, 50) : new Color(183, 28, 28));
            eligibility.setBorder(new EmptyBorder(8, 0, 0, 0));
            gbc.gridy++;
            content.add(eligibility, gbc);
        }

        add(content, BorderLayout.CENTER);

        JButton cancelButton = new JButton("Cancel");
        DialogButtonStyles.apply(cancelButton, "icons/delete.svg");
        cancelButton.addActionListener(_ -> dispose());

        JButton promoteButton = new JButton("Promote to Live");
        DialogButtonStyles.apply(promoteButton, "icons/add-stock-strategy.svg");
        promoteButton.setEnabled(promotionAllowed);
        promoteButton.addActionListener(_ -> {
            promoted = true;
            dispose();
        });

        JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(cancelButton);
        actions.add(promoteButton);
        add(actions, BorderLayout.SOUTH);

        setResizable(true);
        setSize(560, 430);
        setMinimumSize(new java.awt.Dimension(520, 380));
        setLocationRelativeTo(getOwner());
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
