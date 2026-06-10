package com.neuralarc.ui;

import com.neuralarc.model.Strategy;
import com.neuralarc.util.Monetary;

import javax.swing.JOptionPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.Component;
import java.math.BigDecimal;
import java.util.Optional;

final class ManualLimitBuyDialog {
    private ManualLimitBuyDialog() {
    }

    static Optional<ManualLimitBuySelection> show(Component parent, Strategy strategy, BigDecimal currentPrice) {
        if (strategy == null) {
            return Optional.empty();
        }
        JSpinner quantitySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 1_000_000, 1));
        JTextField limitPriceField = new JTextField(defaultLimitPrice(currentPrice), 12);
        String currentPriceText = currentPrice != null && currentPrice.compareTo(BigDecimal.ZERO) > 0
                ? "$" + Monetary.round(currentPrice).toPlainString()
                : "not available";
        String message = "<html><body style='width:380px'>"
                + "<b>Buy more shares of " + strategy.symbol() + " at limit price</b><br><br>"
                + "Current price: " + currentPriceText + "<br>"
                + "Enter the quantity and maximum limit price for the buy order."
                + "<br><br>The strategy remains active and the order is recorded in trade history as a manual buy."
                + "</body></html>";
        Object[] content = {message, "Quantity:", quantitySpinner, "Limit price:", limitPriceField};

        while (true) {
            int choice = JOptionPane.showConfirmDialog(
                    parent,
                    content,
                    "Buy More at Limit - " + strategy.symbol(),
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (choice != JOptionPane.OK_OPTION) {
                return Optional.empty();
            }

            int quantity = ((Number) quantitySpinner.getValue()).intValue();
            BigDecimal limitPrice = parsePositivePrice(limitPriceField.getText());
            if (limitPrice.compareTo(BigDecimal.ZERO) <= 0) {
                JOptionPane.showMessageDialog(
                        parent,
                        "Enter a valid limit price greater than zero.",
                        "Invalid Limit Price",
                        JOptionPane.WARNING_MESSAGE
                );
                continue;
            }
            if (!confirmAboveCurrentPrice(parent, strategy, limitPrice, currentPrice)) {
                continue;
            }
            return Optional.of(new ManualLimitBuySelection(quantity, limitPrice));
        }
    }

    static String defaultLimitPrice(BigDecimal currentPrice) {
        return currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0
                ? ""
                : Monetary.round(currentPrice).toPlainString();
    }

    private static BigDecimal parsePositivePrice(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return Monetary.round(new BigDecimal(value.trim()));
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private static boolean confirmAboveCurrentPrice(
            Component parent,
            Strategy strategy,
            BigDecimal limitPrice,
            BigDecimal currentPrice
    ) {
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0 || limitPrice.compareTo(currentPrice) <= 0) {
            return true;
        }
        int choice = JOptionPane.showConfirmDialog(
                parent,
                "<html><body style='width:360px'>"
                        + "The limit price $" + Monetary.round(limitPrice).toPlainString()
                        + " is above the current price $" + Monetary.round(currentPrice).toPlainString()
                        + " for " + strategy.symbol() + ".<br><br>"
                        + "That can fill immediately at a higher-than-expected price. Continue?"
                        + "</body></html>",
                "Limit Price Above Current Price",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        return choice == JOptionPane.YES_OPTION;
    }
}
