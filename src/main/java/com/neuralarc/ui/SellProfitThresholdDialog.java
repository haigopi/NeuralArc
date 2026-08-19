package com.neuralarc.ui;

import javax.swing.JOptionPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.Component;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

final class SellProfitThresholdDialog {
    private static final String MUTED = "#667085";

    private SellProfitThresholdDialog() {
    }

    static Optional<BigDecimal> show(Component parent, List<ManagedStrategy> targets) {
        JSpinner trailingPercentSpinner = new JSpinner(new SpinnerNumberModel(5.0d, 0.1d, 95.0d, 0.25d));
        String message = "<html><body style='width:420px'>"
                + "<b>Position all Sell Profit Threshold percentage</b><br>"
                + "<span style='color:" + MUTED + "'>This converts each selected open-position strategy with an active "
                + "sell trigger into Profit Hold mode. The current sell trigger price is used as the activation baseline, "
                + "then a trailing profit hold percent is applied for exit pullback.</span><br><br>"
                + "<b>Positions selected:</b> " + (targets == null ? 0 : targets.size())
                + "</body></html>";
        String trailingDescription = "<html><body style='width:420px;color:" + MUTED + "'>"
                + "Trailing percent defines how much price can pull back from the post-activation high before sell exit."
                + "</body></html>";
        Object[] content = {
                message,
                "Trailing pullback percent (%):", trailingPercentSpinner,
                trailingDescription
        };
        int choice = JOptionPane.showConfirmDialog(
                parent,
                content,
                "Position All Sell Profit Threshold Percentage",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (choice != JOptionPane.OK_OPTION) {
            return Optional.empty();
        }
        BigDecimal percent = BigDecimal.valueOf(((Number) trailingPercentSpinner.getValue()).doubleValue());
        if (percent.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }
        return Optional.of(percent);
    }
}
