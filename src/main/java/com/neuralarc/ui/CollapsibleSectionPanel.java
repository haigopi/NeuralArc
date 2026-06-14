package com.neuralarc.ui;

import com.neuralarc.util.FontLoader;
import com.neuralarc.util.ThemeColors;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;

/**
 * Shared section chrome for operator-console panels that can be folded away
 * without destroying their child component state.
 */
final class CollapsibleSectionPanel extends JPanel {
    private final JButton toggleButton;
    private final JComponent content;
    private boolean collapsed;

    CollapsibleSectionPanel(String title, JComponent content) {
        super(new BorderLayout(0, 4));
        this.content = content;
        this.toggleButton = new JButton();

        setOpaque(false);
        setBorder(BorderFactory.createCompoundBorder(
                new EmptyBorder(10, 0, 0, 0),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(ThemeColors.color("NeuralArc.Section.border", new Color(208, 214, 222)), 1, true),
                        new EmptyBorder(6, 8, 8, 8)
                )
        ));

        configureToggle(title);
        add(toggleButton, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
        setCollapsed(false);
    }

    private void configureToggle(String title) {
        toggleButton.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
        toggleButton.setContentAreaFilled(false);
        toggleButton.setFocusPainted(false);
        toggleButton.setHorizontalAlignment(SwingConstants.LEFT);
        toggleButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        toggleButton.setFont(FontLoader.ui(java.awt.Font.BOLD, 10f));
        toggleButton.setForeground(ThemeColors.color("NeuralArc.Section.titleForeground", new Color(78, 84, 94)));
        toggleButton.addActionListener(event -> setCollapsed(!collapsed));
        toggleButton.putClientProperty("sectionTitle", title);
    }

    void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
        content.setVisible(!collapsed);
        String title = String.valueOf(toggleButton.getClientProperty("sectionTitle"));
        toggleButton.setText((collapsed ? "▸ " : "▾ ") + title);
        revalidate();
        repaint();
    }

    @Override
    public Dimension getMinimumSize() {
        Dimension preferred = getPreferredSize();
        return new Dimension(0, preferred.height);
    }
}
