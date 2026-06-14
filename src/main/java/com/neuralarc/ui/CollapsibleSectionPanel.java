package com.neuralarc.ui;

import com.neuralarc.util.FontLoader;
import com.neuralarc.util.ThemeColors;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;

/**
 * Shared section chrome for operator-console panels that can be folded away
 * without destroying their child component state.
 */
final class CollapsibleSectionPanel extends JPanel {
    static final String COLLAPSED_PROPERTY = "collapsed";

    private final JButton toggleButton;
    private final JComponent content;
    private final ChevronIcon chevronIcon = new ChevronIcon();
    private boolean collapsed;

    CollapsibleSectionPanel(String title, JComponent content) {
        super(new BorderLayout(0, 4));
        this.content = content;
        this.toggleButton = new JButton(title);

        setOpaque(false);
        setBorder(BorderFactory.createCompoundBorder(
                new EmptyBorder(10, 0, 0, 0),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createTitledBorder(
                                BorderFactory.createLineBorder(ThemeColors.color("NeuralArc.Section.border", new Color(208, 214, 222)), 1, true),
                                "",
                                TitledBorder.LEADING,
                                TitledBorder.TOP),
                        new EmptyBorder(0, 8, 8, 8)
                )
        ));

        configureToggle();
        add(toggleButton, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
        setCollapsed(false);
    }

    private void configureToggle() {
        toggleButton.setBorder(BorderFactory.createEmptyBorder(0, 2, 2, 0));
        toggleButton.setContentAreaFilled(false);
        toggleButton.setFocusPainted(false);
        toggleButton.setHorizontalAlignment(SwingConstants.LEFT);
        toggleButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        toggleButton.setFont(FontLoader.ui(java.awt.Font.BOLD, 10f));
        toggleButton.setForeground(ThemeColors.color("NeuralArc.Section.titleForeground", new Color(78, 84, 94)));
        toggleButton.setIcon(chevronIcon);
        toggleButton.setIconTextGap(6);
        toggleButton.addActionListener(this::toggleCollapsed);
    }

    private void toggleCollapsed(ActionEvent event) {
        setCollapsed(!collapsed);
    }

    void setCollapsed(boolean collapsed) {
        boolean oldValue = this.collapsed;
        this.collapsed = collapsed;
        content.setVisible(!collapsed);
        chevronIcon.setCollapsed(collapsed);
        toggleButton.repaint();
        firePropertyChange(COLLAPSED_PROPERTY, oldValue, collapsed);
        revalidate();
        repaint();
    }

    @Override
    public Dimension getMinimumSize() {
        Dimension preferred = getPreferredSize();
        return new Dimension(0, preferred.height);
    }

    private static final class ChevronIcon implements Icon {
        private static final int SIZE = 9;
        private boolean collapsed;

        void setCollapsed(boolean collapsed) {
            this.collapsed = collapsed;
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(component.getForeground());
                int midY = y + SIZE / 2;
                if (collapsed) {
                    int[] xs = {x + 2, x + 2, x + 7};
                    int[] ys = {y + 1, y + SIZE - 1, midY};
                    g2.fillPolygon(xs, ys, 3);
                } else {
                    int[] xs = {x + 1, x + SIZE - 1, x + SIZE / 2};
                    int[] ys = {y + 2, y + 2, y + 7};
                    g2.fillPolygon(xs, ys, 3);
                }
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }
    }
}
