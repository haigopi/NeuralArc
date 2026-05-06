package com.neuralarc.ui;

import com.neuralarc.util.AppMetadata;
import com.neuralarc.util.FontLoader;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Properties;
import java.util.function.Consumer;

public final class LegalDisclosureController {
    private static final Path DEFAULT_DISCLOSURE_FILE = AppMetadata.appDataDirectory().resolve("legal-disclosure.properties");
    private static final String DEFAULT_DISCLOSURE_TEXT = String.join(System.lineSeparator(),
            "LEGAL DISCLOSURE AND USER RESPONSIBILITY AGREEMENT\n"
                    + "\n"
                    + "Last Updated: [Insert Date]\n"
                    + "\n"
                    + "This Legal Disclosure and User Responsibility Agreement (\"Agreement\") governs your use of the NeuralArc software application (\"NeuralArc\", \"Application\", or \"Software\"). By installing, accessing, or using NeuralArc, you acknowledge that you have read, understood, and agree to be bound by the terms set forth below.\n"
                    + "\n"
                    + "1. SERVICE FEE\n"
                    + "NeuralArc may apply a service fee equal to twenty percent (20%) of net profit realized from each completed sell transaction executed through the Application.\n"
                    + "\n"
                    + "- No service fee shall be applied to transactions resulting in a loss.\n"
                    + "- Profit calculations are based on data available to the Application and may not include taxes, brokerage fees, or third-party costs.\n"
                    + "- NeuralArc reserves the right to modify its fee structure with reasonable notice.\n"
                    + "\n"
                    + "2. NO INVESTMENT ADVICE\n"
                    + "NeuralArc is a software tool for trade execution and strategy automation only.\n"
                    + "\n"
                    + "- NeuralArc does not provide financial, investment, legal, or tax advice.\n"
                    + "- All trading decisions are made solely by you.\n"
                    + "- You are fully responsible for evaluating risks and outcomes of your strategies.\n"
                    + "\n"
                    + "3. ASSUMPTION OF RISK AND LOSS LIABILITY\n"
                    + "You acknowledge and agree that:\n"
                    + "\n"
                    + "- Trading securities involves substantial risk, including total loss of capital.\n"
                    + "- NeuralArc is not responsible for any losses, including but not limited to:\n"
                    + "  - Market volatility\n"
                    + "  - Slippage\n"
                    + "  - Partial or missed order fills\n"
                    + "  - Delayed execution\n"
                    + "  - Connectivity failures\n"
                    + "  - System errors or downtime\n"
                    + "- NeuralArc does not guarantee profitability or performance.\n"
                    + "\n"
                    + "4. NO FIDUCIARY RELATIONSHIP\n"
                    + "Use of NeuralArc does not create any fiduciary, advisory, or agency relationship between you and NeuralArc or its operators.\n"
                    + "\n"
                    + "- NeuralArc does not act in your best interest in a fiduciary capacity.\n"
                    + "- You retain full control and responsibility for all actions taken.\n"
                    + "\n"
                    + "5. ALPACA ACCOUNT INTEGRATION\n"
                    + "NeuralArc integrates with third-party brokerage services including Alpaca Markets.\n"
                    + "\n"
                    + "- Your brokerage account remains under your sole ownership and control.\n"
                    + "- NeuralArc does not hold or custody funds or securities.\n"
                    + "- API credentials are stored locally and used only to execute your instructions.\n"
                    + "- You are responsible for securing your API keys and permissions.\n"
                    + "\n"
                    + "6. THIRD-PARTY SERVICES DISCLAIMER\n"
                    + "NeuralArc depends on third-party services, including brokerage APIs and market data providers.\n"
                    + "\n"
                    + "- NeuralArc is not responsible for failures, inaccuracies, or interruptions from third-party services.\n"
                    + "- Changes to third-party APIs may affect functionality.\n"
                    + "\n"
                    + "7. DATA STORAGE AND PRIVACY\n"
                    + "- All strategy and application data is stored locally on your device by default.\n"
                    + "- NeuralArc does not upload or store your strategies in the cloud unless explicitly enabled in future features.\n"
                    + "- Optional telemetry, if enabled, is limited to system performance and operational metrics.\n"
                    + "\n"
                    + "8. DATA SECURITY DISCLAIMER\n"
                    + "- NeuralArc does not guarantee protection against unauthorized access to your device.\n"
                    + "- NeuralArc is not responsible for:\n"
                    + "  - Data loss\n"
                    + "  - Device compromise\n"
                    + "  - Malware or external attacks\n"
                    + "- You are responsible for maintaining device security and safe usage practices.\n"
                    + "\n"
                    + "9. BACKUP AND DATA INTEGRITY\n"
                    + "- You are solely responsible for backing up your data.\n"
                    + "- NeuralArc recommends regular export of strategies to avoid data loss.\n"
                    + "- NeuralArc is not responsible for recovery of lost or corrupted data.\n"
                    + "\n"
                    + "10. AVAILABILITY AND SYSTEM RELIABILITY\n"
                    + "- NeuralArc is provided \"as-is\" and \"as-available\".\n"
                    + "- The Application may experience interruptions, delays, or errors.\n"
                    + "- Continuous or error-free operation is not guaranteed.\n"
                    + "\n"
                    + "11. LIMITATION OF LIABILITY\n"
                    + "To the fullest extent permitted by law:\n"
                    + "\n"
                    + "- NeuralArc and its developers shall not be liable for any damages, including:\n"
                    + "  - Direct or indirect financial loss\n"
                    + "  - Loss of profits\n"
                    + "  - Loss of data\n"
                    + "  - Loss of opportunity\n"
                    + "- This applies regardless of cause, including negligence.\n"
                    + "\n"
                    + "12. INDEMNIFICATION\n"
                    + "You agree to indemnify and hold harmless NeuralArc, its developers, and affiliates from any claims, damages, or liabilities arising from:\n"
                    + "\n"
                    + "- Your use of the Application\n"
                    + "- Your trading activities\n"
                    + "- Violation of this Agreement\n"
                    + "\n"
                    + "13. TAX RESPONSIBILITY DISCLAIMER\n"
                    + "- You are solely responsible for reporting and paying any taxes related to your trading activities.\n"
                    + "- NeuralArc does not provide tax reporting or guidance.\n"
                    + "\n"
                    + "14. OPEN SOURCE AND SOFTWARE LICENSE (IF APPLICABLE)\n"
                    + "- Portions of NeuralArc may include open-source components governed by their respective licenses.\n"
                    + "- You agree to comply with all applicable third-party license terms.\n"
                    + "- NeuralArc itself may be distributed under a separate license, if provided.\n"
                    + "\n"
                    + "15. MODIFICATIONS AND UPDATES\n"
                    + "- NeuralArc may update or modify this Agreement at any time.\n"
                    + "- Continued use of the Application constitutes acceptance of updated terms.\n"
                    + "\n"
                    + "16. TERMINATION\n"
                    + "- NeuralArc reserves the right to suspend or terminate access for misuse, violations, or security risks.\n"
                    + "- You may discontinue use at any time.\n"
                    + "\n"
                    + "17. GOVERNING LAW\n"
                    + "This Agreement shall be governed by applicable laws of the jurisdiction in which the Application operator resides, without regard to conflict of law principles.\n"
                    + "\n"
                    + "18. USER RESPONSIBILITY\n"
                    + "You acknowledge that:\n"
                    + "\n"
                    + "- You are solely responsible for all trades executed through NeuralArc.\n"
                    + "- You understand the risks associated with automated trading.\n"
                    + "- You accept full responsibility for outcomes, including financial losses.\n"
                    + "\n"
                    + "19. ACKNOWLEDGMENT AND ACCEPTANCE\n"
                    + "By selecting \"Accept\", installing, or using NeuralArc, you:\n"
                    + "\n"
                    + "- Confirm that you have read and understood this Agreement\n"
                    + "- Accept all terms and conditions\n"
                    + "- Agree to use the Application at your own risk");

