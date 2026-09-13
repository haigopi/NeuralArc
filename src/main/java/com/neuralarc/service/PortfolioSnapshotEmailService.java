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
 * Emails a {@link PortfolioSnapshot} in the background through Mailjet. It goes to the address set
 * for snapshot emails, or else to the user's own email from Settings; with neither, nothing is sent
 * and the log says why. Every outcome is reported through the log callback.
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

    /** Sends in the background to {@code preferredRecipient}, or to the user's email when that is blank. */
    public void send(PortfolioSnapshot snapshot, String preferredRecipient) {
        if (snapshot == null) {
            return;
        }
        executor.execute(() -> {
            String occasion = snapshot.occasion();
            String recipient = resolveRecipient(preferredRecipient);
            if (recipient.isBlank()) {
                log.accept("[EMAIL] Portfolio snapshot (" + occasion + ") not sent: set an email address in Settings.");
                return;
            }
            try {
                sender.send(recipient, builder.subject(snapshot), builder.text(snapshot), builder.html(snapshot));
                log.accept("[EMAIL] Portfolio snapshot (" + occasion + ") sent to " + recipient + ".");
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Failed to send portfolio snapshot email", ex);
                log.accept("[EMAIL] Portfolio snapshot (" + occasion + ") failed: " + ex.getMessage());
            }
        });
    }

    String resolveRecipient(String preferredRecipient) {
        if (preferredRecipient != null && !preferredRecipient.isBlank()) {
            return preferredRecipient.trim();
        }
        String fallback = userEmail.get();
        return fallback == null ? "" : fallback.trim();
    }
}
