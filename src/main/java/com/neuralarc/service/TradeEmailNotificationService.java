package com.neuralarc.service;

import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyOrder;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TradeEmailNotificationService {
    private static final Logger LOGGER = Logger.getLogger(TradeEmailNotificationService.class.getName());
    private static final ExecutorService DEFAULT_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "neuralarc-email-notifications");
        thread.setDaemon(true);
        return thread;
    });

    private final AppSettingsService appSettingsService;
    private final EmailSender emailSender;
    private final Executor executor;
    private volatile EmailNotificationListener notificationListener = EmailNotificationListener.NOOP;

    public TradeEmailNotificationService(AppSettingsService appSettingsService) {
        this(appSettingsService, new MailjetEmailSender(), DEFAULT_EXECUTOR);
    }

    TradeEmailNotificationService(AppSettingsService appSettingsService, EmailSender emailSender, Executor executor) {
        this.appSettingsService = appSettingsService;
        this.emailSender = emailSender;
        this.executor = executor;
    }

    public void notifyBuyExpected(Strategy strategy, StrategyOrder order) {
        AppSettingsService.AppSettings settings = appSettingsService.load();
        if (!settings.emailOnBuyExpected()) {
            return;
        }
        sendAsync("BUY_EXPECTED", strategy.symbol(), settings.userEmail(), buySubject(strategy), buyText(strategy, order), buyHtml(strategy, order));
    }

    public void notifySellExecuted(Strategy strategy, StrategyOrder order) {
        AppSettingsService.AppSettings settings = appSettingsService.load();
        if (!settings.emailOnSellExecuted()) {
            return;
        }
        sendAsync("SELL_EXECUTED", strategy.symbol(), settings.userEmail(), sellSubject(strategy), sellText(strategy, order), sellHtml(strategy, order));
    }

    public void setNotificationListener(EmailNotificationListener notificationListener) {
        this.notificationListener = notificationListener == null ? EmailNotificationListener.NOOP : notificationListener;
    }

    private void sendAsync(String eventType, String symbol, String recipient, String subject, String textBody, String htmlBody) {
        if (recipient == null || recipient.isBlank()) {
            return;
        }
        executor.execute(() -> {
            try {
                emailSender.send(recipient.trim(), subject, textBody, htmlBody);
                notificationListener.onEmailSent(eventType, symbol, recipient.trim(), subject);
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Failed to send trade email notification", ex);
                notificationListener.onEmailFailed(eventType, symbol, recipient.trim(), subject, ex.getMessage());
            }
        });
    }

    private String buySubject(Strategy strategy) {
        return "NeuralArc buy order placed: " + strategy.symbol();
    }

    private String sellSubject(Strategy strategy) {
        return "NeuralArc sell order executed: " + strategy.symbol();
    }

    private String buyText(Strategy strategy, StrategyOrder order) {
        return "A buy order is placed and waiting for fill.\n\n"
                + "Symbol: " + strategy.symbol() + "\n"
                + "Stage: " + order.stage() + "\n"
                + "Quantity: " + order.requestedQuantity() + "\n"
                + "Limit price: " + order.limitPrice() + "\n"
                + "Strategy: " + strategy.name();
    }

    private String sellText(Strategy strategy, StrategyOrder order) {
        return "A sell order has executed.\n\n"
                + "Symbol: " + strategy.symbol() + "\n"
                + "Stage: " + order.stage() + "\n"
                + "Filled quantity: " + order.filledQuantity() + "\n"
                + "Average fill price: " + order.filledAveragePrice() + "\n"
                + "Strategy: " + strategy.name();
    }

    private String buyHtml(Strategy strategy, StrategyOrder order) {
        return html("A buy order is placed and waiting for fill.", strategy, order, false);
    }

    private String sellHtml(Strategy strategy, StrategyOrder order) {
        return html("A sell order has executed.", strategy, order, true);
    }

    private String html(String heading, Strategy strategy, StrategyOrder order, boolean filled) {
        return "<html><body>"
                + "<h2>" + escape(heading) + "</h2>"
                + "<p><b>Symbol:</b> " + escape(strategy.symbol()) + "</p>"
                + "<p><b>Stage:</b> " + escape(String.valueOf(order.stage())) + "</p>"
                + "<p><b>Quantity:</b> " + escape(String.valueOf(filled ? order.filledQuantity() : order.requestedQuantity())) + "</p>"
                + "<p><b>Price:</b> " + escape(String.valueOf(filled ? order.filledAveragePrice() : order.limitPrice())) + "</p>"
                + "<p><b>Strategy:</b> " + escape(strategy.name()) + "</p>"
                + "</body></html>";
    }

    private String escape(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    interface EmailSender {
        void send(String recipientEmail, String subject, String textBody, String htmlBody) throws Exception;
    }

    public interface EmailNotificationListener {
        EmailNotificationListener NOOP = new EmailNotificationListener() {};

        default void onEmailSent(String eventType, String symbol, String recipientEmail, String subject) {}

        default void onEmailFailed(String eventType, String symbol, String recipientEmail, String subject, String error) {}
    }

    private static final class MailjetEmailSender implements EmailSender {
        @Override
        public void send(String recipientEmail, String subject, String textBody, String htmlBody) throws Exception {
            FeedbackEmailService emailService = FeedbackEmailService.fromConfiguration();
            if (!emailService.isConfigured()) {
                throw new IllegalStateException("Mailjet email is not configured");
            }
            emailService.sendUserNotification(recipientEmail, subject, textBody, htmlBody);
        }
    }
}
