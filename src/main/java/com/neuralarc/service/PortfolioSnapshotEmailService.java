package com.neuralarc.service;

import com.neuralarc.analytics.PortfolioSnapshot;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Emails a {@link PortfolioSnapshot} in the background through Mailjet, to the user's email from
 * Settings like every NeuralArc email. Just before sending it compares NeuralArc's positions with
 * Alpaca's, which is broker I/O and so never runs on the EDT. Every outcome goes to the log callback.
 */
public class PortfolioSnapshotEmailService {
    private static final Logger LOGGER = Logger.getLogger(PortfolioSnapshotEmailService.class.getName());
    private static final ExecutorService DEFAULT_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "neuralarc-portfolio-email");
        thread.setDaemon(true);
        return thread;
    });

    private final Supplier<String> userEmail;
    private final TradeEmailNotificationService.EmailSender sender;
    private final Executor executor;
    private final PortfolioSnapshotEmailBuilder builder = new PortfolioSnapshotEmailBuilder();
    private volatile Consumer<String> log = ignored -> { };

    public PortfolioSnapshotEmailService(AppSettingsService appSettingsService) {
        this(() -> appSettingsService.load().userEmail(), new TradeEmailNotificationService.MailjetEmailSender(), DEFAULT_EXECUTOR);
    }

    PortfolioSnapshotEmailService(Supplier<String> userEmail, TradeEmailNotificationService.EmailSender sender, Executor executor) {
        this.userEmail = userEmail == null ? () -> "" : userEmail;
        this.sender = sender;
        this.executor = executor;
    }

    public void setLog(Consumer<String> log) {
        this.log = log == null ? ignored -> { } : log;
    }

    /**
     * Sends in the background. {@code typedEmail} is the User Email as typed in Settings when sending
     * from there, so an address not yet saved still works; otherwise the saved User Email is used.
     * {@code brokerCheck} compares positions with Alpaca and runs on the background thread.
     */
    public void send(PortfolioSnapshot snapshot, String typedEmail, Supplier<PortfolioSnapshot.BrokerCheck> brokerCheck) {
        if (snapshot == null) {
            return;
        }
        executor.execute(() -> {
            String occasion = snapshot.occasion();
            String recipient = resolveRecipient(typedEmail);
            if (recipient.isBlank()) {
                log.accept("[EMAIL] Portfolio snapshot (" + occasion + ") not sent: add your User Email in Settings.");
                return;
            }
            PortfolioSnapshot complete = snapshot.withBrokerCheck(checkBroker(brokerCheck));
            try {
                sender.send(recipient, builder.subject(complete), builder.text(complete), builder.html(complete));
                log.accept("[EMAIL] Portfolio snapshot (" + occasion + ") sent to " + recipient + ".");
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Failed to send portfolio snapshot email", ex);
                log.accept("[EMAIL] Portfolio snapshot (" + occasion + ") failed: " + ex.getMessage());
            }
        });
    }

    String resolveRecipient(String typedEmail) {
        if (typedEmail != null && !typedEmail.isBlank()) {
            return typedEmail.trim();
        }
        String saved = userEmail.get();
        return saved == null ? "" : saved.trim();
    }

    private PortfolioSnapshot.BrokerCheck checkBroker(Supplier<PortfolioSnapshot.BrokerCheck> brokerCheck) {
        if (brokerCheck == null) {
            return null;
        }
        try {
            return brokerCheck.get();
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Broker reconciliation for the portfolio snapshot failed", ex);
            return PortfolioSnapshot.BrokerCheck.unavailable(
                    "Alpaca could not be reached, so positions were not compared (" + ex.getMessage() + ").");
        }
    }
}