    private final Path disclosureFile;
    private final String disclosureText;

    public LegalDisclosureController() {
        this(DEFAULT_DISCLOSURE_FILE, DEFAULT_DISCLOSURE_TEXT);
    }

    LegalDisclosureController(Path disclosureFile, String disclosureText) {
        this.disclosureFile = disclosureFile;
        this.disclosureText = disclosureText == null ? "" : disclosureText;
    }

    public boolean loadAccepted() {
        if (!Files.exists(disclosureFile)) {
            return false;
        }
        Properties properties = new Properties();
        try (var input = Files.newInputStream(disclosureFile)) {
            properties.load(input);
            return Boolean.parseBoolean(properties.getProperty("accepted", "false"));
        } catch (Exception ignored) {
            return false;
        }
    }

    public void saveAccepted(boolean accepted) {
        Properties properties = new Properties();
        properties.setProperty("accepted", String.valueOf(accepted));
        properties.setProperty("acceptedAt", accepted ? Instant.now().toString() : "");
        try {
            Files.createDirectories(disclosureFile.getParent());
            try (var output = Files.newOutputStream(disclosureFile)) {
                properties.store(output, "NeuralArc legal disclosure acceptance");
            }
        } catch (Exception ignored) {
            // Keep app running even if acceptance state cannot be persisted.
        }
    }

