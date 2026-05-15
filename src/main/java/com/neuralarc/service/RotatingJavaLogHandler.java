package com.neuralarc.service;

import java.nio.file.Path;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

public final class RotatingJavaLogHandler extends Handler {
    private final RotatingLogWriter writer;

    public RotatingJavaLogHandler(Path logDirectory) {
        this.writer = new RotatingLogWriter(logDirectory);
        setFormatter(new SimpleFormatter());
        setLevel(Level.ALL);
    }

    @Override
    public synchronized void publish(LogRecord record) {
        if (record == null || !isLoggable(record)) {
            return;
        }
        try {
            String entry = getFormatter().format(record);
            writer.append(RotatingLogWriter.LogType.SYSTEM, entry);
            if (isTradeRecord(record)) {
                writer.append(RotatingLogWriter.LogType.TRADE, entry);
            }
            if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                writer.append(RotatingLogWriter.LogType.ERROR, entry);
            }
            writer.flush();
        } catch (Exception ignored) {
            // Logging must never disrupt trading, polling, or UI work.
        }
    }

    @Override
    public synchronized void flush() {
        try {
            writer.flush();
        } catch (Exception ignored) {
            // Best effort.
        }
    }

    @Override
    public synchronized void close() {
        flush();
    }

    private boolean isTradeRecord(LogRecord record) {
        String loggerName = record.getLoggerName();
        return loggerName != null && loggerName.startsWith("com.neuralarc.trade");
    }
}
