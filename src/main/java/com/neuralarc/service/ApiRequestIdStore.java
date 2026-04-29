package com.neuralarc.service;

import com.neuralarc.util.AppMetadata;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ApiRequestIdStore {
    private static final int MAX_ENTRIES = 50;

    private final Path filePath;

    public ApiRequestIdStore() {
        this(AppMetadata.appDataDirectory().resolve("alpaca-request-ids.log"));
    }

    ApiRequestIdStore(Path filePath) {
        this.filePath = filePath;
    }

    public synchronized void record(String source, String method, String endpoint, String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return;
        }
        List<String> lines = new ArrayList<>(readAll());
        lines.add(Instant.now() + " | " + normalize(source) + " | " + normalize(method) + " | "
                + normalize(endpoint) + " | " + requestId.trim());
        int fromIndex = Math.max(0, lines.size() - MAX_ENTRIES);
        writeAll(lines.subList(fromIndex, lines.size()));
    }

    public synchronized String buildRecentReport() {
        List<String> lines = readAll();
        StringBuilder builder = new StringBuilder();
        builder.append("Recent Alpaca Request IDs").append(System.lineSeparator());
        builder.append("File: ").append(filePath).append(System.lineSeparator()).append(System.lineSeparator());
        if (lines.isEmpty()) {
            builder.append("No Request IDs recorded yet.").append(System.lineSeparator());
            return builder.toString();
        }
        for (String line : lines) {
            builder.append(line).append(System.lineSeparator());
        }
        return builder.toString();
    }

    private List<String> readAll() {
        if (!Files.exists(filePath)) {
            return List.of();
        }
        try {
            return Files.readAllLines(filePath, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return List.of();
        }
    }

    private void writeAll(List<String> lines) {
        try {
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, lines, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Keep runtime resilient; request ID capture is helpful but non-fatal.
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }
}
