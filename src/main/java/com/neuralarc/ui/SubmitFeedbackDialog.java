package com.neuralarc.ui;

import com.neuralarc.util.FontLoader;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

public class SubmitFeedbackDialog extends JDialog {
    private static final Font TITLE_FONT = FontLoader.ui(Font.BOLD, 17);
    private static final Font BODY_FONT = FontLoader.ui(Font.PLAIN, 13);
    private static final Font LABEL_FONT = FontLoader.ui(Font.BOLD, 12);

    SubmitFeedbackDialog(JFrame owner) {
        super(owner, "Submit Feedback", true);
        setLayout(new BorderLayout(12, 12));
        ((JComponent) getContentPane()).setBorder(new EmptyBorder(18, 20, 14, 20));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildForm(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        setPreferredSize(new Dimension(540, 460));
        pack();
        setLocationRelativeTo(owner);
    }

    private JPanel buildHeader() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(new EmptyBorder(0, 0, 8, 0));

        JLabel title = new JLabel("Help us improve NeuralArc");
        title.setFont(TITLE_FONT);
        title.setForeground(new Color(42, 42, 96));
        panel.add(title, BorderLayout.NORTH);

        JTextArea subtitle = new JTextArea(
                "Tell us what worked, what didn't, and what you'd like to see next. " +
                "Your feedback directly influences upcoming releases."
        );
        subtitle.setEditable(false);
        subtitle.setOpaque(false);
        subtitle.setLineWrap(true);
        subtitle.setWrapStyleWord(true);
        subtitle.setFont(BODY_FONT);
        subtitle.setForeground(new Color(65, 65, 65));
        subtitle.setBorder(null);
        panel.add(subtitle, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildForm() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(212, 216, 230), 1, true),
                new EmptyBorder(14, 14, 10, 14)
        ));
        panel.setBackground(new Color(250, 251, 255));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 0, 4, 0);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.gridx = 0;

        gbc.gridy = 0;
        panel.add(formLabel("Name"), gbc);
        gbc.gridy = 1;
        panel.add(formField(28), gbc);

        gbc.gridy = 2;
        panel.add(formLabel("Email"), gbc);
        gbc.gridy = 3;
        panel.add(formField(28), gbc);

        gbc.gridy = 4;
        panel.add(formLabel("Phone (optional)"), gbc);
        gbc.gridy = 5;
        panel.add(phoneField(), gbc);

        gbc.gridy = 6;
        panel.add(formLabel("Category"), gbc);
        gbc.gridy = 7;
        JComboBox<String> category = new JComboBox<>(new String[] {
                "Bug Report",
                "Feature Request",
                "UI / UX Feedback",
                "General Comment"
        });
        category.setFont(BODY_FONT);
        panel.add(category, gbc);

        gbc.gridy = 8;
        panel.add(formLabel("Feedback"), gbc);
        gbc.gridy = 9;
        JTextArea feedback = new JTextArea(6, 28);
        feedback.setLineWrap(true);
        feedback.setWrapStyleWord(true);
        feedback.setFont(BODY_FONT);
        panel.add(new JScrollPane(feedback), gbc);
        return panel;
    }

    private JPanel buildFooter() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton cancel = new JButton("Cancel");
        JButton submit = new JButton("Submit");
        cancel.addActionListener(e -> setVisible(false));
        submit.addActionListener(e -> setVisible(false));
        panel.add(cancel);
        panel.add(submit);
        return panel;
    }

    private JLabel formLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(LABEL_FONT);
        return label;
    }

    private JTextField formField(int columns) {
        JTextField field = new JTextField(columns);
        field.setFont(BODY_FONT);
        return field;
    }

    private JComponent phoneField() {
        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        wrapper.setOpaque(false);
        JTextField phone = formField(14);
        Dimension preferred = phone.getPreferredSize();
        phone.setPreferredSize(new Dimension(170, preferred.height));
        wrapper.add(phone);
        return wrapper;
    }
}
