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
    private static final Color TRACK = new Color(32, 48, 64);
    private static final Color ACCENT_START = new Color(124, 246, 196);
    private static final Color ACCENT_END = new Color(120, 185, 255);

    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final int splashDurationMillis;
    private Timer progressTimer;
    private long startedAtMillis;

    public SplashScreenWindow(int splashDurationMillis) {
        this.splashDurationMillis = Math.max(0, splashDurationMillis);
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

        progressBar.setValue(0);
        progressBar.setBorderPainted(false);
        progressBar.setStringPainted(false);
        progressBar.setPreferredSize(new Dimension(0, 12));
        progressBar.setBackground(TRACK);
        progressBar.setForeground(ACCENT_END);
        progressBar.setOpaque(false);
        progressBar.setUI(new GradientProgressBarUI());

        JPanel progressPanel = new JPanel(new BorderLayout());
        progressPanel.setOpaque(false);
        progressPanel.setBorder(new EmptyBorder(2, 0, 0, 0));
        progressPanel.add(progressBar, BorderLayout.CENTER);
        content.add(progressPanel, BorderLayout.SOUTH);

        root.add(content, BorderLayout.CENTER);
        setContentPane(root);
        setAlwaysOnTop(true);
        setSize(new Dimension(580, 438));
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

    @Override
    public void dispose() {
        if (progressTimer != null) {
            progressTimer.stop();
            progressTimer = null;
        }
        super.dispose();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            startProgressAnimation();
        }
    }

    private void startProgressAnimation() {
        if (progressTimer != null && progressTimer.isRunning()) {
            return;
        }
        startedAtMillis = System.currentTimeMillis();
        if (splashDurationMillis <= 0) {
            progressBar.setValue(100);
            progressBar.repaint();
            return;
        }

        progressBar.setValue(0);
        progressBar.repaint();

        Timer timer = new Timer(30, null);
        timer.setCoalesce(true);
        timer.addActionListener(event -> {
            long elapsed = System.currentTimeMillis() - startedAtMillis;
            int progress = (int) Math.min(100L, Math.round((elapsed * 100.0d) / splashDurationMillis));
            progressBar.setValue(progress);
            progressBar.repaint();
            if (progress >= 100) {
                ((Timer) event.getSource()).stop();
            }
        });
        timer.start();
        progressTimer = timer;
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

    private static final class GradientProgressBarUI extends javax.swing.plaf.basic.BasicProgressBarUI {
        @Override
        protected Dimension getPreferredInnerHorizontal() {
            return new Dimension(146, 12);
        }

        @Override
        protected void paintDeterminate(Graphics graphics, JComponent component) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = progressBar.getWidth();
            int height = progressBar.getHeight();
            int arc = height;

            g2.setColor(TRACK);
            g2.fillRoundRect(0, 0, width, height, arc, arc);

            int amountFull = Math.max(0, Math.min(width, getAmountFull(null, width, height)));
            if (amountFull > 0) {
                g2.setPaint(new GradientPaint(0, 0, ACCENT_START, width, 0, ACCENT_END));
                int fillArc = Math.min(arc, amountFull * 2);
                g2.fillRoundRect(0, 0, amountFull, height, fillArc, fillArc);
            }

            g2.dispose();
        }
    }
}
