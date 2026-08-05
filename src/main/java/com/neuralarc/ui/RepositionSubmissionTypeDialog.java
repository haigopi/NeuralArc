package com.neuralarc.ui;

import com.neuralarc.model.RepositionSubmissionType;
import com.neuralarc.model.Strategy;

import javax.swing.JOptionPane;
import java.awt.Component;
import java.util.Optional;

final class RepositionSubmissionTypeDialog {
    private RepositionSubmissionTypeDialog() {
    }

    static Optional<RepositionSubmissionType> show(Component parent, Strategy strategy) {
        Object[] options = {"Limit Buy", "Market Buy", "Cancel"};
        String message = "<html><body style='width:380px'>"
                + "<b>Reposition expired " + strategy.symbol() + "?</b><br><br>"
                + "<b>Limit Buy</b>: reactivates the strategy and submits a fresh base limit buy using existing strategy rules.<br>"
                + "<b>Market Buy</b>: reactivates the strategy and submits an immediate market buy for the base buy quantity."
                + "<br><br>Strategies with open positions or open orders are skipped by the service for safety."
                + "</body></html>";
        int choice = JOptionPane.showOptionDialog(
                parent,
                message,
                "Reposition Expired — " + strategy.symbol(),
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[0]
        );
        if (choice == 0) {
            return Optional.of(RepositionSubmissionType.LIMIT_BUY);
        }
        if (choice == 1) {
            return Optional.of(RepositionSubmissionType.MARKET_BUY);
        }
        return Optional.empty();
    }
}
