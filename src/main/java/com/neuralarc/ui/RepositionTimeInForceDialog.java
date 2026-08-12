package com.neuralarc.ui;

import com.neuralarc.model.Strategy;
import com.neuralarc.model.TimeInForce;

import javax.swing.JOptionPane;
import java.awt.Component;
import java.util.Optional;

final class RepositionTimeInForceDialog {
    private RepositionTimeInForceDialog() {
    }

    static Optional<TimeInForce> show(Component parent, Strategy strategy) {
        Object[] options = {"DAY", "GTC", "Cancel"};
        TimeInForce current = strategy == null || strategy.timeInForce() == null
                ? TimeInForce.DAY
                : strategy.timeInForce();
        String message = "<html><body style='width:360px'>"
                + "<b>Choose Time In Force for repositioning " + (strategy == null ? "" : strategy.symbol()) + "</b><br><br>"
                + "<b>DAY</b>: expires at market close if not filled.<br>"
                + "<b>GTC</b>: stays open across sessions until filled, canceled, or broker expiry."
                + "</body></html>";
        int defaultIndex = current == TimeInForce.GTC ? 1 : 0;
        int choice = JOptionPane.showOptionDialog(
                parent,
                message,
                "Reposition Expired — Time In Force",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[defaultIndex]
        );
        if (choice == 0) {
            return Optional.of(TimeInForce.DAY);
        }
        if (choice == 1) {
            return Optional.of(TimeInForce.GTC);
        }
        return Optional.empty();
    }
}
