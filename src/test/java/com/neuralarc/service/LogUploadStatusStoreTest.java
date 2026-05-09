package com.neuralarc.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogUploadStatusStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void persistsUploadStatusForRestartRecovery() {
        Path file = tempDir.resolve("status.json");
        LocalDate date = LocalDate.parse("2026-05-08");
        LogUploadStatusStore store = new LogUploadStatusStore(file);

        store.save(new LogUploadStatus(
                date,
                "logs-2026-05-08.zip",
                LogUploadStatus.UploadState.UPLOADED,
                Instant.parse("2026-05-09T01:00:00Z"),
                1,
                "",
                "user/logs/2026-05-08/logs-2026-05-08.zip"
        ));

        LogUploadStatus loaded = new LogUploadStatusStore(file).find(date).orElseThrow();

        assertTrue(new LogUploadStatusStore(file).isUploaded(date));
        assertEquals("logs-2026-05-08.zip", loaded.archiveFileName());
        assertEquals(LogUploadStatus.UploadState.UPLOADED, loaded.uploadStatus());
        assertEquals(1, loaded.retryCount());
    }
}
