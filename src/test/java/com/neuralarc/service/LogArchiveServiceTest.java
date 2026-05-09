package com.neuralarc.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LogArchiveServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void discoversPreviousLogDatesAndCreatesZipArchive() throws Exception {
        Path logs = tempDir.resolve("logs");
        Path archives = tempDir.resolve("archives");
        Files.createDirectories(logs);
        Files.writeString(logs.resolve("app-2026-05-08.log"), "app");
        Files.writeString(logs.resolve("error-2026-05-08.log"), "error");
        Files.writeString(logs.resolve("trade-2026-05-09.log"), "trade");

        LogArchiveService service = new LogArchiveService(logs, archives);

        assertEquals(List.of(LocalDate.parse("2026-05-08")), service.discoverArchivedLogDates(LocalDate.parse("2026-05-09")));
        Path archive = service.archive(LocalDate.parse("2026-05-08"));

        try (ZipFile zip = new ZipFile(archive.toFile())) {
            assertNotNull(zip.getEntry("app-2026-05-08.log"));
            assertNotNull(zip.getEntry("error-2026-05-08.log"));
        }
    }
}
