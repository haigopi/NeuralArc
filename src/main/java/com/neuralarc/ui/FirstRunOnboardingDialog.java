package com.neuralarc.ui;

import com.neuralarc.util.AppMetadata;
import com.neuralarc.util.FontLoader;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Font;
import java.net.URI;

final class FirstRunOnboardingDialog extends JDialog {
    private static final String[] STEP_IDS = {"welcome", "alpaca", "getStarted", "strategy"};

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardPanel = new JPanel(cardLayout);
    private final JButton backButton = new JButton("Back");
    private final JButton nextButton = new JButton("Next");
    private final JButton skipButton = new JButton("Skip Tour");
    private final JLabel stepLabel = new JLabel();
    private int stepIndex;
    private boolean completed;

    FirstRunOnboardingDialog(JFrame owner) {
        super(owner, "Welcome to NeuralArc", true);
        buildUi();
    }

    boolean showDialog() {
        setVisible(true);
        return completed;
    }

    private void buildUi() {
        setLayout(new BorderLayout(0, 12));
        getContentPane().setBackground(Color.WHITE);
        ((JComponent) getContentPane()).setBorder(new EmptyBorder(16, 16, 16, 16));

        JLabel title = new JLabel("Welcome to NeuralArc");
        title.setFont(FontLoader.ui(Font.BOLD, 16f));
        title.setForeground(new Color(38, 46, 58));

        stepLabel.setFont(FontLoader.ui(Font.PLAIN, 10f));
        stepLabel.setForeground(new Color(108, 117, 125));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(title, BorderLayout.NORTH);
        header.add(stepLabel, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        cardPanel.setOpaque(false);
        cardPanel.add(createStepPanel(
                "What NeuralArc Does",
                """
                NeuralArc is a desktop trading utility that helps you manage staged limit-buy strategies, stop loss rules,
                target exits, and profit-hold logic.

                It starts in paper trading by default so you can validate your setup before using LIVE mode.
                """
        ), STEP_IDS[0]);
        cardPanel.add(createAlpacaStepPanel(), STEP_IDS[1]);
        cardPanel.add(createStepPanel(
                "Get Started",
                """
                Recommended first path:

                1. Copy your Alpaca Paper API keys
                2. Put the key and secret in Settings
                3. Click Verify Connection
                4. Try Lucky Strategies with Top 20 Diversified Stocks
                5. Review results in paper mode until the workflow feels comfortable

                Move to LIVE mode and real funds only after you understand the strategy behavior and risk.
                """
        ), STEP_IDS[2]);
        cardPanel.add(createStepPanel(
                "How to Add Strategies",
                """
                After your Alpaca paper credentials are saved:

                1. Click Add New Stock Strategy
                2. Enter the base buy, staged buy levels, stop loss, and target sell values
                3. Save the strategy to place the first paper limit buy

                You can edit, pause, or later promote a paper strategy to LIVE from the Stock Strategies grid.
                """
        ), STEP_IDS[3]);
        add(cardPanel, BorderLayout.CENTER);

        DialogButtonStyles.apply(backButton, "icons/edit.svg");
        DialogButtonStyles.apply(nextButton, "icons/apply.svg");
        DialogButtonStyles.apply(skipButton, "icons/close.svg");

        backButton.addActionListener(event -> moveStep(-1));
        nextButton.addActionListener(event -> {
            if (stepIndex == STEP_IDS.length - 1) {
                completed = true;
                dispose();
            } else {
                moveStep(1);
            }
        });
        skipButton.addActionListener(event -> {
            completed = true;
            dispose();
        });

        JPanel actions = new JPanel(new BorderLayout());
        actions.setOpaque(false);
        JPanel left = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(skipButton);
        JPanel right = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(backButton);
        right.add(nextButton);
        actions.add(left, BorderLayout.WEST);
        actions.add(right, BorderLayout.EAST);
        add(actions, BorderLayout.SOUTH);

        DialogSizing.packAndFit(this, 560, 340);
        setResizable(true);
        setLocationRelativeTo(getOwner());
        refreshStep();
    }

    private JPanel createAlpacaStepPanel() {
        JPanel panel = createStepPanel(
                "Create Alpaca Paper API Keys",
                """
                NeuralArc needs Alpaca API credentials to place paper trading orders and read prices.

                Recommended path:

                1. Register for an Alpaca account
                2. Generate Paper Trading API credentials
                3. Open Settings in NeuralArc
                4. Paste your API key and secret
                5. Click Verify Connection
                6. Click Encrypt, Save and Close
                """
        );

        JButton signupButton = new JButton("Open Alpaca Signup");
        DialogButtonStyles.apply(signupButton, "icons/contact-us.svg");
        signupButton.addActionListener(event -> openExternalLink(AppMetadata.alpacaSignupUrl()));

        JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        actions.setBorder(new EmptyBorder(6, 0, 0, 0));
        actions.add(signupButton);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createStepPanel(String heading, String body) {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(214, 220, 228), 1, true),
                new EmptyBorder(16, 16, 16, 16)
        ));

        JLabel headingLabel = new JLabel(heading);
        headingLabel.setFont(FontLoader.ui(Font.BOLD, 13f));
        headingLabel.setForeground(new Color(49, 57, 68));

        JTextArea textArea = new JTextArea(body.strip());
        textArea.setEditable(false);
        textArea.setWrapStyleWord(true);
        textArea.setLineWrap(true);
        textArea.setOpaque(false);
        textArea.setFocusable(false);
        textArea.setFont(FontLoader.ui(Font.PLAIN, 11f));
        textArea.setForeground(new Color(93, 100, 111));
        textArea.setBorder(null);

        panel.add(headingLabel, BorderLayout.NORTH);
        panel.add(textArea, BorderLayout.CENTER);
        return panel;
    }

    private void moveStep(int delta) {
        stepIndex = Math.max(0, Math.min(STEP_IDS.length - 1, stepIndex + delta));
        refreshStep();
    }

    private void refreshStep() {
        cardLayout.show(cardPanel, STEP_IDS[stepIndex]);
        stepLabel.setText("Step " + (stepIndex + 1) + " of " + STEP_IDS.length);
        backButton.setEnabled(stepIndex > 0);
        nextButton.setText(stepIndex == STEP_IDS.length - 1 ? "Finish" : "Next");
    }

    private void openExternalLink(String url) {
        if (url == null || url.isBlank() || !Desktop.isDesktopSupported()) {
            return;
        }
        try {
            Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception ignored) {
            // Non-fatal: the user can still copy the URL manually if needed.
        }
    }
}
