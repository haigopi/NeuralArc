package com.neuralarc.ui;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Frame;

/**
 * The stock chart window. Modeless, so several charts can stay open beside the grid while the
 * operator keeps working.
 */
final class StockChartDialog extends JDialog {
    private final StockChartView view;

    StockChartDialog(Frame owner, String symbol, String context) {
        super(owner, (symbol == null ? "" : symbol) + " – Stock Chart", Dialog.ModalityType.MODELESS);
        DialogCloseActions.bindEscapeToClose(this);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        view = new StockChartView(symbol, context);
        add(view, BorderLayout.CENTER);

        JButton close = new JButton("Close");
        DialogButtonStyles.apply(close, "icons/close.svg");
        close.addActionListener(event -> dispose());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 8));
        actions.setBackground(view.getBackground());
        actions.add(close);
        add(actions, BorderLayout.SOUTH);

        setResizable(true);
        DialogSizing.packAndFitTall(this, 1180, 780);
        setLocationRelativeTo(owner);
    }

    StockChartView view() {
        return view;
    }
}
