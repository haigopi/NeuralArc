package com.neuralarc.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

public class FeedbackDialog extends JDialog {
    private final JTextField phoneField = new JTextField(12);
    private final JTextArea descriptionArea = new JTextArea(7, 34);
    private FeedbackData result;

    public FeedbackDialog(JFrame owner, String title) {
        super(owner, title, true);
        setLayout(new BorderLayout(10, 10));
        ((JComponent) getContentPane()).setBorder(new EmptyBorder(16, 18, 14, 18));

        add(buildHeader(title), BorderLayout.NORTH);
        add(buildForm(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        setPreferredSize(new Dimension(560, 410));
        pack();
        setLocationRelativeTo(owner);
    }

    private JComponent buildHeader(String title) {
        JPanel header = new JPanel(new BorderLayout(0, 6));
        JLabel heading = new JLabel(title);
        heading.setFont(heading.getFont().deriveFont(16f));
        heading.setForeground(new Color(40, 40, 96));

        JTextArea subtitle = new JTextArea("Please share your phone and details. We will review and follow up.");
        subtitle.setEditable(false);
        subtitle.setOpaque(false);
        subtitle.setLineWrap(true);
        subtitle.setWrapStyleWord(true);
        subtitle.setBorder(null);

        header.add(heading, BorderLayout.NORTH);
        header.add(subtitle, BorderLayout.CENTER);
        return header;
    }

    private JComponent buildForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(210, 214, 228), 1, true),
                new EmptyBorder(12, 12, 10, 12)
        ));
        form.setBackground(new Color(248, 250, 255));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(4, 0, 4, 0);

        gbc.gridy = 0;
        form.add(new JLabel("Phone Number *"), gbc);

        gbc.gridy = 1;
        JPanel phoneWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        phoneWrap.setOpaque(false);
        Dimension phoneSize = phoneField.getPreferredSize();
        phoneField.setPreferredSize(new Dimension(180, phoneSize.height));
        phoneWrap.add(phoneField);
        form.add(phoneWrap, gbc);

        gbc.gridy = 2;
        form.add(new JLabel("Description *"), gbc);

        gbc.gridy = 3;
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setBorder(new EmptyBorder(6, 6, 6, 6));
        JScrollPane descriptionScroll = new JScrollPane(descriptionArea);
        descriptionScroll.setPreferredSize(new Dimension(420, 170));
        form.add(descriptionScroll, gbc);

        return form;
    }

    private JComponent buildFooter() {
        JPanel actions = new JPanel(new BorderLayout());
        JButton helpFaq = new JButton("Help & FAQ");
        JButton cancelButton = new JButton("Cancel");
        JButton sendButton = new JButton("Submit");
        DialogButtonStyles.apply(helpFaq);
        DialogButtonStyles.apply(cancelButton);
        DialogButtonStyles.apply(sendButton);

        helpFaq.addActionListener(e -> new HelpDialog((JFrame) getOwner()).setVisible(true));
        sendButton.addActionListener(e -> onSend());
        cancelButton.addActionListener(e -> {
            result = null;
            setVisible(false);
        });

        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightActions.setOpaque(false);
        rightActions.add(cancelButton);
        rightActions.add(sendButton);

        actions.add(helpFaq, BorderLayout.WEST);
        actions.add(rightActions, BorderLayout.EAST);
        return actions;
    }

    public FeedbackData showDialog() {
        result = null;
        setVisible(true);
        return result;
    }

    private void onSend() {
        String phone = phoneField.getText().trim();
        String description = descriptionArea.getText().trim();
        if (phone.isBlank()) {
            JOptionPane.showMessageDialog(this, "Phone number is required.", "Missing Field", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (description.isBlank()) {
            JOptionPane.showMessageDialog(this, "Description is required.", "Missing Field", JOptionPane.WARNING_MESSAGE);
            return;
        }
        result = new FeedbackData(phone, description);
        setVisible(false);
    }

    public record FeedbackData(String phoneNumber, String description) {}
}