    public boolean showDisclosure(
            JFrame owner,
            boolean accepted,
            boolean requireAcceptance,
            Consumer<Boolean> acceptanceUpdater
    ) {
        JDialog dialog = new JDialog(owner, "Legal Disclosure", true);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.getRootPane().setBorder(new EmptyBorder(10, 10, 10, 10));

        JTextArea disclosureArea = new JTextArea(disclosureText);
        disclosureArea.setEditable(false);
        disclosureArea.setLineWrap(true);
        disclosureArea.setWrapStyleWord(true);
        disclosureArea.setCaretPosition(0);
        disclosureArea.setFont(FontLoader.ui(java.awt.Font.PLAIN, 12f));
        JScrollPane disclosureScroll = new JScrollPane(disclosureArea);
        disclosureScroll.setPreferredSize(new Dimension(760, 440));

        JCheckBox acceptCheck = new JCheckBox("I have read and accept this legal disclosure.", accepted);
        boolean requiresScrollGate = requireAcceptance && !accepted;
        acceptCheck.setEnabled(!requiresScrollGate);
        JLabel scrollHint = new JLabel("Scroll to end to enable acceptance.");
        scrollHint.setFont(FontLoader.ui(java.awt.Font.PLAIN, 11f));
        scrollHint.setForeground(new Color(180, 160, 110));
        scrollHint.setVisible(requiresScrollGate);

        JButton acceptButton = new JButton(requireAcceptance ? "Accept and Continue" : "Save Acceptance");
        DialogButtonStyles.apply(acceptButton, "icons/verify.svg");
        JButton closeButton = new JButton(requireAcceptance ? "Decline" : "Close");
        DialogButtonStyles.apply(closeButton, "icons/close.svg");

        final boolean[] acceptedState = new boolean[]{accepted};
        acceptButton.setEnabled(acceptCheck.isSelected());
        acceptCheck.addActionListener(ignored -> acceptButton.setEnabled(acceptCheck.isSelected()));

        if (requiresScrollGate) {
            JScrollBar verticalBar = disclosureScroll.getVerticalScrollBar();
            verticalBar.addAdjustmentListener(ignored -> {
                boolean atBottom = isScrolledToBottom(verticalBar);
                acceptCheck.setEnabled(atBottom);
                scrollHint.setVisible(!atBottom);
                if (!atBottom) {
                    acceptCheck.setSelected(false);
                    acceptButton.setEnabled(false);
                }
            });
            SwingUtilities.invokeLater(() -> {
                boolean atBottom = isScrolledToBottom(verticalBar);
                acceptCheck.setEnabled(atBottom);
                scrollHint.setVisible(!atBottom);
            });
        }

        acceptButton.addActionListener(ignored -> {
            boolean newAccepted = acceptCheck.isSelected();
            saveAccepted(newAccepted);
            acceptanceUpdater.accept(newAccepted);
            acceptedState[0] = newAccepted;
            dialog.dispose();
        });
        closeButton.addActionListener(ignored -> {
            acceptedState[0] = accepted;
            dialog.dispose();
        });

        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(new EmptyBorder(12, 4, 8, 4));
        JPanel footerLeft = new JPanel();
        footerLeft.setLayout(new BoxLayout(footerLeft, BoxLayout.Y_AXIS));
        footerLeft.setOpaque(false);
        acceptCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        scrollHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        footerLeft.add(acceptCheck);
        footerLeft.add(Box.createVerticalStrut(4));
        footerLeft.add(scrollHint);
        footer.add(footerLeft, BorderLayout.WEST);
        JPanel footerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footerActions.setBorder(new EmptyBorder(6, 0, 6, 0));
        footerActions.add(acceptButton);
        footerActions.add(closeButton);
        footer.add(footerActions, BorderLayout.EAST);

        dialog.add(disclosureScroll, BorderLayout.CENTER);
        dialog.add(footer, BorderLayout.SOUTH);
        dialog.setMinimumSize(new Dimension(790, 590));
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return acceptedState[0];
    }

    private boolean isScrolledToBottom(JScrollBar bar) {
        int extent = bar.getModel().getExtent();
        int max = bar.getMaximum();
        int value = bar.getValue();
        return value + extent >= max;
    }
}

