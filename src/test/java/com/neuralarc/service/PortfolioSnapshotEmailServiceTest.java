package com.neuralarc.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioSnapshotEmailServiceTest {
    private final List<String> recipients = new ArrayList<>();
    private final List<String> subjects = new ArrayList<>();
    private final List<String> htmls = new ArrayList<>();
    private final List<String> log = new ArrayList<>();

    @Test
    void sendsToTheUserEmailTypedInSettings() {
        PortfolioSnapshotEmailService service = service("user@example.com", recording());

        service.send(PortfolioSnapshotEmailBuilderTest.snapshot(), "me@example.com", null);

        assertEquals(List.of("me@example.com"), recipients);
        assertTrue(subjects.getFirst().startsWith("NeuralArc: Live - Portfolio snapshot: Before the open"), subjects.getFirst());
        assertEquals(List.of("[EMAIL] Portfolio snapshot (Before the open) sent to me@example.com."), log);
    }

    @Test
    void scheduledSnapshotsGoToTheSavedUserEmail() {
        PortfolioSnapshotEmailService service = service("user@example.com", recording());

        service.send(PortfolioSnapshotEmailBuilderTest.snapshot(), "  ", null);

        assertEquals(List.of("user@example.com"), recipients);
    }

    @Test
    void withNoAddressAtAllNothingIsSentAndTheLogSaysWhy() {
        PortfolioSnapshotEmailService service = service("", recording());

        service.send(PortfolioSnapshotEmailBuilderTest.snapshot(), null, () -> {
            throw new AssertionError("no broker call when nothing can be sent");
        });

        assertEquals(List.of(), recipients);
        assertTrue(log.getFirst().contains("not sent: add your User Email in Settings"), log.getFirst());
    }

    @Test
    void aFailedSendIsLogged() {
        PortfolioSnapshotEmailService service = service("user@example.com", (recipient, subject, text, html) -> {
            throw new IllegalStateException("Mailjet email is not configured");
        });

        service.send(PortfolioSnapshotEmailBuilderTest.snapshot(), "", null);

        assertEquals(List.of("[EMAIL] Portfolio snapshot (Before the open) failed: Mailjet email is not configured"), log);
    }

    @Test
    void theBrokerReconciliationIsIncludedInTheEmail() {
        PortfolioSnapshotEmailService service = service("user@example.com", recording());

        service.send(PortfolioSnapshotEmailBuilderTest.snapshot(), null, () -> com.neuralarc.analytics.PortfolioSnapshot.BrokerCheck.compared(
                2, List.of(new com.neuralarc.analytics.PortfolioSnapshot.Mismatch("ORCL", "held at Alpaca (5 shares) but no NeuralArc strategy tracks it"))));

        assertTrue(htmls.getFirst().contains("Broker reconciliation (NeuralArc vs Alpaca)"));
        assertTrue(htmls.getFirst().contains("1 mismatch among 2 symbols:"));
        assertTrue(htmls.getFirst().contains("ORCL"));
    }

    @Test
    void anUnreachableBrokerStillSendsTheSnapshotAndSaysWhy() {
        PortfolioSnapshotEmailService service = service("user@example.com", recording());

        service.send(PortfolioSnapshotEmailBuilderTest.snapshot(), null, () -> {
            throw new IllegalStateException("timeout");
        });

        assertEquals(List.of("user@example.com"), recipients);
        assertTrue(htmls.getFirst().contains("Alpaca could not be reached, so positions were not compared (timeout)."));
    }

    private PortfolioSnapshotEmailService service(String userEmail, TradeEmailNotificationService.EmailSender sender) {
        PortfolioSnapshotEmailService service = new PortfolioSnapshotEmailService(() -> userEmail, sender, Runnable::run);
        service.setLog(log::add);
        return service;
    }

    private TradeEmailNotificationService.EmailSender recording() {
        return (recipient, subject, text, html) -> {
            recipients.add(recipient);
            subjects.add(subject);
            htmls.add(html);
        };
    }
}
