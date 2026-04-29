package com.neuralarc.ui;

import com.mailjet.client.errors.MailjetException;
import com.neuralarc.service.ApiRequestIdStore;
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

public class ContactUsDialog extends JDialog {
    private final FeedbackEmailService emailService;
    private final ApiRequestIdStore apiRequestIdStore = new ApiRequestIdStore();
    private final String customerEmail;
    private final JTextField fullNameField = SupportDialogStyles.createTextField(28);
    private final JTextField phoneField = SupportDialogStyles.createTextField(18);
    private final JTextArea messageArea = SupportDialogStyles.createTextArea(8, 34);
    private final JButton sendButton = new JButton("Send Message");
    private boolean sent;

    public ContactUsDialog(JFrame owner, String customerEmail, FeedbackEmailService emailService) {
        super(owner, "Contact Us", true);
        this.customerEmail = customerEmail == null ? "" : customerEmail.trim();
        this.emailService = emailService;

        setLayout(new BorderLayout(12, 12));
        ((JComponent) getContentPane()).setBorder(new EmptyBorder(18, 20, 16, 20));

        add(SupportDialogStyles.createHeroPanel(
                "Contact the NeuralArc Team",
                "Use this form for support, product questions, or partnerships."
        ), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        DialogButtonStyles.apply(sendButton, "icons/send.svg");
        SupportDialogStyles.applyDialogTheme(getContentPane());

        setPreferredSize(new Dimension(700, 730));
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

        JPanel detailsSection = SupportDialogStyles.createSectionPanel("Contact Details");
        detailsSection.add(buildDetailsForm(), BorderLayout.CENTER);

        JPanel messageSection = SupportDialogStyles.createSectionPanel("Message");
        messageSection.add(buildMessageForm(), BorderLayout.CENTER);

        content.add(detailsSection);
        content.add(Box.createVerticalStrut(12));
        content.add(messageSection);

        return content;
    }

    private JComponent buildDetailsForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);

        GridBagConstraints gbc = baseConstraints();
        addFormRow(form, gbc, 0, "Customer email", SupportDialogStyles.createReadOnlyField(customerEmail));
        addFormRow(form, gbc, 1, "Full name *", fullNameField);
        addFormRow(form, gbc, 2, "Phone number", phoneField);
        return form;
    }

    private JComponent buildMessageForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);

        GridBagConstraints gbc = baseConstraints();
        addFormRow(form, gbc, 0, "Message *", SupportDialogStyles.wrapTextArea(messageArea, 210));
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
        String fullName = fullNameField.getText().trim();
        String phone = phoneField.getText().trim();
        String message = messageArea.getText().trim();

        if (customerEmail.isBlank()) {
            JOptionPane.showMessageDialog(this,
                    "Please save the customer email in Settings before sending a contact request.",
                    "Missing Settings Email",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (fullName.isBlank() || message.isBlank()) {
            JOptionPane.showMessageDialog(this,
                    "Please complete the full name and message fields.",
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
                String mailSubject = "NeuralArc - Contact Us";
                String textBody = "Category: Contact Us\n"
                        + "Customer Email: " + customerEmail + "\n"
                        + "Full Name: " + fullName + "\n"
                        + "Phone: " + (phone.isBlank() ? "-" : phone) + "\n\n"
                        + "Message:\n" + message;
                String htmlBody = "<h3>Contact Us</h3>"
                        + "<p><b>Customer Email:</b> " + escape(customerEmail) + "<br/>"
                        + "<b>Full Name:</b> " + escape(fullName) + "<br/>"
                        + "<b>Phone:</b> " + escape(phone.isBlank() ? "-" : phone) + "</p>"
                        + "<p><b>Message</b><br/>" + htmlMultiline(message) + "</p>";
                FeedbackEmailService.SupportEmailAttachment requestIds = FeedbackEmailService.SupportEmailAttachment.textFile(
                        "alpaca-request-ids.txt",
                        apiRequestIdStore.buildRecentReport()
                );
                emailService.sendSupportEmail(new FeedbackEmailService.SupportEmailRequest(
                        mailSubject, textBody, htmlBody, customerEmail, java.util.List.of(requestIds)
                ));
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    sent = true;
                    JOptionPane.showMessageDialog(ContactUsDialog.this,
                            "Message sent successfully.",
                            "Sent",
                            JOptionPane.INFORMATION_MESSAGE);
                    setVisible(false);
                } catch (Exception ex) {
                    String detail = ex.getCause() instanceof MailjetException
                            ? ex.getCause().getMessage()
                            : ex.getMessage();
                    JOptionPane.showMessageDialog(ContactUsDialog.this,
                            "Unable to send your message.\n" + detail,
                            "Send Failed",
                            JOptionPane.ERROR_MESSAGE);
                } finally {
                    setSendingState(false, "Send Message");
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
