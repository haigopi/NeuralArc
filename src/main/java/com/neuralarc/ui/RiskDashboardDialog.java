package com.neuralarc.ui;

import com.neuralarc.util.FontLoader;
import com.neuralarc.util.ThemeColors;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Frame;

/**
 * Read-only strategy risk dashboard: renders the reconciliation + risk analytics HTML produced by
 * {@link RiskDashboardPresenter}. Self-contained themed dialog so it doesn't touch the main layout.
 */
final class RiskDashboardDialog extends JDialog {
    private static final Color DIALOG_BG = UIManager.getColor("Panel.background") != null
            ? UIManager.getColor("Panel.background") : Color.WHITE;
    private static final Color TEXT_PRIMARY = UIManager.getColor("Label.foreground") != null
            ? UIManager.getColor("Label.foreground") : new Color(41, 51, 66);
    private static final Color SECTION_BORDER = ThemeColors.color("NeuralArc.Section.border", new Color(208, 214, 222));

    RiskDashboardDialog(Frame owner, String html) {
        super(owner, "Strategy Risk Dashboard", true);
        setLayout(new BorderLayout(0, 12));
        getContentPane().setBackground(DIALOG_BG);
        ((JComponent) getContentPane()).setBorder(new EmptyBorder(14, 14, 14, 14));

        JEditorPane body = new JEditorPane("text/html", html);
        body.setEditable(false);
        body.setOpaque(true);
        body.setBackground(DIALOG_BG);
        body.setForeground(TEXT_PRIMARY);
        body.setFont(FontLoader.ui(Font.PLAIN, 11f));
        body.setBorder(new EmptyBorder(4, 4, 4, 4));
        body.setCaretPosition(0);

        JScrollPane scrollPane = new JScrollPane(body);
        scrollPane.setBorder(BorderFactory.createLineBorder(SECTION_BORDER, 1, true));
        scrollPane.getViewport().setBackground(DIALOG_BG);
        add(scrollPane, BorderLayout.CENTER);

        JButton closeButton = new JButton("Close");
        DialogButtonStyles.apply(closeButton, "icons/close.svg");
        closeButton.addActionListener(event -> dispose());
        JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(closeButton);
        add(actions, BorderLayout.SOUTH);

        setResizable(true);
        DialogSizing.packAndFit(this, 620, 640);
        setLocationRelativeTo(owner);
    }
}
