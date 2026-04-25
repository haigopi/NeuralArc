package com.neuralarc.ui;

import com.neuralarc.model.StrategyConfig;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.math.BigDecimal;

public class StrategyDialog extends JDialog {
    private final JTextField symbolField = new JTextField("NEO", 12);
    private final JCheckBox paperMode = new JCheckBox("Paper trading mode", true);
    private final JTextField basePriceField = new JTextField("8.00", 12);
    private final JTextField baseQtyField = new JTextField("10", 12);
    private final JTextField stopActivationField = new JTextField("9.00", 12);
    private final JTextField sellTriggerField = new JTextField("10.00", 12);
    private final JTextField loss1PriceField = new JTextField("7.00", 12);
    private final JTextField loss1QtyField = new JTextField("5", 12);
    private final JTextField loss2PriceField = new JTextField("6.00", 12);
    private final JTextField loss2QtyField = new JTextField("5", 12);
    private final JTextField pollingField = new JTextField("2", 12);

    private StrategyConfig result;

    public StrategyDialog(JFrame owner, StrategyConfig initialConfig) {
        super(owner, initialConfig == null ? "Add Stock Strategy" : "Edit Stock Strategy", true);
        setLayout(new BorderLayout(12, 12));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(14, 14, 0, 14));

        JPanel strategyPanel = section("Strategy Parameters");
        addRow(strategyPanel, "Symbol", symbolField);
        addRow(strategyPanel, "Trading mode", paperMode);
        addRow(strategyPanel, "Base buy price (less than or equals to)", basePriceField);
        addRow(strategyPanel, "Base buy quantity", baseQtyField);

        JPanel riskPanel = section("Risk Controls");
        addRow(riskPanel, "Stop Loss", stopActivationField);
        addRow(riskPanel, "Sell trigger price", sellTriggerField);
        addRow(riskPanel, "Loss Buy Level 1 Price (less than or equals to)", loss1PriceField);
        addRow(riskPanel, "Loss buy level 1 qty", loss1QtyField);
        addRow(riskPanel, "Loss Buy Level 2 Price (less than or equals to)", loss2PriceField);
        addRow(riskPanel, "Loss buy level 2 qty", loss2QtyField);

        JPanel executionPanel = section("Execution");
        addRow(executionPanel, "Polling interval seconds", pollingField);

        content.add(strategyPanel);
        content.add(Box.createVerticalStrut(10));
        content.add(riskPanel);
        content.add(Box.createVerticalStrut(10));
        content.add(executionPanel);
        add(content, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.setBorder(new EmptyBorder(0, 14, 14, 14));
        JButton save = new JButton("Save Strategy");
        JButton cancel = new JButton("Cancel");
        save.addActionListener(e -> onSave());
        cancel.addActionListener(e -> {
            result = null;
            setVisible(false);
        });
        actions.add(save);
        actions.add(cancel);
        add(actions, BorderLayout.SOUTH);

        if (initialConfig != null) {
            applyConfig(initialConfig);
        }

        pack();
        setLocationRelativeTo(owner);
    }

    public StrategyConfig showDialog() {
        result = null;
        setVisible(true);
        return result;
    }

    private void applyConfig(StrategyConfig config) {
        symbolField.setText(config.symbol());
        paperMode.setSelected(config.paperTrading());
        basePriceField.setText(config.baseBuyPrice().toPlainString());
        baseQtyField.setText(String.valueOf(config.baseBuyQty()));
        stopActivationField.setText(config.stopActivationPrice().toPlainString());
        sellTriggerField.setText(config.sellTriggerPrice().toPlainString());
        loss1PriceField.setText(config.lossBuyLevel1Price().toPlainString());
        loss1QtyField.setText(String.valueOf(config.lossBuyLevel1Qty()));
        loss2PriceField.setText(config.lossBuyLevel2Price().toPlainString());
        loss2QtyField.setText(String.valueOf(config.lossBuyLevel2Qty()));
        pollingField.setText(String.valueOf(config.pollingSeconds()));
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
                    new BigDecimal(stopActivationField.getText().trim()),
                    new BigDecimal(sellTriggerField.getText().trim()),
                    new BigDecimal(loss1PriceField.getText().trim()),
                    Integer.parseInt(loss1QtyField.getText().trim()),
                    new BigDecimal(loss2PriceField.getText().trim()),
                    Integer.parseInt(loss2QtyField.getText().trim()),
                    Integer.parseInt(pollingField.getText().trim()),
                    paperMode.isSelected()
            );
            setVisible(false);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Please enter valid numeric values for all strategy fields.", "Invalid Input", JOptionPane.WARNING_MESSAGE);
        }
    }

    private JPanel section(String title) {
        JPanel panel = new JPanel(new GridLayout(0, 2, 10, 10));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(title),
                new EmptyBorder(10, 10, 10, 10)
        ));
        return panel;
    }

    private void addRow(JPanel panel, String label, JComponent component) {
        panel.add(new JLabel(label));
        panel.add(component);
    }
}

