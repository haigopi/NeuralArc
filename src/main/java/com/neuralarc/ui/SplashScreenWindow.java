package com.neuralarc.ui;

import com.neuralarc.util.AppMetadata;
import com.neuralarc.util.FontLoader;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Image;
import java.awt.Component;
import java.net.URL;

public class SplashScreenWindow extends JWindow {
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final Timer progressTimer;
    private final long startedAtMillis;
    private final int splashDurationMillis;

    public SplashScreenWindow(int splashDurationMillis) {
        this.splashDurationMillis = Math.max(0, splashDurationMillis);
        this.startedAtMillis = System.currentTimeMillis();
        JPanel content = new JPanel(new BorderLayout(0, 0));
        content.setBackground(Color.WHITE);
        content.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1, true),
                new EmptyBorder(22, 24, 0, 24)
        ));

        JLabel logoLabel = new JLabel(loadLogo());
        logoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        logoLabel.setBorder(new EmptyBorder(20, 0, 12, 0));
        content.add(logoLabel, BorderLayout.NORTH);

        JLabel title = new JLabel(AppMetadata.name(), SwingConstants.CENTER);
        title.setFont(FontLoader.bold(30f));
        title.setForeground(new Color(86, 92, 102));

        JLabel version = new JLabel("Loading " + AppMetadata.version(), SwingConstants.CENTER);
        version.setFont(FontLoader.bold(FontLoader.DEFAULT_UI_SIZE));
        version.setForeground(new Color(122, 128, 138));

        JLabel caption = new JLabel("Preparing your trading workspace", SwingConstants.CENTER);
        caption.setFont(FontLoader.regular(13f));
        caption.setForeground(new Color(150, 156, 166));

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setOpaque(false);
        textPanel.setBorder(new EmptyBorder(18, 24, 34, 24));
        textPanel.add(title);
        textPanel.add(Box.createVerticalStrut(8));
        textPanel.add(version);
        textPanel.add(Box.createVerticalStrut(10));
        textPanel.add(caption);
        for (Component component : textPanel.getComponents()) {
            if (component instanceof JComponent swingComponent) {
                swingComponent.setAlignmentX(Component.CENTER_ALIGNMENT);
            }
        }
        content.add(textPanel, BorderLayout.CENTER);

        progressBar.setValue(0);
        progressBar.setBorderPainted(false);
        progressBar.setStringPainted(false);
        progressBar.setPreferredSize(new Dimension(0, 14));
        progressBar.setBackground(new Color(236, 239, 243));
        progressBar.setForeground(new Color(91, 127, 255));

        JPanel progressPanel = new JPanel(new BorderLayout());
        progressPanel.setOpaque(false);
        progressPanel.setBorder(new EmptyBorder(0, 0, 0, 0));
        progressPanel.add(progressBar, BorderLayout.CENTER);
        content.add(progressPanel, BorderLayout.SOUTH);

        setContentPane(content);
        setAlwaysOnTop(true);
        setSize(new Dimension(560, 430));
        setLocationRelativeTo(null);
        progressTimer = startProgressAnimation();
    }

    private ImageIcon loadLogo() {
        URL resource = getClass().getResource("/logo.png");
        if (resource == null) {
            return new ImageIcon();
        }
        ImageIcon original = new ImageIcon(resource);
        Image scaled = original.getImage().getScaledInstance(150, 150, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }

    @Override
    public void dispose() {
        if (progressTimer != null) {
            progressTimer.stop();
        }
        super.dispose();
    }

    private Timer startProgressAnimation() {
        if (splashDurationMillis <= 0) {
            progressBar.setValue(100);
            return new Timer(0, null);
        }

        Timer timer = new Timer(40, null);
        timer.addActionListener(event -> {
            long elapsed = System.currentTimeMillis() - startedAtMillis;
            int progress = (int) Math.min(100L, Math.round((elapsed * 100.0d) / splashDurationMillis));
            progressBar.setValue(progress);
            if (progress >= 100) {
                ((Timer) event.getSource()).stop();
            }
        });
        timer.start();
        return timer;
    }
}
