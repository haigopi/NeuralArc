package com.neuralarc.ui;

import com.neuralarc.util.AppMetadata;
import com.neuralarc.util.FontLoader;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;

public class AboutDialog extends JDialog {
    public AboutDialog(JFrame owner) {
        super(owner, "About NeuralArc", true);
        setLayout(new BorderLayout());
        setResizable(false);

        JLabel title = new JLabel(AppMetadata.name());
        title.setFont(FontLoader.ui(Font.BOLD, 18f));
        title.setForeground(new Color(35, 35, 45));

        JLabel version = new JLabel("Version: " + AppMetadata.version());
        version.setFont(FontLoader.ui(Font.PLAIN, 13f));

        JTextArea legal = new JTextArea(AppMetadata.copyright() + "\n" + AppMetadata.patent());
        legal.setEditable(false);
        legal.setOpaque(false);
        legal.setLineWrap(true);
        legal.setWrapStyleWord(true);
        legal.setFont(FontLoader.ui(Font.PLAIN, 13f));
        legal.setForeground(new Color(70, 70, 85));

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(new EmptyBorder(18, 20, 12, 20));
        content.add(title, BorderLayout.NORTH);

        JPanel details = new JPanel();
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.setOpaque(false);
        details.add(version);
        details.add(Box.createVerticalStrut(12));
        details.add(legal);
        content.add(details, BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(new EmptyBorder(0, 20, 18, 20));
        JButton close = new JButton("Close");
        close.setFocusPainted(false);
        close.addActionListener(e -> setVisible(false));

        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.add(close);
        footer.add(actions, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);

        setPreferredSize(new Dimension(420, 220));
        pack();
        setLocationRelativeTo(owner);
    }
}
