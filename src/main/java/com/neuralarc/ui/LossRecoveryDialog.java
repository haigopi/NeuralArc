package com.neuralarc.ui;

import com.neuralarc.analytics.LossRecoveryPlan;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.math.BigDecimal;

/**
 * Shows a {@link LossRecoveryPlan} for one losing position and, on Review and Execute, hands it back
 * to the caller to turn into an order ticket. A thin shell around {@link LossRecoveryPanel}, which
 * holds the wording.
 */
final class LossRecoveryDialog extends JDialog {
    private boolean executed;

    LossRecoveryDialog(Frame owner, String symbol, int shares, BigDecimal averageCost, BigDecimal currentPrice,
                       LossRecoveryPlan.Plan plan) {
        super(owner, "Minimize Loss Impact — " + (symbol == null ? "" : symbol), true);
        DialogCloseActions.bindEscapeToClose(this);
        setLayout(new BorderLayout());

        LossRecoveryPanel content = new LossRecoveryPanel(symbol, shares, averageCost, currentPrice, plan);
        add(content, BorderLayout.CENTER);

        JButton cancel = new JButton("Close");
        DialogButtonStyles.apply(cancel, "icons/close.svg");
        cancel.addActionListener(event -> dispose());
        JButton execute = content.executeButton();
        DialogButtonStyles.apply(execute, "icons/apply.svg");
        execute.addActionListener(event -> {
            executed = true;
            dispose();
        });

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 8));
        actions.add(cancel);
        actions.add(execute);
        add(actions, BorderLayout.SOUTH);

        DialogSizing.packAndFit(this, 620, 420);
        setLocationRelativeTo(owner);
    }

    /** True when the operator pressed Review and Execute rather than closing the window. */
    boolean wasExecuted() {
        return executed;
    }
}
