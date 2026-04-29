package com.neuralarc.ui;

import com.mailjet.client.errors.MailjetException;
import com.neuralarc.service.ApiRequestIdStore;
import com.neuralarc.service.DailyLogBundleService;
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
import java.util.List;

public class SubmitBugDialog extends JDialog {
    private final FeedbackEmailService emailService;
    private final DailyLogBundleService dailyLogBundleService;
    private final ApiRequestIdStore apiRequestIdStore;
    private final String customerEmail;
    private final JTextField fullNameField = SupportDialogStyles.createTextField(28);
    private final JTextField phoneField = SupportDialogStyles.createTextField(18);
    private final JTextArea stepsArea = SupportDialogStyles.createTextArea(5, 34);
    private final JTextArea expectedArea = SupportDialogStyles.createTextArea(5, 34);
    private final JTextArea actualArea = SupportDialogStyles.createTextArea(5, 34);
    private final JButton sendButton = new JButton("Submit Bug");
    private boolean sent;

    public SubmitBugDialog(JFrame owner, String customerEmail, FeedbackEmailService emailService) {
        this(owner, customerEmail, emailService, new DailyLogBundleService(), new ApiRequestIdStore());
    }

    SubmitBugDialog(JFrame owner, String customerEmail, FeedbackEmailService emailService,
                    DailyLogBundleService dailyLogBundleService, ApiRequestIdStore apiRequestIdStore) {
        super(owner, "Submit Bug", true);
        this.customerEmail = customerEmail == null ? "" : customerEmail.trim();
        this.emailService = emailService;
        this.dailyLogBundleService = dailyLogBundleService;
        this.apiRequestIdStore = apiRequestIdStore;

        setLayout(new BorderLayout(12, 12));
        ((JComponent) getContentPane()).setBorder(new EmptyBorder(18, 20, 16, 20));

        add(SupportDialogStyles.createHeroPanel(
                "Submit a Bug Report",
                "Describe the issue. Today's logs will be attached automatically."
        ), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        DialogButtonStyles.apply(sendButton, "icons/submit-bug.svg");
        SupportDialogStyles.applyDialogTheme(getContentPane());

        setPreferredSize(new Dimension(720, 820));
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

        JPanel detailsSection = SupportDialogStyles.createSectionPanel("Reporter Details");
        detailsSection.add(buildDetailsForm(), BorderLayout.CENTER);

        JPanel reportSection = SupportDialogStyles.createSectionPanel("Bug Details");
        reportSection.add(buildReportForm(), BorderLayout.CENTER);

        content.add(detailsSection);
        content.add(Box.createVerticalStrut(12));
        content.add(reportSection);

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getViewport().setBackground(SupportDialogStyles.DIALOG_BG);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        return scrollPane;
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

    private JComponent buildReportForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);

        GridBagConstraints gbc = baseConstraints();
        addFormRow(form, gbc, 0, "Steps to reproduce *", SupportDialogStyles.wrapTextArea(stepsArea, 120));
        addFormRow(form, gbc, 1, "Expected behavior", SupportDialogStyles.wrapTextArea(expectedArea, 110));
        addFormRow(form, gbc, 2, "Actual behavior *", SupportDialogStyles.wrapTextArea(actualArea, 130));
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
        String steps = stepsArea.getText().trim();
        String expected = expectedArea.getText().trim();
        String actual = actualArea.getText().trim();

        if (customerEmail.isBlank()) {
            JOptionPane.showMessageDialog(this,
                    "Please save the customer email in Settings before sending a bug report.",
                    "Missing Settings Email",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (fullName.isBlank() || steps.isBlank() || actual.isBlank()) {
            JOptionPane.showMessageDialog(this,
                    "Please complete full name, steps to reproduce, and actual behavior.",
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
                String mailSubject = "NeuralArc - Bug Report";
                String textBody = "Category: Bug Report\n"
                        + "Customer Email: " + customerEmail + "\n"
                        + "Full Name: " + fullName + "\n"
                        + "Phone: " + (phone.isBlank() ? "-" : phone) + "\n\n"
                        + "Steps to Reproduce:\n" + steps + "\n\n"
                        + "Expected Behavior:\n" + (expected.isBlank() ? "-" : expected) + "\n\n"
                        + "Actual Behavior:\n" + actual + "\n\n"
                        + "Attachment: daily logs for current day";
                String htmlBody = "<h3>Bug Report</h3>"
                        + "<p><b>Customer Email:</b> " + escape(customerEmail) + "<br/>"
                        + "<b>Full Name:</b> " + escape(fullName) + "<br/>"
                        + "<b>Phone:</b> " + escape(phone.isBlank() ? "-" : phone) + "</p>"
                        + "<p><b>Steps to Reproduce</b><br/>" + htmlMultiline(steps) + "</p>"
                        + "<p><b>Expected Behavior</b><br/>" + htmlMultiline(expected.isBlank() ? "-" : expected) + "</p>"
                        + "<p><b>Actual Behavior</b><br/>" + htmlMultiline(actual) + "</p>"
                        + "<p><i>Daily system logs are attached.</i></p>";

                FeedbackEmailService.SupportEmailAttachment dailyLogs = dailyLogBundleService.buildTodayLogAttachment();
                FeedbackEmailService.SupportEmailAttachment requestIds = FeedbackEmailService.SupportEmailAttachment.textFile(
                        "alpaca-request-ids.txt",
                        apiRequestIdStore.buildRecentReport()
                );
                emailService.sendSupportEmail(new FeedbackEmailService.SupportEmailRequest(
                        mailSubject,
                        textBody,
                        htmlBody,
                        customerEmail,
                        List.of(dailyLogs, requestIds)
                ));
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    sent = true;
                    JOptionPane.showMessageDialog(SubmitBugDialog.this,
                            "Bug report sent successfully.",
                            "Sent",
                            JOptionPane.INFORMATION_MESSAGE);
                    setVisible(false);
                } catch (Exception ex) {
                    String detail = ex.getCause() instanceof MailjetException
                            ? ex.getCause().getMessage()
                            : ex.getMessage();
                    JOptionPane.showMessageDialog(SubmitBugDialog.this,
                            "Unable to send your bug report.\n" + detail,
                            "Send Failed",
                            JOptionPane.ERROR_MESSAGE);
                } finally {
                    setSendingState(false, "Submit Bug");
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
