package com.neuralarc.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

public final class RotatingLogWriter {
    public enum LogType {
        APP("app"),
        ERROR("error"),
        SYSTEM("system"),
        TRADE("trade");

        private final String prefix;

        LogType(String prefix) {
            this.prefix = prefix;
        }
    }

    private final Path logDirectory;
    private final Clock clock;
    private final Map<LogType, StringBuilder> buffers = new EnumMap<>(LogType.class);

    public RotatingLogWriter(Path logDirectory) {
        this(logDirectory, Clock.systemDefaultZone());
    }

    RotatingLogWriter(Path logDirectory, Clock clock) {
        this.logDirectory = logDirectory;
        this.clock = clock;
        for (LogType type : LogType.values()) {
            buffers.put(type, new StringBuilder());
        }
    }

    public synchronized void append(LogType type, String logEntry) {
        if (logEntry == null || logEntry.isBlank()) {
            return;
        }
        buffers.get(type == null ? LogType.APP : type).append(logEntry);
    }

    public synchronized void flush() throws IOException {
        Files.createDirectories(logDirectory);
        for (Map.Entry<LogType, StringBuilder> entry : buffers.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            Files.writeString(
                    fileFor(entry.getKey(), LocalDate.now(clock)),
                    entry.getValue().toString(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
            entry.getValue().setLength(0);
        }
    }

    public Path fileFor(LogType type, LocalDate date) {
        return logDirectory.resolve((type == null ? LogType.APP : type).prefix + "-" + date + ".log");
    }

    public Path logDirectory() {
        return logDirectory;
    }
}
