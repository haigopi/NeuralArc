package com.neuralarc.ui;

import com.neuralarc.util.AppMetadata;
import com.neuralarc.util.FontLoader;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.net.URL;

public class SplashScreenWindow extends JWindow {
    private static final Color BACKDROP = new Color(0, 0, 0, 0);
    private static final Color CARD_TOP = new Color(13, 24, 36);
    private static final Color CARD_BOTTOM = new Color(8, 15, 24);
    private static final Color BORDER = new Color(84, 108, 132, 92);
    private static final Color TEXT_PRIMARY = new Color(240, 246, 251);
    private static final Color TEXT_SECONDARY = new Color(164, 181, 198);
    private static final Color TEXT_TERTIARY = new Color(117, 136, 156);

    public SplashScreenWindow() {
        setBackground(BACKDROP);

        JPanel root = new JPanel(new BorderLayout());
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(18, 18, 18, 18));

        JPanel content = new RoundedGradientPanel();
        content.setLayout(new BorderLayout(0, 0));
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(24, 28, 18, 28));

        JLabel logoLabel = new JLabel(loadLogo());
        logoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        logoLabel.setBorder(new EmptyBorder(18, 0, 12, 0));
        content.add(logoLabel, BorderLayout.NORTH);

        JLabel title = new JLabel(AppMetadata.name(), SwingConstants.CENTER);
        title.setFont(FontLoader.bold(32f));
        title.setForeground(TEXT_PRIMARY);

        JLabel version = new JLabel("Loading " + AppMetadata.displayVersion(), SwingConstants.CENTER);
        version.setFont(FontLoader.bold(FontLoader.DEFAULT_UI_SIZE + 1f));
        version.setForeground(TEXT_SECONDARY);

        JLabel caption = new JLabel("Preparing your trading workspace", SwingConstants.CENTER);
        caption.setFont(FontLoader.regular(13f));
        caption.setForeground(TEXT_TERTIARY);

        JLabel status = new JLabel("Local-first desktop AI trader", SwingConstants.CENTER);
        status.setFont(FontLoader.regular(12f));
        status.setForeground(TEXT_SECONDARY);

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setOpaque(false);
        textPanel.setBorder(new EmptyBorder(18, 24, 28, 24));
        textPanel.add(title);
        textPanel.add(Box.createVerticalStrut(10));
        textPanel.add(version);
        textPanel.add(Box.createVerticalStrut(10));
        textPanel.add(caption);
        textPanel.add(Box.createVerticalStrut(8));
        textPanel.add(status);
        for (Component component : textPanel.getComponents()) {
            if (component instanceof JComponent swingComponent) {
                swingComponent.setAlignmentX(Component.CENTER_ALIGNMENT);
            }
        }
        content.add(textPanel, BorderLayout.CENTER);

        root.add(content, BorderLayout.CENTER);
        setContentPane(root);
        setAlwaysOnTop(true);
        setSize(new Dimension(580, 408));
        setLocationRelativeTo(null);
    }

    private ImageIcon loadLogo() {
        URL resource = getClass().getResource("/logo.png");
        if (resource == null) {
            return new ImageIcon();
        }
        ImageIcon original = new ImageIcon(resource);
        Image scaled = original.getImage().getScaledInstance(160, 160, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }

    private static final class RoundedGradientPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();

            g2.setColor(new Color(0, 0, 0, 56));
            g2.fillRoundRect(4, 10, width - 8, height - 8, 34, 34);

            g2.setPaint(new GradientPaint(0, 0, CARD_TOP, 0, height, CARD_BOTTOM));
            g2.fillRoundRect(0, 0, width - 1, height - 1, 30, 30);

            g2.setColor(new Color(255, 255, 255, 12));
            g2.fillRoundRect(1, 1, width - 3, (height / 3), 30, 30);

            g2.setColor(BORDER);
            g2.drawRoundRect(0, 0, width - 1, height - 1, 30, 30);

            g2.dispose();
        }
    }

}
