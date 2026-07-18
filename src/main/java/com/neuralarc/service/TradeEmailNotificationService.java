package com.neuralarc.service;

import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyOrder;
import com.neuralarc.util.AppMetadata;

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
    private final String automatedRecipientEmail;
    private final TradeEmailNotificationContextProvider contextProvider;
    private final TradeEmailContentBuilder contentBuilder = new TradeEmailContentBuilder();
    private volatile EmailNotificationListener notificationListener = EmailNotificationListener.NOOP;

    public TradeEmailNotificationService(AppSettingsService appSettingsService) {
        this(appSettingsService, new MailjetEmailSender(), DEFAULT_EXECUTOR, AppMetadata.mailjetToEmail());
    }

    TradeEmailNotificationService(
            AppSettingsService appSettingsService,
            StrategyRepository strategyRepository,
            StrategyOrderRepository orderRepository,
            WorkspaceRepository workspaceRepository
    ) {
        this(
                appSettingsService,
                new MailjetEmailSender(),
                DEFAULT_EXECUTOR,
                AppMetadata.mailjetToEmail(),
                new RepositoryTradeEmailNotificationContextProvider(strategyRepository, orderRepository, workspaceRepository)
        );
    }

    TradeEmailNotificationService(AppSettingsService appSettingsService, EmailSender emailSender, Executor executor) {
        this(appSettingsService, emailSender, executor, AppMetadata.mailjetToEmail());
    }

    TradeEmailNotificationService(
            AppSettingsService appSettingsService,
            EmailSender emailSender,
            Executor executor,
            String automatedRecipientEmail
    ) {
        this(appSettingsService, emailSender, executor, automatedRecipientEmail, TradeEmailNotificationContextProvider.empty());
    }

    TradeEmailNotificationService(
            AppSettingsService appSettingsService,
            EmailSender emailSender,
            Executor executor,
            String automatedRecipientEmail,
            TradeEmailNotificationContextProvider contextProvider
    ) {
        this.appSettingsService = appSettingsService;
        this.emailSender = emailSender;
        this.executor = executor;
        this.automatedRecipientEmail = automatedRecipientEmail == null ? "" : automatedRecipientEmail.trim();
        this.contextProvider = contextProvider == null ? TradeEmailNotificationContextProvider.empty() : contextProvider;
    }

    public void notifyBuyExpected(Strategy strategy, StrategyOrder order) {
        if (!isLiveStrategy(strategy)) {
            return;
        }
        AppSettingsService.AppSettings settings = appSettingsService.load();
        if (!settings.emailOnBuyExpected()) {
            return;
        }
        TradeEmailNotificationContext context = contextProvider.contextFor(strategy, order);
        sendAsync(
                "BUY_EXPECTED",
                strategy.symbol(),
                automatedRecipientEmail,
                contentBuilder.buySubject(strategy),
                contentBuilder.buyText(strategy, order, context),
                contentBuilder.buyHtml(strategy, order, context)
        );
    }

    public void notifySellExecuted(Strategy strategy, StrategyOrder order) {
        if (!isLiveStrategy(strategy)) {
            return;
        }
        AppSettingsService.AppSettings settings = appSettingsService.load();
        if (!settings.emailOnSellExecuted()) {
            return;
        }
        TradeEmailNotificationContext context = contextProvider.contextFor(strategy, order);
        sendAsync(
                "SELL_EXECUTED",
                strategy.symbol(),
                automatedRecipientEmail,
                contentBuilder.sellSubject(strategy),
                contentBuilder.sellText(strategy, order, context),
                contentBuilder.sellHtml(strategy, order, context)
        );
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

    private boolean isLiveStrategy(Strategy strategy) {
        return strategy != null && strategy.mode() == StrategyMode.LIVE;
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
