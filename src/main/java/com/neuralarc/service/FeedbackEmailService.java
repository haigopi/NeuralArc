package com.neuralarc.service;

import com.mailjet.client.ClientOptions;
import com.mailjet.client.MailjetClient;
import com.mailjet.client.MailjetRequest;
import com.mailjet.client.MailjetResponse;
import com.mailjet.client.errors.MailjetException;
import com.mailjet.client.resource.Emailv31;
import org.json.JSONArray;
import org.json.JSONObject;

public class FeedbackEmailService {
    private final String apiKey;
    private final String apiSecret;
    private final String fromEmail;
    private final String toEmail;

    public FeedbackEmailService(String apiKey, String apiSecret, String fromEmail, String toEmail) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.apiSecret = apiSecret == null ? "" : apiSecret.trim();
        this.fromEmail = fromEmail == null ? "" : fromEmail.trim();
        this.toEmail = toEmail == null ? "" : toEmail.trim();
    }

    public static FeedbackEmailService fromEnvironment() {
        return new FeedbackEmailService(
                System.getenv("MAILJET_API_KEY"),
                System.getenv("MAILJET_API_SECRET"),
                System.getenv("MAILJET_FROM_EMAIL"),
                System.getenv("MAILJET_TO_EMAIL")
        );
    }

    public boolean isConfigured() {
        return !apiKey.isBlank() && !apiSecret.isBlank() && !fromEmail.isBlank() && !toEmail.isBlank();
    }

    public String missingConfigMessage() {
        return "Missing Mailjet configuration. Set MAILJET_API_KEY, MAILJET_API_SECRET, MAILJET_FROM_EMAIL, MAILJET_TO_EMAIL.";
    }

    public void sendFeedback(String subjectPrefix, String phoneNumber, String description, String userEmail) throws MailjetException {
        MailjetClient client = new MailjetClient(ClientOptions.builder()
                .apiKey(apiKey)
                .apiSecretKey(apiSecret)
                .build());

        String subject = "NeuralArc - " + subjectPrefix;
        String textBody = "Category: " + subjectPrefix + "\n"
                + "Phone: " + phoneNumber + "\n"
                + "User Email: " + (userEmail == null ? "" : userEmail) + "\n\n"
                + "Description:\n" + description;

        JSONObject message = new JSONObject()
                .put(Emailv31.Message.FROM, new JSONObject().put("Email", fromEmail).put("Name", "NeuralArc App"))
                .put(Emailv31.Message.TO, new JSONArray().put(new JSONObject().put("Email", toEmail)))
                .put(Emailv31.Message.SUBJECT, subject)
                .put(Emailv31.Message.TEXTPART, textBody);

        MailjetRequest request = new MailjetRequest(Emailv31.resource)
                .property(Emailv31.MESSAGES, new JSONArray().put(message));

        MailjetResponse response = client.post(request);
        if (response.getStatus() < 200 || response.getStatus() >= 300) {
            throw new MailjetException("Mailjet send failed with status " + response.getStatus());
        }
    }
}
