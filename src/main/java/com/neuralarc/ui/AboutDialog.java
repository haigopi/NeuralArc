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
import java.awt.Image;
import java.net.URL;

public class AboutDialog extends JDialog {
    public AboutDialog(JFrame owner) {
        super(owner, "About NeuralArc", true);
        setLayout(new BorderLayout());
        setResizable(false);

        JPanel content = new JPanel(new BorderLayout(0, 0));
        content.setBackground(Color.WHITE);
        content.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1, true),
                new EmptyBorder(20, 22, 16, 22)
        ));

        JLabel logoLabel = new JLabel(loadLogo());
        logoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        logoLabel.setBorder(new EmptyBorder(10, 0, 6, 0));
        content.add(logoLabel, BorderLayout.NORTH);

        JLabel title = new JLabel(AppMetadata.name(), SwingConstants.CENTER);
        title.setFont(FontLoader.bold(24f));
        title.setForeground(new Color(86, 92, 102));

        JLabel version = new JLabel("Version " + AppMetadata.version(), SwingConstants.CENTER);
        version.setFont(FontLoader.bold(13f));
        version.setForeground(new Color(122, 128, 138));

        JTextArea legal = new JTextArea(AppMetadata.copyright() + System.lineSeparator() + AppMetadata.patent());
        legal.setEditable(false);
        legal.setOpaque(false);
        legal.setLineWrap(true);
        legal.setWrapStyleWord(true);
        legal.setFont(FontLoader.regular(12f));
        legal.setForeground(new Color(150, 156, 166));
        legal.setAlignmentX(Component.CENTER_ALIGNMENT);
        legal.setBorder(new EmptyBorder(0, 6, 0, 6));

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setOpaque(false);
        textPanel.setBorder(new EmptyBorder(16, 10, 14, 10));
        textPanel.add(title);
        textPanel.add(Box.createVerticalStrut(8));
        textPanel.add(version);
        textPanel.add(Box.createVerticalStrut(10));
        textPanel.add(legal);
        for (Component component : textPanel.getComponents()) {
            if (component instanceof JComponent swingComponent) {
                swingComponent.setAlignmentX(Component.CENTER_ALIGNMENT);
            }
        }
        content.add(textPanel, BorderLayout.CENTER);

        add(content, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(new EmptyBorder(8, 20, 16, 20));
        JButton close = new JButton("Close");
        close.setFocusPainted(false);
        close.addActionListener(e -> setVisible(false));

        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.add(close);
        footer.add(actions, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);

        setPreferredSize(new Dimension(560, 400));
        pack();
        setLocationRelativeTo(owner);
    }

    private ImageIcon loadLogo() {
        URL resource = getClass().getResource("/logo.png");
        if (resource == null) {
            return new ImageIcon();
        }
        ImageIcon original = new ImageIcon(resource);
        Image scaled = original.getImage().getScaledInstance(110, 110, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }
}
