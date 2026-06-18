package com.neuralarc.ui.chart;

import com.neuralarc.util.FontLoader;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * A titled, rounded white card used as the building block of the chart dashboard. Hosts any chart or
 * content component in its center and renders a subtle border so the dashboard reads as a grid of tiles.
 */
public final class DashboardCard extends JPanel {
    public DashboardCard(String title, JComponent content) {
        this(title, null, content);
    }

    public DashboardCard(String title, String subtitle, JComponent content) {
        super(new BorderLayout(0, 8));
        setOpaque(false);
        setBorder(new EmptyBorder(12, 14, 12, 14));

        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        JLabel titleLabel = new JLabel(title == null ? "" : title);
        titleLabel.setFont(FontLoader.ui(Font.BOLD, 12f));
        titleLabel.setForeground(ChartPalette.TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        heading.add(titleLabel);
        if (subtitle != null && !subtitle.isBlank()) {
            JLabel subtitleLabel = new JLabel(subtitle);
            subtitleLabel.setFont(FontLoader.ui(Font.PLAIN, 10f));
            subtitleLabel.setForeground(ChartPalette.TEXT_MUTED);
            subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            heading.add(Box.createVerticalStrut(2));
            heading.add(subtitleLabel);
        }
        add(heading, BorderLayout.NORTH);
        if (content != null) {
            content.setOpaque(false);
            add(content, BorderLayout.CENTER);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g2.setColor(ChartPalette.CARD_BG);
            g2.fillRoundRect(0, 0, w - 1, h - 1, 14, 14);
            g2.setColor(ChartPalette.CARD_BORDER);
            g2.drawRoundRect(0, 0, w - 1, h - 1, 14, 14);
        } finally {
            g2.dispose();
        }
        super.paintComponent(g);
    }

    /** Wrap any content in a card and give it a sensible minimum width so the grid lays out cleanly. */
    public static DashboardCard of(String title, JComponent content, int minWidth, int minHeight) {
        DashboardCard card = new DashboardCard(title, content);
        card.setPreferredSize(new Dimension(minWidth, minHeight));
        return card;
    }
}
