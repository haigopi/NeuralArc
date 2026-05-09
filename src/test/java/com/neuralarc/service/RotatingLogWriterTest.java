package com.neuralarc.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RotatingLogWriterTest {
    @TempDir
    Path tempDir;

    @Test
    void flushWritesDatedLogFilesWithoutOverwritingPreviousDays() throws Exception {
        RotatingLogWriter firstDay = new RotatingLogWriter(
                tempDir,
                Clock.fixed(Instant.parse("2026-05-08T12:00:00Z"), ZoneId.of("UTC"))
        );
        firstDay.append(RotatingLogWriter.LogType.APP, "one\n");
        firstDay.flush();

        RotatingLogWriter secondDay = new RotatingLogWriter(
                tempDir,
                Clock.fixed(Instant.parse("2026-05-09T12:00:00Z"), ZoneId.of("UTC"))
        );
        secondDay.append(RotatingLogWriter.LogType.APP, "two\n");
        secondDay.flush();

        assertEquals("one\n", Files.readString(tempDir.resolve("app-2026-05-08.log")));
        assertEquals("two\n", Files.readString(tempDir.resolve("app-2026-05-09.log")));
        assertTrue(Files.exists(tempDir.resolve("app-2026-05-08.log")));
    }
}
