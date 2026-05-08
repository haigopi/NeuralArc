package com.neuralarc.service;

import com.mailjet.client.ClientOptions;
import com.mailjet.client.MailjetClient;
import com.mailjet.client.MailjetRequest;
import com.mailjet.client.MailjetResponse;
import com.mailjet.client.errors.MailjetException;
import com.mailjet.client.resource.Emailv31;
import com.neuralarc.util.AppMetadata;
import com.neuralarc.util.EncryptedMailjetSecrets;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

public class FeedbackEmailService {
    private final String apiKey;
    private final String apiSecret;
    private final String fromEmail;
    private final String fromName;
    private final String toEmail;

    public FeedbackEmailService(String apiKey, String apiSecret, String fromEmail, String fromName, String toEmail) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.apiSecret = apiSecret == null ? "" : apiSecret.trim();
        this.fromEmail = fromEmail == null ? "" : fromEmail.trim();
        this.fromName = fromName == null ? "" : fromName.trim();
        this.toEmail = toEmail == null ? "" : toEmail.trim();
    }

    public static FeedbackEmailService fromConfiguration() {
        return new FeedbackEmailService(
                AppMetadata.mailjetApiKey(),
                AppMetadata.mailjetApiSecret(),
                AppMetadata.mailjetFromEmail(),
                AppMetadata.mailjetFromName(),
                AppMetadata.mailjetToEmail()
        );
    }

    public boolean isConfigured() {
        return !apiKey.isBlank() && !apiSecret.isBlank() && !fromEmail.isBlank() && !toEmail.isBlank();
    }

    public String missingConfigMessage() {
        return "Missing Mailjet configuration. Set encrypted Mailjet key/secret values and provide the Mailjet decryption passphrase with "
                + EncryptedMailjetSecrets.passphraseConfigurationHint() + ".";
    }

    public void sendSupportEmail(SupportEmailRequest supportEmailRequest) throws MailjetException {
        MailjetClient client = new MailjetClient(ClientOptions.builder()
                .apiKey(apiKey)
                .apiSecretKey(apiSecret)
                .build());

        JSONObject message = new JSONObject()
                .put("From", new JSONObject().put("Email", fromEmail).put("Name", fromName.isBlank() ? "NeuralArc Desktop" : fromName))
                .put("To", new JSONArray().put(new JSONObject().put("Email", toEmail).put("Name", "NeuralArc Support")))
                .put("Subject", supportEmailRequest.subject())
                .put("TextPart", supportEmailRequest.textBody())
                .put("HTMLPart", supportEmailRequest.htmlBody());

        if (!supportEmailRequest.customerEmail().isBlank()) {
            message.put("Cc", new JSONArray().put(new JSONObject().put("Email", supportEmailRequest.customerEmail())));
            message.put("ReplyTo", new JSONObject().put("Email", supportEmailRequest.customerEmail()));
        }
        if (!supportEmailRequest.attachments().isEmpty()) {
            JSONArray attachments = new JSONArray();
            for (SupportEmailAttachment attachment : supportEmailRequest.attachments()) {
                attachments.put(new JSONObject()
                        .put("ContentType", attachment.contentType())
                        .put("Filename", attachment.filename())
                        .put("Base64Content", attachment.base64Content()));
            }
            message.put("Attachments", attachments);
        }

        MailjetRequest request = new MailjetRequest(Emailv31.resource)
                .property(Emailv31.MESSAGES, new JSONArray().put(message));

        MailjetResponse response = client.post(request);
        if (response.getStatus() < 200 || response.getStatus() >= 300) {
            throw new MailjetException("Mailjet send failed with status " + response.getStatus());
        }
    }

    public record SupportEmailRequest(
            String subject,
            String textBody,
            String htmlBody,
            String customerEmail,
            List<SupportEmailAttachment> attachments
    ) {
        public SupportEmailRequest(String subject, String textBody, String htmlBody, String customerEmail) {
            this(subject, textBody, htmlBody, customerEmail, List.of());
        }

        public SupportEmailRequest {
            subject = subject == null ? "" : subject.trim();
            textBody = textBody == null ? "" : textBody.trim();
            htmlBody = htmlBody == null ? "" : htmlBody.trim();
            customerEmail = customerEmail == null ? "" : customerEmail.trim();
            attachments = attachments == null ? List.of() : List.copyOf(attachments);
        }
    }

    public record SupportEmailAttachment(String filename, String contentType, String base64Content) {
        public static SupportEmailAttachment textFile(String filename, String content) {
            String encoded = Base64.getEncoder().encodeToString((content == null ? "" : content)
                    .getBytes(StandardCharsets.UTF_8));
            return new SupportEmailAttachment(filename, "text/plain", encoded);
        }

        public SupportEmailAttachment {
            filename = filename == null ? "attachment.txt" : filename.trim();
            contentType = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType.trim();
            base64Content = base64Content == null ? "" : base64Content.trim();
        }
    }
}
