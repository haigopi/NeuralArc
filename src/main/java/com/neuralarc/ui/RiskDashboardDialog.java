package com.neuralarc.ui;

import com.neuralarc.ui.chart.ChartPalette;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Frame;

/**
 * Read-only strategy risk dashboard: hosts the chart-based {@link RiskDashboardPanel} in a scroll
 * pane. Self-contained themed dialog so it doesn't touch the main layout.
 */
final class RiskDashboardDialog extends JDialog {
    RiskDashboardDialog(Frame owner, JComponent content) {
        super(owner, "Strategy Risk Dashboard", true);
        setLayout(new BorderLayout());
        getContentPane().setBackground(ChartPalette.CANVAS_BG);

        JScrollPane scrollPane = new JScrollPane(content,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(ChartPalette.CANVAS_BG);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

        JButton closeButton = new JButton("Close");
        DialogButtonStyles.apply(closeButton, "icons/close.svg");
        closeButton.addActionListener(event -> dispose());
        JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 12, 8));
        actions.setBackground(ChartPalette.CANVAS_BG);
        actions.setBorder(new EmptyBorder(0, 0, 4, 8));
        actions.add(closeButton);
        add(actions, BorderLayout.SOUTH);

        setResizable(true);
        DialogSizing.packAndFit(this, 1040, 760);
        setLocationRelativeTo(owner);
    }
}
