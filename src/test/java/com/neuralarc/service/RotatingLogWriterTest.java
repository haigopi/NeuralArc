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

    @Test
    void javaLogHandlerWritesSystemTradeAndErrorLogs() throws Exception {
        RotatingJavaLogHandler handler = new RotatingJavaLogHandler(tempDir);
        java.util.logging.LogRecord tradeRecord = new java.util.logging.LogRecord(java.util.logging.Level.INFO, "trade completed");
        tradeRecord.setLoggerName("com.neuralarc.trade");
        java.util.logging.LogRecord warningRecord = new java.util.logging.LogRecord(java.util.logging.Level.WARNING, "warning message");
        warningRecord.setLoggerName("com.neuralarc.service.Test");

        handler.publish(tradeRecord);
        handler.publish(warningRecord);
        handler.close();

        String today = java.time.LocalDate.now().toString();
        assertTrue(Files.readString(tempDir.resolve("system-" + today + ".log")).contains("trade completed"));
        assertTrue(Files.readString(tempDir.resolve("trade-" + today + ".log")).contains("trade completed"));
        assertTrue(Files.readString(tempDir.resolve("error-" + today + ".log")).contains("warning message"));
    }
}
