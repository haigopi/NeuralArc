package com.neuralarc.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyLogBundleServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void includesIsoDateEntriesAndSkipsOtherDates() throws Exception {
        Files.writeString(tempDir.resolve("broker.log"),
                "INFO 2026-04-29 first\n"
                        + "INFO 2026-04-28 old\n");

        DailyLogBundleService service = new DailyLogBundleService(
                tempDir,
                fixedClock("2026-04-29T10:00:00Z")
        );

        FeedbackEmailService.SupportEmailAttachment attachment = service.buildTodayLogAttachment();
        String decoded = new String(Base64.getDecoder().decode(attachment.base64Content()));

        assertTrue(decoded.contains("INFO 2026-04-29 first"));
        assertTrue(!decoded.contains("INFO 2026-04-28 old"));
    }

    @Test
    void includesAppLogWhenFileModifiedToday() throws Exception {
        Path appLog = tempDir.resolve("app.log");
        Files.writeString(appLog, "[Apr 29th - 10:05 AM] UI started\n");

        DailyLogBundleService service = new DailyLogBundleService(
                tempDir,
                fixedClock("2026-04-29T10:00:00Z")
        );

        FeedbackEmailService.SupportEmailAttachment attachment = service.buildTodayLogAttachment();
        String decoded = new String(Base64.getDecoder().decode(attachment.base64Content()));

        assertTrue(decoded.contains("UI started"));
    }

    private Clock fixedClock(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneId.of("UTC"));
    }
}

