package com.neuralarc.ui;

import javax.swing.ButtonGroup;
import javax.swing.JOptionPane;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.Component;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

final class AverageLosingPositionsDialog {
    private static final String MUTED = "#667085";

    private AverageLosingPositionsDialog() {
    }

    static Optional<AverageLosingPositionsSelection> show(Component parent, List<ManagedStrategy> targets) {
        JRadioButton limitOrder = new JRadioButton("Patient average-down: buy only if price pulls back", true);
        JRadioButton marketOrder = new JRadioButton("Immediate average-down: buy now at market", false);
        ButtonGroup orderType = new ButtonGroup();
        orderType.add(limitOrder);
        orderType.add(marketOrder);

        JSpinner discountSpinner = new JSpinner(new SpinnerNumberModel(1.0d, 0.0d, 99.0d, 0.25d));
        JRadioButton currentQuantity = new JRadioButton("Double-down size: buy the same share count already held", true);
        JRadioButton fixedQuantity = new JRadioButton("Controlled add: buy a fixed share count per position", false);
        ButtonGroup quantityMode = new ButtonGroup();
        quantityMode.add(currentQuantity);
        quantityMode.add(fixedQuantity);
        JSpinner quantitySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 1_000_000, 1));

        String message = "<html><body style='width:420px'>"
                + "<b>Average down losing positions</b><br>"
                + "<span style='color:" + MUTED + "'>This submits one manual buy order for each losing open position "
                + "in the current workspace. From the All tab, it applies to all visible workspaces.</span><br><br>"
                + "<b>Positions selected:</b> " + (targets == null ? 0 : targets.size())
                + "</body></html>";
        String executionDescription = muted(
                "<b>How execution works</b><br>"
                        + "Patient average-down places each limit order below that position's cached market price. "
                        + "It can avoid chasing price, but orders may not fill. Immediate average-down sends market orders "
                        + "and prioritizes execution; fill prices can move."
        );
        String discountDescription = muted(
                "Example: if AAPL is $100 and pullback is 2%, the limit buy is placed at $98."
        );
        String sizingDescription = muted(
                "<b>How share size works</b><br>"
                        + "Double-down size mirrors each position's current share count. Controlled add uses the same quantity "
                        + "for every matched symbol, useful when you want a smaller risk increase."
        );
        Object[] content = {
                message,
                executionDescription,
                limitOrder,
                "Required pullback below market (%):", discountSpinner,
                discountDescription,
                marketOrder,
                sizingDescription,
                currentQuantity,
                fixedQuantity,
                "Fixed add quantity per position:", quantitySpinner
        };

        int choice = JOptionPane.showConfirmDialog(
                parent,
                content,
                "Average Down Losing Positions",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (choice != JOptionPane.OK_OPTION) {
            return Optional.empty();
        }
        AverageLosingPositionsSelection.OrderType selectedOrderType = marketOrder.isSelected()
                ? AverageLosingPositionsSelection.OrderType.MARKET
                : AverageLosingPositionsSelection.OrderType.LIMIT_BELOW_MARKET;
        AverageLosingPositionsSelection.QuantityMode selectedQuantityMode = fixedQuantity.isSelected()
                ? AverageLosingPositionsSelection.QuantityMode.FIXED_INPUT_QUANTITY
                : AverageLosingPositionsSelection.QuantityMode.CURRENT_POSITION_QUANTITY;
        BigDecimal discountPercent = BigDecimal.valueOf(((Number) discountSpinner.getValue()).doubleValue());
        int quantity = ((Number) quantitySpinner.getValue()).intValue();
        return Optional.of(new AverageLosingPositionsSelection(
                selectedOrderType,
                selectedQuantityMode,
                quantity,
                discountPercent
        ));
    }

    private static String muted(String body) {
        return "<html><body style='width:420px;color:" + MUTED + "'>" + body + "</body></html>";
    }
}
