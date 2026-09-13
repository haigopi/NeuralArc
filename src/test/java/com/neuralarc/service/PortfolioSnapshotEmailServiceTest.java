package com.neuralarc.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioSnapshotEmailServiceTest {
    private final List<String> recipients = new ArrayList<>();
    private final List<String> subjects = new ArrayList<>();
    private final List<String> log = new ArrayList<>();

    @Test
    void sendsToTheChosenAddress() {
        PortfolioSnapshotEmailService service = service("user@example.com", recording());

        service.send(PortfolioSnapshotEmailBuilderTest.snapshot(), "me@example.com");

        assertEquals(List.of("me@example.com"), recipients);
        assertTrue(subjects.getFirst().startsWith("NeuralArc: Live - Portfolio snapshot: Before the open"), subjects.getFirst());
        assertEquals(List.of("[EMAIL] Portfolio snapshot (Before the open) sent to me@example.com."), log);
    }

    @Test
    void aBlankAddressMeansTheUsersOwnEmail() {
        PortfolioSnapshotEmailService service = service("user@example.com", recording());

        service.send(PortfolioSnapshotEmailBuilderTest.snapshot(), "  ");

        assertEquals(List.of("user@example.com"), recipients);
    }

    @Test
    void withNoAddressAtAllNothingIsSentAndTheLogSaysWhy() {
        PortfolioSnapshotEmailService service = service("", recording());

        service.send(PortfolioSnapshotEmailBuilderTest.snapshot(), null);

        assertEquals(List.of(), recipients);
        assertTrue(log.getFirst().contains("not sent: set an email address in Settings"), log.getFirst());
    }

    @Test
    void aFailedSendIsLogged() {
        PortfolioSnapshotEmailService service = service("user@example.com", (recipient, subject, text, html) -> {
            throw new IllegalStateException("Mailjet email is not configured");
        });

        service.send(PortfolioSnapshotEmailBuilderTest.snapshot(), "");

        assertEquals(List.of("[EMAIL] Portfolio snapshot (Before the open) failed: Mailjet email is not configured"), log);
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
        };
    }
}
