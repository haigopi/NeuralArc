package com.neuralarc.ui;

import com.neuralarc.model.Strategy;
import com.neuralarc.model.TimeInForce;
import com.neuralarc.util.Monetary;

import javax.swing.ButtonGroup;
import javax.swing.JOptionPane;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.Component;
import java.awt.FlowLayout;
import java.math.BigDecimal;
import java.util.Optional;

final class ManualLimitBuyDialog {
    private ManualLimitBuyDialog() {
    }

    static Optional<ManualLimitBuySelection> show(
            Component parent,
            Strategy strategy,
            BigDecimal currentPrice,
            TimeInForce defaultTimeInForce
    ) {
        return show(parent, strategy, currentPrice, defaultTimeInForce, 1, null, null);
    }

    /**
     * The same ticket, seeded with an order the app has already worked out — the loss-recovery plan's
     * add, for instance. The operator still reviews and may change every field before submitting.
     */
    static Optional<ManualLimitBuySelection> show(
            Component parent,
            Strategy strategy,
            BigDecimal currentPrice,
            TimeInForce defaultTimeInForce,
            int suggestedQuantity,
            BigDecimal suggestedLimitPrice,
            String introHtml
    ) {
        if (strategy == null) {
            return Optional.empty();
        }
        JSpinner quantitySpinner = new JSpinner(
                new SpinnerNumberModel(Math.max(1, suggestedQuantity), 1, 1_000_000, 1));
        JTextField limitPriceField = new JTextField(
                suggestedLimitPrice != null && suggestedLimitPrice.compareTo(BigDecimal.ZERO) > 0
                        ? Monetary.round(suggestedLimitPrice).toPlainString()
                        : defaultLimitPrice(currentPrice),
                12);
        JRadioButton dayButton = new JRadioButton("DAY - expires at session close");
        JRadioButton gtcButton = new JRadioButton("GTC - works until filled or cancelled");
        dayButton.setOpaque(false);
        gtcButton.setOpaque(false);
        ButtonGroup timeInForceGroup = new ButtonGroup();
        timeInForceGroup.add(dayButton);
        timeInForceGroup.add(gtcButton);
        if (defaultTimeInForce == TimeInForce.GTC) {
            gtcButton.setSelected(true);
        } else {
            dayButton.setSelected(true);
        }
        JPanel timeInForcePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        timeInForcePanel.setOpaque(false);
        timeInForcePanel.add(dayButton);
        timeInForcePanel.add(gtcButton);
        JCheckBox repositionAfterExpiry = new JCheckBox("Automatically reposition this limit buy if it expires",
                strategy.resubmitOnExpiryEnabled());
        repositionAfterExpiry.setOpaque(false);
        String currentPriceText = currentPrice != null && currentPrice.compareTo(BigDecimal.ZERO) > 0
                ? "$" + Monetary.round(currentPrice).toPlainString()
                : "not available";
        String message = introHtml != null && !introHtml.isBlank() ? introHtml : "<html><body style='width:380px'>"
                + "<b>Buy more shares of " + strategy.symbol() + " at limit price</b><br><br>"
                + "Current price: " + currentPriceText + "<br>"
                + "Enter the quantity and maximum limit price for the buy order."
                + "<br><br>The strategy remains active and the order is recorded in trade history as a manual buy."
                + "<br><br><span style='color:#667085'>Time in force defaults to the value saved in Settings. "
                + "A GTC order never expires, so auto reposition only applies to a DAY order.</span>"
                + "</body></html>";
        Object[] content = {
                message,
                "Quantity:", quantitySpinner,
                "Limit price:", limitPriceField,
                "Time in force:", timeInForcePanel,
                repositionAfterExpiry
        };

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
            return Optional.of(new ManualLimitBuySelection(
                    quantity,
                    limitPrice,
                    repositionAfterExpiry.isSelected(),
                    gtcButton.isSelected() ? TimeInForce.GTC : TimeInForce.DAY
            ));
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
