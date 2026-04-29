package com.neuralarc.ui;

import com.mailjet.client.errors.MailjetException;
import com.neuralarc.service.FeedbackEmailService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

public class RequestNewFeatureDialog extends JDialog {
    private final FeedbackEmailService emailService;
    private final String customerEmail;
    private final JTextField featureTitleField = SupportDialogStyles.createTextField(30);
    private final JTextField phoneField = SupportDialogStyles.createTextField(18);
    private final JTextArea useCaseArea = SupportDialogStyles.createTextArea(5, 34);
    private final JTextArea outcomeArea = SupportDialogStyles.createTextArea(5, 34);
    private final JButton sendButton = new JButton("Send Feature Request");
    private boolean sent;

    public RequestNewFeatureDialog(JFrame owner, String customerEmail, FeedbackEmailService emailService) {
        super(owner, "Request New Feature", true);
        this.customerEmail = customerEmail == null ? "" : customerEmail.trim();
        this.emailService = emailService;

        setLayout(new BorderLayout(12, 12));
        ((JComponent) getContentPane()).setBorder(new EmptyBorder(18, 20, 16, 20));

        add(SupportDialogStyles.createHeroPanel(
                "Request a New Feature",
                "Share the feature, the use case, and the expected outcome."
        ), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        DialogButtonStyles.apply(sendButton, "icons/send.svg");
        SupportDialogStyles.applyDialogTheme(getContentPane());

        setPreferredSize(new Dimension(700, 690));
        pack();
        setLocationRelativeTo(owner);
    }

    public boolean showDialog() {
        sent = false;
        setVisible(true);
        return sent;
    }

    private JComponent buildBody() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);

        JPanel requesterSection = SupportDialogStyles.createSectionPanel("Requester Details");
        requesterSection.add(buildRequesterForm(), BorderLayout.CENTER);

        JPanel requestSection = SupportDialogStyles.createSectionPanel("Feature Request");
        requestSection.add(buildRequestForm(), BorderLayout.CENTER);

        content.add(requesterSection);
        content.add(Box.createVerticalStrut(12));
        content.add(requestSection);

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getViewport().setBackground(SupportDialogStyles.DIALOG_BG);
        return scrollPane;
    }

    private JComponent buildRequesterForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);

        GridBagConstraints gbc = baseConstraints();
        addFormRow(form, gbc, 0, "Customer email", SupportDialogStyles.createReadOnlyField(customerEmail));
        addFormRow(form, gbc, 1, "Phone number", phoneField);
        return form;
    }

    private JComponent buildRequestForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);

        GridBagConstraints gbc = baseConstraints();
        addFormRow(form, gbc, 0, "Feature title *", featureTitleField);
        addFormRow(form, gbc, 1, "Problem / use case *", SupportDialogStyles.wrapTextArea(useCaseArea, 120));
        addFormRow(form, gbc, 2, "Desired outcome *", SupportDialogStyles.wrapTextArea(outcomeArea, 120));
        return form;
    }

    private JComponent buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);

        JButton helpFaq = new JButton("Help & FAQ");
        JButton cancel = new JButton("Cancel");
        DialogButtonStyles.apply(helpFaq, "icons/faqs.svg");
        DialogButtonStyles.apply(cancel, "icons/close.svg");

        helpFaq.addActionListener(e -> new HelpDialog((JFrame) getOwner()).setVisible(true));
        cancel.addActionListener(e -> {
            sent = false;
            setVisible(false);
        });
        sendButton.addActionListener(e -> onSend());

        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightActions.setOpaque(false);
        rightActions.add(cancel);
        rightActions.add(sendButton);

        footer.add(helpFaq, BorderLayout.WEST);
        footer.add(rightActions, BorderLayout.EAST);
        return footer;
    }

    private void onSend() {
        String featureTitle = featureTitleField.getText().trim();
        String useCase = useCaseArea.getText().trim();
        String desiredOutcome = outcomeArea.getText().trim();
        String phone = phoneField.getText().trim();

        if (customerEmail.isBlank()) {
            JOptionPane.showMessageDialog(this,
                    "Please save the customer email in Settings before sending a feature request.",
                    "Missing Settings Email",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (featureTitle.isBlank() || useCase.isBlank() || desiredOutcome.isBlank()) {
            JOptionPane.showMessageDialog(this,
                    "Please complete the feature title, problem / use case, and desired outcome fields.",
                    "Missing Required Fields",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (emailService == null || !emailService.isConfigured()) {
            JOptionPane.showMessageDialog(this,
                    emailService == null ? "Mail service is not available." : emailService.missingConfigMessage(),
                    "Mailjet Not Configured",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        setSendingState(true, "Sending...");
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                String subject = "NeuralArc - Request New Feature";
                String textBody = "Category: Request New Feature\n"
                        + "Customer Email: " + customerEmail + "\n"
                        + "Phone: " + (phone.isBlank() ? "-" : phone) + "\n"
                        + "Feature Title: " + featureTitle + "\n\n"
                        + "Problem / Use Case:\n" + useCase + "\n\n"
                        + "Desired Outcome:\n" + desiredOutcome;
                String htmlBody = "<h3>Request New Feature</h3>"
                        + "<p><b>Customer Email:</b> " + escape(customerEmail) + "<br/>"
                        + "<b>Phone:</b> " + escape(phone.isBlank() ? "-" : phone) + "<br/>"
                        + "<b>Feature Title:</b> " + escape(featureTitle) + "</p>"
                        + "<p><b>Problem / Use Case</b><br/>" + htmlMultiline(useCase) + "</p>"
                        + "<p><b>Desired Outcome</b><br/>" + htmlMultiline(desiredOutcome) + "</p>";
                emailService.sendSupportEmail(new FeedbackEmailService.SupportEmailRequest(
                        subject, textBody, htmlBody, customerEmail
                ));
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    sent = true;
                    JOptionPane.showMessageDialog(RequestNewFeatureDialog.this,
                            "Feature request sent successfully.",
                            "Sent",
                            JOptionPane.INFORMATION_MESSAGE);
                    setVisible(false);
                } catch (Exception ex) {
                    String message = ex.getCause() instanceof MailjetException
                            ? ex.getCause().getMessage()
                            : ex.getMessage();
                    JOptionPane.showMessageDialog(RequestNewFeatureDialog.this,
                            "Unable to send the feature request.\n" + message,
                            "Send Failed",
                            JOptionPane.ERROR_MESSAGE);
                } finally {
                    setSendingState(false, "Send Feature Request");
                }
            }
        };
        worker.execute();
    }

    private void setSendingState(boolean sending, String buttonText) {
        sendButton.setEnabled(!sending);
        sendButton.setText(buttonText);
        setCursor(sending ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
    }

    private GridBagConstraints baseConstraints() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(0, 0, 10, 0);
        return gbc;
    }

    private void addFormRow(JPanel form, GridBagConstraints gbc, int row, String label, JComponent component) {
        gbc.gridy = row * 2;
        form.add(SupportDialogStyles.createFieldLabel(label), gbc);
        gbc.gridy = row * 2 + 1;
        form.add(component, gbc);
    }

    private static String escape(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String htmlMultiline(String value) {
        return escape(value).replace("\n", "<br/>");
    }
}
